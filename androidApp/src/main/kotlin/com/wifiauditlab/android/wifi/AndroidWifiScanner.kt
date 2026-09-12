package com.wifiauditlab.android.wifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.wifi.WifiManager
import com.wifiauditlab.assessment.port.WifiScanState
import com.wifiauditlab.assessment.port.WifiScanRequestResult
import com.wifiauditlab.assessment.port.WifiScanner
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Android [WifiScanner]. Emits scan states from the system broadcast and exposes
 * a cooperative [refresh]. A refresh does not guarantee a fresh physical scan:
 * the OS throttles `startScan`, which is surfaced as [WifiScanRequestResult.THROTTLED].
 */
class AndroidWifiScanner(
    context: Context,
    private val permissions: AndroidWifiPermissionManager,
    private val mapper: AndroidWifiMapper,
    private val now: () -> Long = { System.currentTimeMillis() },
) : WifiScanner {

    private val appContext = context.applicationContext
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    override fun observeState(): Flow<WifiScanState> = callbackFlow {
        val manager = wifiManager
        if (manager == null) {
            trySend(WifiScanState.Unavailable)
            awaitClose { }
            return@callbackFlow
        }

        fun publish() = trySend(currentState(manager))

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = Unit.also { publish() }
        }
        appContext.registerReceiver(receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        publish()

        awaitClose { runCatching { appContext.unregisterReceiver(receiver) } }
    }

    override suspend fun refresh(): WifiScanRequestResult {
        val manager = wifiManager ?: return WifiScanRequestResult.UNAVAILABLE
        if (!permissions.hasScanPermission()) return WifiScanRequestResult.PERMISSION_REQUIRED
        if (!isLocationEnabled()) return WifiScanRequestResult.LOCATION_SERVICES_DISABLED
        @Suppress("DEPRECATION")
        val started = manager.startScan()
        return if (started) WifiScanRequestResult.STARTED else WifiScanRequestResult.THROTTLED
    }

    private fun currentState(manager: WifiManager): WifiScanState {
        if (!permissions.hasScanPermission()) return WifiScanState.PermissionRequired
        if (!isLocationEnabled()) return WifiScanState.LocationServicesDisabled
        return try {
            @Suppress("DEPRECATION")
            val results = manager.scanResults.orEmpty()
            WifiScanState.Results(results.map { mapper.toObservation(it, now()) })
        } catch (security: SecurityException) {
            WifiScanState.PermissionRequired
        } catch (error: Exception) {
            WifiScanState.Error(error.message ?: "scan failed")
        }
    }

    private fun isLocationEnabled(): Boolean {
        val manager = locationManager ?: return false
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}
