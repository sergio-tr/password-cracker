package com.wifiauditlab.assessment.domain.audit

/**
 * Evolved NetworkValidationAdapter contract for F1.
 *
 * Production [AndroidValidationAdapter] remains fail-closed (Unavailable).
 * Active AP probing is Planned F2 — not declared Available without a demonstrated path.
 */
interface NetworkValidationAdapter {
    suspend fun capability(): ApAuthCapability

    /**
     * Validates within an already-authorized session context.
     * Implementations must not choose the target — [AuthorizedValidationContext.networkSnapshot]
     * is authoritative.
     */
    suspend fun validateOnce(context: AuthorizedValidationContext): NetworkValidationResult
}

/** ADR-004 name retained as alias of [NetworkValidationAdapter]. */
typealias PlatformApAuthProbe = NetworkValidationAdapter

/**
 * Legacy request shape retained for documentation / future F2 passphrase binding.
 * Passphrase is redacted in [toString]; F1 adapter path does not use this type.
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

    /**
     * Suspends until cancelled or [delayMs] elapses (virtual time under test dispatchers).
     */
    data class DelayThen(
        val delayMs: Long,
        val result: NetworkValidationResult,
    ) : SimulatedValidationBehavior

    /** Suspends forever until the calling coroutine is cancelled. */
    data object HangUntilCancelled : SimulatedValidationBehavior
}

/**
 * JVM / unit-test adapter. Never talks to a real AP.
 * Default capability is Unavailable so production-like tests stay fail-closed unless
 * a test explicitly constructs [SimulatedValidationAdapter] with Available.
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

    override suspend fun capability(): ApAuthCapability = capabilityValue

    override suspend fun validateOnce(context: AuthorizedValidationContext): NetworkValidationResult {
        invokeCount += 1
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
 * Stock Android / Play build: AP validation path not available (F0/F1).
 * Documents [ApAuthUnavailableReason.PlatformApiLimitation] — no silent/batch AP auth API.
 * Real probe = Planned F2 when a public capability is demonstrated.
 */
class AndroidValidationAdapter(
    private val reason: ApAuthUnavailableReason = ApAuthUnavailableReason.PlatformApiLimitation,
) : NetworkValidationAdapter {
    override suspend fun capability(): ApAuthCapability = ApAuthCapability.Unavailable(reason)

    override suspend fun validateOnce(context: AuthorizedValidationContext): NetworkValidationResult =
        NetworkValidationResult.Unavailable(reason)
}

/** ADR-004 name retained as alias of [AndroidValidationAdapter]. */
typealias UnavailablePlatformApAuthProbe = AndroidValidationAdapter
