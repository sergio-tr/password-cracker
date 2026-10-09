package com.wifiauditlab.assessment.domain.audit

import com.wifiauditlab.assessment.domain.connection.CurrentWifiConnection
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily

/**
 * Immutable identity snapshot taken at LAB session admission.
 * Target always derives from the current Wi‑Fi connection — never from free-form UI.
 *
 * Primary identity (F0 registry): SSID + [SecurityFamily].
 * Optional BSSID is used for change detection when the OS provides a usable value.
 */
data class LabNetworkSnapshot(
    val ssid: String,
    val securityFamily: SecurityFamily,
    val bssid: String? = null,
) {
    init {
        require(ssid.isNotBlank()) { "ssid must not be blank" }
    }

    fun registryKey(): AuthorizedLabNetworkKey = AuthorizedLabNetworkKey.of(ssid, securityFamily)

    /** Whether [connection] still represents the same lab target. */
    fun matches(connection: CurrentWifiConnection?): Boolean {
        if (connection == null) return false
        val connectionSsid = connection.ssid?.normalized?.takeIf { it.isNotEmpty() } ?: return false
        if (connectionSsid != ssid.trim()) return false
        val connectionFamily = connection.securityFamily
        if (connectionFamily != null && connectionFamily != securityFamily) return false
        val snapBssid = bssid?.takeIf { it.isNotBlank() && it != REDACTED_BSSID && it != ZERO_BSSID }
        val connBssid =
            connection.bssid
                ?.value
                ?.takeIf { it.isNotBlank() && it != REDACTED_BSSID && it != ZERO_BSSID }
        if (snapBssid != null && connBssid != null && snapBssid != connBssid) return false
        return true
    }

    companion object {
        private const val REDACTED_BSSID = "02:00:00:00:00:00"
        private const val ZERO_BSSID = "00:00:00:00:00:00"

        fun fromConnection(
            connection: CurrentWifiConnection,
            fallbackFamily: SecurityFamily,
        ): LabNetworkSnapshot? {
            val ssid = connection.ssid?.normalized?.takeIf { it.isNotEmpty() } ?: return null
            val family = connection.securityFamily ?: fallbackFamily
            val bssid =
                connection.bssid
                    ?.value
                    ?.takeIf { it.isNotBlank() && it != REDACTED_BSSID && it != ZERO_BSSID }
            return LabNetworkSnapshot(ssid = ssid, securityFamily = family, bssid = bssid)
        }
    }
}
