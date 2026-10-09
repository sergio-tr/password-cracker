package com.wifiauditlab.assessment.domain.audit

/**
 * Single authorized network validation probe (platform).
 *
 * Must never be used as the high-throughput [com.wifiauditlab.lab.domain.engine.CandidateVerifier].
 * F2 Android path: at most ONE [validateOnce] per session with an explicit [ValidationCredential].
 */
interface NetworkValidationAdapter {
    suspend fun capability(): ApAuthCapability

    /**
     * Validates within an already-authorized session context.
     * Implementations must not choose the target — [AuthorizedValidationContext.networkSnapshot]
     * is authoritative. [credential] is consumed/cleared by the adapter.
     */
    suspend fun validateOnce(
        context: AuthorizedValidationContext,
        credential: ValidationCredential,
    ): NetworkValidationResult
}

/** ADR-004 name retained as alias of [NetworkValidationAdapter]. */
typealias PlatformApAuthProbe = NetworkValidationAdapter

/**
 * Legacy request shape retained for documentation / redaction tests.
 * Passphrase is redacted in [toString].
 */
data class ApAuthProbeRequest(
    val ssid: String,
    val bssidHint: String?,
    val family: com.wifiauditlab.assessment.domain.wifi.SecurityFamily,
    val passphrase: String,
) {
    override fun toString(): String =
        "ApAuthProbeRequest(ssid=$ssid, bssidHint=$bssidHint, family=$family, passphrase=••••••••)"
}

sealed interface ApAuthProbeResult {
    data object Accepted : ApAuthProbeResult

    data object Rejected : ApAuthProbeResult

    data class Unavailable(
        val reason: ApAuthUnavailableReason,
    ) : ApAuthProbeResult

    data class Aborted(
        val reason: ApAuthProbeAbortReason,
    ) : ApAuthProbeResult
}

enum class ApAuthProbeAbortReason {
    ConnectionChanged,
    Cancelled,
    GateDenied,
    Error,
}

/** Deterministic behaviors for [SimulatedValidationAdapter] (no long real sleeps). */
sealed interface SimulatedValidationBehavior {
    data class Immediate(
        val result: NetworkValidationResult,
    ) : SimulatedValidationBehavior

    data class DelayThen(
        val delayMs: Long,
        val result: NetworkValidationResult,
    ) : SimulatedValidationBehavior

    data object HangUntilCancelled : SimulatedValidationBehavior
}

/**
 * JVM / unit-test adapter. Never talks to a real AP.
 */
class SimulatedValidationAdapter(
    private val capabilityValue: ApAuthCapability =
        ApAuthCapability.Unavailable(ApAuthUnavailableReason.NotImplemented),
    private val behavior: SimulatedValidationBehavior =
        SimulatedValidationBehavior.Immediate(
            NetworkValidationResult.Unavailable(ApAuthUnavailableReason.NotImplemented),
        ),
) : NetworkValidationAdapter {
    @Volatile
    var invokeCount: Int = 0
        private set

    @Volatile
    var lastCredentialPresent: Boolean? = null
        private set

    override suspend fun capability(): ApAuthCapability = capabilityValue

    override suspend fun validateOnce(
        context: AuthorizedValidationContext,
        credential: ValidationCredential,
    ): NetworkValidationResult {
        invokeCount += 1
        lastCredentialPresent = credential.isPresent()
        // Consume credential like production (no secret retention).
        credential.clear()
        return when (val b = behavior) {
            is SimulatedValidationBehavior.Immediate -> b.result
            is SimulatedValidationBehavior.DelayThen -> {
                kotlinx.coroutines.delay(b.delayMs)
                b.result
            }
            SimulatedValidationBehavior.HangUntilCancelled -> {
                kotlinx.coroutines.awaitCancellation()
            }
        }
    }
}

/**
 * Compatibility stub retained for unit tests that assert fail-closed without Android APIs.
 * Production DI wires [com.wifiauditlab.android.wifi.AndroidNetworkValidationAdapter].
 */
class AndroidValidationAdapter(
    private val reason: ApAuthUnavailableReason = ApAuthUnavailableReason.PlatformApiLimitation,
) : NetworkValidationAdapter {
    override suspend fun capability(): ApAuthCapability = ApAuthCapability.Unavailable(reason)

    override suspend fun validateOnce(
        context: AuthorizedValidationContext,
        credential: ValidationCredential,
    ): NetworkValidationResult {
        credential.clear()
        return NetworkValidationResult.Unavailable(reason)
    }
}

/** ADR-004 name retained as alias of [AndroidValidationAdapter]. */
typealias UnavailablePlatformApAuthProbe = AndroidValidationAdapter
