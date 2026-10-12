package com.wifiauditlab.assessment.domain.real

import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.Ssid

/**
 * Platform-neutral reference to a Wi‑Fi network selected for a real audit.
 * Must not carry Android framework types. BSSID is optional metadata.
 */
data class RealNetworkReference(
    val ssid: Ssid,
    val bssid: Bssid?,
    val securityFamily: SecurityFamily,
    val displayName: String,
)

/**
 * Explicit user consent to start a real Wi‑Fi audit session.
 */
data class RealNetworkConsent(
    val granted: Boolean,
) {
    init {
        require(granted) { "consent must be granted" }
    }
}
