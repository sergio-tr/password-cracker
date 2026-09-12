package com.wifiauditlab.assessment.domain.wifi

import kotlin.jvm.JvmInline

/** Frequency band a network operates on. */
enum class WifiBand { GHZ_2_4, GHZ_5, GHZ_6, UNKNOWN }

/** Wi-Fi generation, normalized to marketing names where sensible. */
enum class WifiStandard { LEGACY, WIFI_4, WIFI_5, WIFI_6, WIFI_6E, WIFI_7, UNKNOWN }

/** Management-frame protection state (802.11w). */
enum class ManagementFrameProtection { REQUIRED, CAPABLE, DISABLED, UNKNOWN }

/**
 * Normalized family of the security/authentication mechanism. Assessment
 * strategies dispatch on this instead of on raw capability strings, keeping
 * large `when` blocks out of the UI layer.
 */
enum class SecurityFamily {
    OPEN,
    WEP,
    WPA_PERSONAL,
    WPA2_PERSONAL,
    WPA3_PERSONAL,
    WPA2_WPA3_PERSONAL,
    WPA2_ENTERPRISE,
    WPA3_ENTERPRISE,
    OWE,
    PASSPOINT,
    DPP,
    UNKNOWN,
}

/** A normalized SSID. Empty means a hidden network. */
@JvmInline
value class Ssid(val value: String) {
    val isHidden: Boolean get() = value.isEmpty()

    /** Normalized form used for identity/matching: trimmed, without surrounding whitespace. */
    val normalized: String get() = value.trim()

    override fun toString(): String = if (isHidden) "<hidden>" else value

    companion object {
        val HIDDEN: Ssid = Ssid("")
    }
}

/** A normalized BSSID (access-point MAC), lower-case colon-separated. */
@JvmInline
value class Bssid private constructor(val value: String) {
    override fun toString(): String = value

    companion object {
        fun of(raw: String): Bssid = Bssid(raw.trim().lowercase())
    }
}

/** A radio channel with its band and center frequency. */
data class WifiChannel(val number: Int, val band: WifiBand, val frequencyMhz: Int)

/** Human-friendly signal quality buckets, derived from RSSI. */
enum class SignalQuality { EXCELLENT, GOOD, FAIR, WEAK }

/** Received signal strength. */
@JvmInline
value class WifiSignal(val rssiDbm: Int) {
    val quality: SignalQuality
        get() =
            when {
                rssiDbm >= -55 -> SignalQuality.EXCELLENT
                rssiDbm >= -67 -> SignalQuality.GOOD
                rssiDbm >= -78 -> SignalQuality.FAIR
                else -> SignalQuality.WEAK
            }
}

/**
 * Normalized description of a network's security configuration. Built by the
 * platform-independent [com.wifiauditlab.assessment.domain.classifier.WifiSecurityClassifier].
 */
data class WifiSecurityProfile(
    val family: SecurityFamily,
    val keyManagements: Set<String>,
    val managementFrameProtection: ManagementFrameProtection,
    val isTransitionMode: Boolean,
    val rawCapabilities: String?,
) {
    companion object {
        fun open(rawCapabilities: String? = null): WifiSecurityProfile =
            WifiSecurityProfile(
                family = SecurityFamily.OPEN,
                keyManagements = emptySet(),
                managementFrameProtection = ManagementFrameProtection.UNKNOWN,
                isTransitionMode = false,
                rawCapabilities = rawCapabilities,
            )
    }
}

/**
 * Stable-ish identity of a network for matching purposes. A network is *not*
 * identified by a single BSSID; identity is the normalized SSID plus the
 * security family. Known BSSIDs are an additional matching signal, not identity.
 */
data class NetworkIdentity(
    val normalizedSsid: String,
    val securityFamily: SecurityFamily,
)

/**
 * A transient sighting of a network during a scan. This is never persisted as-is;
 * persistence uses [com.wifiauditlab.assessment.domain.vault.SavedWifiNetwork].
 */
data class WifiObservation(
    val ssid: Ssid,
    val bssid: Bssid,
    val signal: WifiSignal,
    val channel: WifiChannel,
    val standard: WifiStandard,
    val securityProfile: WifiSecurityProfile,
    val observedAtEpochMillis: Long,
) {
    val identity: NetworkIdentity
        get() = NetworkIdentity(ssid.normalized, securityProfile.family)
}
