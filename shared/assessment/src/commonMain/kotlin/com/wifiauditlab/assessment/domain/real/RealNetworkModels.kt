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

/**
 * Password material held only in memory for local search / one-shot confirmation.
 * Never log or render via [toString].
 */
class SensitiveCredential private constructor(
    private val value: String,
) {
    fun reveal(): String = value

    fun toValidationChars(): CharArray = value.toCharArray()

    override fun toString(): String = "SensitiveCredential(███)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SensitiveCredential) return false
        return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()

    companion object {
        fun of(plaintext: String): SensitiveCredential {
            require(plaintext.isNotEmpty()) { "credential must not be empty" }
            return SensitiveCredential(plaintext)
        }
    }
}
