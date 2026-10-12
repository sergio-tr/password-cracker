package com.wifiauditlab.assessment.domain.real

import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.Ssid

/**
 * Platform-neutral reference to a Wi‑Fi network selected for a real credential probe.
 * Must not carry Android framework types.
 */
data class RealNetworkReference(
    val ssid: Ssid,
    val bssid: Bssid?,
    val securityFamily: SecurityFamily,
    val displayName: String,
)

/**
 * Explicit user consent for a one-shot real credential probe.
 * Creating this value implies the UI collected an affirmative checkbox.
 */
data class RealNetworkConsent(
    val granted: Boolean,
) {
    init {
        require(granted) { "consent must be granted" }
    }
}

/**
 * Password material for a real probe. Never log or render via [toString].
 */
class SensitiveCredential private constructor(
    private val value: String,
) {
    fun reveal(): String = value

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

data class RealNetworkPasswordExecutionRequest(
    val network: RealNetworkReference,
    val credential: SensitiveCredential,
    val consent: RealNetworkConsent,
) {
    override fun toString(): String =
        "RealNetworkPasswordExecutionRequest(network=$network, credential=███, consent=$consent)"
}

sealed interface RealNetworkUnavailableReason {
    /** Adapter deliberately not wired yet — product surface is ready. */
    data object NotImplemented : RealNetworkUnavailableReason

    data object MissingPermissions : RealNetworkUnavailableReason

    data object CapabilityUnavailable : RealNetworkUnavailableReason

    data object NetworkNotCurrent : RealNetworkUnavailableReason

    data object ConsentRequired : RealNetworkUnavailableReason

    /** Missing BSSID or other identity data required for Exact targeting. */
    data object InsufficientInformation : RealNetworkUnavailableReason
}

sealed interface RealNetworkFailureReason {
    data object PlatformError : RealNetworkFailureReason

    data object Timeout : RealNetworkFailureReason

    data object Unknown : RealNetworkFailureReason
}

/**
 * Typed outcomes of a real network credential probe.
 *
 * Do **not** map a generic Android framework failure to [Rejected]
 * (that would falsely assert “password incorrect”).
 */
sealed interface RealNetworkPasswordExecutionResult {
    data object Accepted : RealNetworkPasswordExecutionResult

    data object Rejected : RealNetworkPasswordExecutionResult

    data object Inconclusive : RealNetworkPasswordExecutionResult

    data object Cancelled : RealNetworkPasswordExecutionResult

    data class Unavailable(
        val reason: RealNetworkUnavailableReason,
    ) : RealNetworkPasswordExecutionResult

    data class Failed(
        val reason: RealNetworkFailureReason,
    ) : RealNetworkPasswordExecutionResult
}

/**
 * Application port for one-shot real credential execution against the
 * **currently associated** network.
 *
 * Separated from Lab Search Engine V2 (`CandidateVerifier` / local synthetic).
 */
interface RealNetworkPasswordExecutionPort {
    /**
     * Cooperative cancel for an in-flight [execute]. Implementations must
     * release platform callbacks and never log the credential.
     */
    fun cancel()

    suspend fun execute(request: RealNetworkPasswordExecutionRequest): RealNetworkPasswordExecutionResult
}
