package com.wifiauditlab.assessment.domain.audit

import com.wifiauditlab.assessment.domain.connection.CurrentWifiConnection
import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily

/**
 * Exactly one personal lab AP allowed for [VerificationMode.SINGLE_REAL_CREDENTIAL_VALIDATION].
 * Stronger than [AuthorizedLabNetworkStore]: SSID **and** BSSID must match; no wildcards.
 *
 * Password is never part of this model — always entered at runtime.
 */
data class FixedLabAccessPoint(
    val ssid: String,
    val bssid: String,
    val securityFamily: SecurityFamily,
) {
    init {
        require(ssid.isNotBlank()) { "ssid must not be blank" }
        require(bssid.isNotBlank()) { "bssid must not be blank" }
        val normalized = Bssid.of(bssid)
        require(normalized.isUsableForFixedLab()) {
            "bssid must be a usable MAC (not redacted/zero)"
        }
    }

    val ssidNormalized: String get() = ssid.trim()
    val bssidNormalized: Bssid get() = Bssid.of(bssid)

    fun matchesIdentity(
        ssid: String,
        bssid: String,
    ): Boolean =
        ssidNormalized == ssid.trim() &&
            bssidNormalized.value == Bssid.of(bssid).value

    fun matchesConnection(connection: CurrentWifiConnection?): FixedLabConnectionMatch {
        if (connection == null) return FixedLabConnectionMatch.NotConnected
        val connectionSsid = connection.ssid?.normalized?.takeIf { it.isNotEmpty() }
        val connectionBssid = connection.bssid?.takeIf { it.isUsableForFixedLab() }
        if (connectionSsid == null || connectionBssid == null) {
            return FixedLabConnectionMatch.InsufficientInformation
        }
        return if (matchesIdentity(connectionSsid, connectionBssid.value)) {
            FixedLabConnectionMatch.Matched
        } else {
            FixedLabConnectionMatch.TargetMismatch
        }
    }

    fun toSnapshot(): LabNetworkSnapshot =
        LabNetworkSnapshot(
            ssid = ssidNormalized,
            securityFamily = securityFamily,
            bssid = bssidNormalized.value,
        )
}

enum class FixedLabConnectionMatch {
    Matched,
    NotConnected,
    TargetMismatch,
    InsufficientInformation,
}

/** Provides the single configured fixed lab AP, or null when unavailable / not configured. */
fun interface FixedLabAccessPointProvider {
    fun configuredOrNull(): FixedLabAccessPoint?
}

internal fun Bssid.isUsableForFixedLab(): Boolean {
    if (value.isEmpty()) return false
    return value != "02:00:00:00:00:00" && value != "00:00:00:00:00:00"
}
