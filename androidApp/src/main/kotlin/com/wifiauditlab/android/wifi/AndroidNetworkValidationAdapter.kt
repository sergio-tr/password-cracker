package com.wifiauditlab.android.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.MacAddress
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import androidx.annotation.RequiresApi
import com.wifiauditlab.assessment.domain.audit.AndroidNetworkValidationCapability
import com.wifiauditlab.assessment.domain.audit.ApAuthCapability
import com.wifiauditlab.assessment.domain.audit.ApAuthUnavailableReason
import com.wifiauditlab.assessment.domain.audit.AuthorizedValidationContext
import com.wifiauditlab.assessment.domain.audit.LabNetworkSnapshot
import com.wifiauditlab.assessment.domain.audit.LocalOnlyFailureReasonMapper
import com.wifiauditlab.assessment.domain.audit.NetworkValidationAdapter
import com.wifiauditlab.assessment.domain.audit.NetworkValidationResult
import com.wifiauditlab.assessment.domain.audit.ValidationCredential
import com.wifiauditlab.assessment.domain.audit.supportsLabNetworkValidation
import com.wifiauditlab.assessment.domain.audit.toApAuthReason
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/**
 * F2 Android adapter: **one** local-only [ConnectivityManager.requestNetwork] against the
 * authorized snapshot, using an explicit [ValidationCredential].
 *
 * Capability is Available only when [AndroidNetworkValidationCapabilityEvaluator] reports
 * AuthenticationResultCapable (API 34+ + STA concurrency + permissions + Wi‑Fi on).
 *
 * Never iterates candidates. Never reads system PSK. Never binds the process network.
 */
