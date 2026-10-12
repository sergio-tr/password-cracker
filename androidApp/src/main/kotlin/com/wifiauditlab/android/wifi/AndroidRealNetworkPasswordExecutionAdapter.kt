package com.wifiauditlab.android.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.MacAddress
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import com.wifiauditlab.assessment.domain.real.RealNetworkFailureReason
import com.wifiauditlab.assessment.domain.real.RealNetworkPasswordExecutionPort
import com.wifiauditlab.assessment.domain.real.RealNetworkPasswordExecutionRequest
import com.wifiauditlab.assessment.domain.real.RealNetworkPasswordExecutionResult
import com.wifiauditlab.assessment.domain.real.RealNetworkUnavailableReason
import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference

/**
 * Android-side adapter for real credential probes against the associated network.
 *
 * This is the **only** place where WifiNetworkSpecifier / NetworkRequest /
 * NetworkCallback for this product surface should live — never inside
 * Search Engine V2 or `:shared:lab`.
 *
 * Buscar globalmente el marcador único en [execute].
 */
class AndroidRealNetworkPasswordExecutionAdapter(
    context: Context,
) : RealNetworkPasswordExecutionPort {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val activeCallback =
        AtomicReference<ConnectivityManager.NetworkCallback?>(null)

    private val activeResult =
        AtomicReference<CompletableDeferred<RealNetworkPasswordExecutionResult>?>(null)

    override fun cancel() {
        activeResult
            .getAndSet(null)
            ?.complete(RealNetworkPasswordExecutionResult.Cancelled)

        activeCallback
            .getAndSet(null)
            ?.let { callback ->
                runCatching {
                    connectivityManager.unregisterNetworkCallback(callback)
                }
            }
    }

    override suspend fun execute(
        request: RealNetworkPasswordExecutionRequest,
    ): RealNetworkPasswordExecutionResult {
        // USER_IMPLEMENTATION_POINT_REAL_WIFI
        // One-shot real probe. No Search Engine. Never log the credential.

        // 1. Consentimiento
        if (!request.consent.granted) {
            return RealNetworkPasswordExecutionResult.Unavailable(
                RealNetworkUnavailableReason.ConsentRequired,
            )
        }

        // 2. SSID required; BSSID optional additional selector
        val ssid = request.network.ssid.value
        val optionalBssid = RealNetworkSpecifierPolicy.optionalBssidValue(request.network.bssid)

        // 3. Credential (never log)
        val password = request.credential.reveal()

        // 4. WifiNetworkSpecifier.Builder — SSID always; BSSID only when present
        val builder = WifiNetworkSpecifier.Builder().setSsid(ssid)
        optionalBssid?.let { builder.setBssid(MacAddress.fromString(it)) }

        // 5. Aplicar WPA2/WPA3 según SecurityFamily
        when (request.network.securityFamily) {
            SecurityFamily.WPA2_PERSONAL -> {
                builder.setWpa2Passphrase(password)
            }
            SecurityFamily.WPA3_PERSONAL -> {
                builder.setWpa3Passphrase(password)
            }
            SecurityFamily.WPA2_WPA3_PERSONAL -> {
                // Variante inicial compatible (mismo criterio Lab F2).
                builder.setWpa2Passphrase(password)
            }
            SecurityFamily.OPEN,
            SecurityFamily.WEP,
            SecurityFamily.WPA_PERSONAL,
            SecurityFamily.WPA2_ENTERPRISE,
            SecurityFamily.WPA3_ENTERPRISE,
            SecurityFamily.OWE,
            SecurityFamily.PASSPOINT,
            SecurityFamily.DPP,
            SecurityFamily.UNKNOWN,
            -> {
                return RealNetworkPasswordExecutionResult.Unavailable(
                    RealNetworkUnavailableReason.CapabilityUnavailable,
                )
            }
        }

        val specifier = builder.build()

        // 6. NetworkRequest (local-only Wi‑Fi)
        val networkRequest =
            NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()

        val result = CompletableDeferred<RealNetworkPasswordExecutionResult>()
        activeResult.set(result)

        // 7. NetworkCallback
        // onUnavailable ≠ Rejected (puede ser rechazo de usuario / solicitud no satisfecha).
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    result.complete(RealNetworkPasswordExecutionResult.Accepted)
                }

                override fun onUnavailable() {
                    result.complete(RealNetworkPasswordExecutionResult.Inconclusive)
                }
            }
        activeCallback.set(callback)

        return try {
            // 8. requestNetwork()
            connectivityManager.requestNetwork(networkRequest, callback)

            // 9. await con timeout
            withTimeoutOrNull(30_000L) {
                result.await()
            } ?: RealNetworkPasswordExecutionResult.Inconclusive
        } catch (_: SecurityException) {
            RealNetworkPasswordExecutionResult.Unavailable(
                RealNetworkUnavailableReason.MissingPermissions,
            )
        } catch (_: IllegalArgumentException) {
            RealNetworkPasswordExecutionResult.Failed(
                RealNetworkFailureReason.PlatformError,
            )
        } catch (_: Exception) {
            RealNetworkPasswordExecutionResult.Failed(
                RealNetworkFailureReason.PlatformError,
            )
        } finally {
            // 10. cleanup siempre
            activeResult.compareAndSet(result, null)
            if (activeCallback.compareAndSet(callback, null)) {
                runCatching {
                    connectivityManager.unregisterNetworkCallback(callback)
                }
            }
        }
    }
}

/**
 * Pure policy for optional BSSID attachment — unit-testable without ConnectivityManager.
 */
internal object RealNetworkSpecifierPolicy {
    fun optionalBssidValue(bssid: Bssid?): String? =
        bssid?.value?.takeIf { it.isNotEmpty() }
}
