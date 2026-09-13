package com.wifiauditlab.android.wifi

import android.content.Context
import android.net.wifi.WifiManager
import com.wifiauditlab.assessment.domain.classifier.WifiSecurityClassifier
import com.wifiauditlab.assessment.domain.connection.CurrentWifiConnection
import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.Ssid
import com.wifiauditlab.assessment.port.CurrentWifiConnectionProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the active Wi-Fi association via [WifiManager].
 * Never attempts authentication or candidate submission against an AP.
 */
class AndroidCurrentWifiConnectionProvider(
    context: Context,
    private val permissions: AndroidWifiPermissionManager,
    private val classifier: WifiSecurityClassifier = WifiSecurityClassifier(),
) : CurrentWifiConnectionProvider {
    private val appContext = context.applicationContext
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    override suspend fun currentConnection(): CurrentWifiConnection? =
        withContext(Dispatchers.IO) {
            val manager = wifiManager ?: return@withContext null
            if (!permissions.hasScanPermission()) return@withContext null

            @Suppress("DEPRECATION")
            val info = manager.connectionInfo ?: return@withContext null

            @Suppress("DEPRECATION")
            val networkId = info.networkId
            if (networkId == -1) return@withContext null

            @Suppress("DEPRECATION")
            val rawSsid = info.ssid
            val ssid = normalizeSsid(rawSsid)

            @Suppress("DEPRECATION")
            val rawBssid = info.bssid
            val bssid =
                rawBssid
                    ?.takeIf { it.isNotBlank() && it != "02:00:00:00:00:00" && it != "00:00:00:00:00:00" }
                    ?.let { Bssid.of(it) }

            if (ssid == null && bssid == null) return@withContext null

            @Suppress("DEPRECATION")
            val rssi = info.rssi.takeIf { it != -127 && it != Int.MIN_VALUE }

            // Security family from the matching scan result when available.
            val family =
                runCatching {
                    @Suppress("DEPRECATION")
                    manager.scanResults
                        ?.firstOrNull { result ->
                            bssid != null &&
                                result.BSSID != null &&
                                Bssid.of(result.BSSID) == bssid
                        }?.capabilities
                        ?.let { classifier.classify(it).family }
                }.getOrNull()

            CurrentWifiConnection(
                ssid = ssid,
                bssid = bssid,
                securityFamily = family,
                rssi = rssi,
            )
        }

    private fun normalizeSsid(raw: String?): Ssid? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim().removeSurrounding("\"")
        if (trimmed.isEmpty() || trimmed.equals("<unknown ssid>", ignoreCase = true)) return null
        // Hidden SSIDs may appear empty after stripping.
        return Ssid(trimmed)
    }
}

/** Whether connection inspection can return usable SSID/BSSID on this device. */
fun AndroidWifiPermissionManager.hasConnectionInspectionPermission(): Boolean = hasScanPermission()