class AndroidNetworkValidationAdapter(
    context: Context,
    private val permissions: AndroidWifiPermissionManager,
    private val capabilityEvaluator: AndroidNetworkValidationCapabilityEvaluator =
        AndroidNetworkValidationCapabilityEvaluator(context, permissions),
) : NetworkValidationAdapter {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val mainExecutor: Executor = ContextCompatMainExecutor(appContext)

    override suspend fun capability(): ApAuthCapability =
        when (val eval = capabilityEvaluator.evaluate()) {
            is AndroidNetworkValidationCapability.Available -> ApAuthCapability.Available
            is AndroidNetworkValidationCapability.Unavailable ->
                ApAuthCapability.Unavailable(eval.reason.toApAuthReason())
        }

    override suspend fun validateOnce(
        context: AuthorizedValidationContext,
        credential: ValidationCredential,
    ): NetworkValidationResult {
        val snapshot = context.networkSnapshot
        val eval = capabilityEvaluator.evaluate(snapshot.securityFamily)
        if (eval is AndroidNetworkValidationCapability.Unavailable) {
            credential.clear()
            return NetworkValidationResult.Unavailable(eval.reason.toApAuthReason())
        }
        if (!snapshot.securityFamily.supportsLabNetworkValidation()) {
            credential.clear()
            return NetworkValidationResult.Unsupported
        }
        val cm = connectivityManager
        val wm = wifiManager
        if (cm == null || wm == null) {
            credential.clear()
            return NetworkValidationResult.Unavailable(ApAuthUnavailableReason.DeviceUnsupported)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            credential.clear()
            return NetworkValidationResult.Unavailable(ApAuthUnavailableReason.RequiresApi34)
        }

        val passphrase =
            credential.use { it }
                ?: return NetworkValidationResult.PlatformError("MissingCredential")
        return performSingleLocalOnlyRequest(
            connectivityManager = cm,
            wifiManager = wm,
            snapshot = snapshot,
            passphrase = passphrase,
        )
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun performSingleLocalOnlyRequest(
        connectivityManager: ConnectivityManager,
        wifiManager: WifiManager,
        snapshot: LabNetworkSnapshot,
        passphrase: String,
    ): NetworkValidationResult {
        val specifier =
            try {
                buildSpecifier(snapshot, passphrase)
            } catch (t: Throwable) {
                return NetworkValidationResult.PlatformError(t::class.simpleName ?: "SpecifierError")
            }

        val request =
            NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()

        return suspendCancellableCoroutine { cont ->
            val completed = AtomicBoolean(false)
            val callbackRef = AtomicReference<ConnectivityManager.NetworkCallback?>(null)
            val failureListenerRef = AtomicReference<WifiManager.LocalOnlyConnectionFailureListener?>(null)
            val mainHandler = android.os.Handler(appContext.mainLooper)

            fun finish(result: NetworkValidationResult) {
                if (!completed.compareAndSet(false, true)) return
                mainHandler.removeCallbacksAndMessages(null)
                cleanup(
                    connectivityManager = connectivityManager,
                    wifiManager = wifiManager,
                    callback = callbackRef.getAndSet(null),
                    failureListener = failureListenerRef.getAndSet(null),
                )
                if (cont.isActive) cont.resume(result)
            }

            val failureListener =
                WifiManager.LocalOnlyConnectionFailureListener { failedSpecifier, reason ->
                    if (failedSpecifier != specifier) return@LocalOnlyConnectionFailureListener
                    finish(LocalOnlyFailureReasonMapper.map(reason))
                }
            failureListenerRef.set(failureListener)

            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        finish(NetworkValidationResult.Validated)
                    }

                    override fun onUnavailable() {
                        // Give LocalOnlyConnectionFailureListener a short chance to report a
                        // specific reason (e.g. AUTHENTICATION) before falling back.
                        mainHandler.postDelayed(
                            {
                                finish(NetworkValidationResult.RequestUnavailable)
                            },
                            FAILURE_REASON_GRACE_MS,
                        )
                    }
                }
            callbackRef.set(callback)

            cont.invokeOnCancellation {
                finish(NetworkValidationResult.Cancelled)
            }

            try {
                wifiManager.addLocalOnlyConnectionFailureListener(mainExecutor, failureListener)
                connectivityManager.requestNetwork(request, callback, OPERATION_TIMEOUT_HINT_MS.toInt())
            } catch (t: Throwable) {
                finish(NetworkValidationResult.PlatformError(t::class.simpleName ?: "RequestError"))
            }
        }
    }

    private fun buildSpecifier(
        snapshot: LabNetworkSnapshot,
        passphrase: String,
    ): WifiNetworkSpecifier {
        val builder = WifiNetworkSpecifier.Builder().setSsid(snapshot.ssid)
        val bssid = snapshot.bssid
        if (!bssid.isNullOrBlank()) {
            runCatching { builder.setBssid(MacAddress.fromString(bssid)) }
        }
        when (snapshot.securityFamily) {
            SecurityFamily.WPA2_PERSONAL,
            SecurityFamily.WPA2_WPA3_PERSONAL,
            -> builder.setWpa2Passphrase(passphrase)
            SecurityFamily.WPA3_PERSONAL -> builder.setWpa3Passphrase(passphrase)
            else -> error("unsupported family")
        }
        return builder.build()
    }

    private fun cleanup(
        connectivityManager: ConnectivityManager,
        wifiManager: WifiManager,
        callback: ConnectivityManager.NetworkCallback?,
        failureListener: WifiManager.LocalOnlyConnectionFailureListener?,
    ) {
        if (callback != null) {
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        }
        if (failureListener != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching { wifiManager.removeLocalOnlyConnectionFailureListener(failureListener) }
        }
    }

    companion object {
        /** Hint for ConnectivityManager; orchestrator applies the hard operation timeout. */
        private const val OPERATION_TIMEOUT_HINT_MS: Long = 25_000

        private const val FAILURE_REASON_GRACE_MS: Long = 400
    }
}

/** Main-thread executor without adding androidx.core dependency surface beyond ContextCompat. */
private class ContextCompatMainExecutor(
    context: Context,
) : Executor {
    private val handler = android.os.Handler(context.mainLooper)

    override fun execute(command: Runnable) {
        if (android.os.Looper.myLooper() == handler.looper) {
            command.run()
        } else {
            handler.post(command)
        }
    }
}

/** Production alias: DI may register under [NetworkValidationAdapter]. */
typealias AndroidValidationAdapterProduction = AndroidNetworkValidationAdapter
