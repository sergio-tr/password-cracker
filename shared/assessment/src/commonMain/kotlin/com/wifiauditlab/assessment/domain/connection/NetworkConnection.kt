package com.wifiauditlab.assessment.domain.connection

import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.Ssid

/**
 * Snapshot of the device's active Wi-Fi association.
 * Used only as context for known-password audits — never to drive AP authentication attempts.
 */
data class CurrentWifiConnection(
    val ssid: Ssid?,
    val bssid: Bssid?,
    val securityFamily: SecurityFamily?,
    val rssi: Int?,
)

/**
 * How a scanned observation relates to the device's current Wi-Fi association.
 * Prefer BSSID when available; never treat SSID alone as Exact.
 */
sealed interface NetworkConnectionMatch {
    /** Same BSSID as the active association. */
    data object Exact : NetworkConnectionMatch

    /**
     * Same normalized SSID (and compatible signals) but BSSID differs or is unavailable —
     * typical of multi-AP ESS / roaming.
     */
    data object Probable : NetworkConnectionMatch

    /** Associated to a different network than the observation. */
    data object DifferentNetwork : NetworkConnectionMatch

    /** No active Wi-Fi association. */
    data object NotConnected : NetworkConnectionMatch

    /** Association exists but SSID/BSSID cannot be read (permissions / OS redaction). */
    data object InsufficientInformation : NetworkConnectionMatch
}

interface NetworkConnectionMatcher {
    fun match(
        observationSsid: Ssid,
        observationBssid: Bssid,
        connection: CurrentWifiConnection?,
    ): NetworkConnectionMatch
}

class DefaultNetworkConnectionMatcher : NetworkConnectionMatcher {
    override fun match(
        observationSsid: Ssid,
        observationBssid: Bssid,
        connection: CurrentWifiConnection?,
    ): NetworkConnectionMatch {
        if (connection == null) return NetworkConnectionMatch.NotConnected

        val connectionSsid = connection.ssid?.normalized?.takeIf { it.isNotEmpty() }
        val connectionBssid = connection.bssid?.takeIf { it.isUsable() }

        if (connectionSsid == null && connectionBssid == null) {
            return NetworkConnectionMatch.InsufficientInformation
        }

        if (connectionBssid != null && connectionBssid == observationBssid) {
            return NetworkConnectionMatch.Exact
        }

        val observationNormalized = observationSsid.normalized
        if (connectionSsid != null && connectionSsid == observationNormalized) {
            return NetworkConnectionMatch.Probable
        }

        return NetworkConnectionMatch.DifferentNetwork
    }
}

private fun Bssid.isUsable(): Boolean {
    if (value.isEmpty()) return false
    // Android redacts BSSID when location/nearby permission is missing.
    return value != "02:00:00:00:00:00" && value != "00:00:00:00:00:00"
}
