package com.wifiauditlab.assessment.domain.realaudit

import com.wifiauditlab.assessment.domain.real.RealNetworkConsent
import com.wifiauditlab.assessment.domain.real.RealNetworkReference
import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile

/**
 * Application request to audit a **user-selected** real Wi‑Fi network.
 * Contains no password / known-secret material — the secret is unknown to the app.
 */
data class RealWifiAuditRequest(
    val network: RealNetworkReference,
    val securityProfile: WifiSecurityProfile,
    val consent: RealNetworkConsent,
)

/**
 * Strategy descriptor selected from [WifiSecurityProfile] / security family.
 * No offensive logic — descriptive only.
 */
sealed interface RealWifiAuditStrategy {
    data object OpenNetworkAssessment : RealWifiAuditStrategy

    data object LegacyPersonalKeyAudit : RealWifiAuditStrategy

    data object PersonalPskAudit : RealWifiAuditStrategy

    data object PersonalSaeAudit : RealWifiAuditStrategy

    data object TransitionPersonalAudit : RealWifiAuditStrategy

    data object EnterpriseAssessment : RealWifiAuditStrategy

    data object EnhancedOpenAssessment : RealWifiAuditStrategy

    data object PasspointAssessment : RealWifiAuditStrategy

    data object DppAssessment : RealWifiAuditStrategy

    data object Unsupported : RealWifiAuditStrategy
}

/** Strategies that need Search Engine V2 + an external CandidateVerifier backend. */
fun RealWifiAuditStrategy.requiresPasswordSearch(): Boolean =
    when (this) {
        RealWifiAuditStrategy.LegacyPersonalKeyAudit,
        RealWifiAuditStrategy.PersonalPskAudit,
        RealWifiAuditStrategy.PersonalSaeAudit,
        RealWifiAuditStrategy.TransitionPersonalAudit,
        -> true
        else -> false
    }

@Deprecated(
    "Renamed to requiresPasswordSearch — RealWifiAudit does not use a local known secret.",
    ReplaceWith("requiresPasswordSearch()"),
)
fun RealWifiAuditStrategy.requiresLocalPasswordSearch(): Boolean = requiresPasswordSearch()

/**
 * Platform-neutral execution limits for a real Wi‑Fi audit plan.
 * No credential material.
 */
data class RealWifiAuditLimits(
    val maxDurationMillis: Long = 60_000L,
    val maxCandidatesHint: Long? = null,
    val workerCountHint: Int = 1,
)

/**
 * Prepared audit plan: network + security + strategy + limits.
 * Does **not** contain a password or known secret.
 */
data class RealWifiAuditPlan(
    val network: RealNetworkReference,
    val securityProfile: WifiSecurityProfile,
    val strategy: RealWifiAuditStrategy,
    val limits: RealWifiAuditLimits,
    val strategyLabel: String,
)

/**
 * Stable capability of the RealWifiAudit stack (checked at prepare, before START).
 * Not an execution outcome — Unavailable here means START must stay disabled.
 */
sealed interface RealWifiAuditCapability {
    data object Available : RealWifiAuditCapability

    data class Unavailable(
        val reason: RealWifiAuditUnavailableReason,
    ) : RealWifiAuditCapability
}

sealed interface RealWifiAuditPhase {
    data object Idle : RealWifiAuditPhase

    /** Resolving strategy / plan / external-verifier capability. */
    data object CheckingCapability : RealWifiAuditPhase

    data object Preparing : RealWifiAuditPhase

    /** Password search (or assessment-only) can start. */
    data object Ready : RealWifiAuditPhase

    /**
     * External verifier capability is Unavailable for a password strategy.
     * Persistent screen state — START stays disabled; not a terminal “error toast”.
     */
    data object BackendUnavailable : RealWifiAuditPhase

    /** Search Engine V2 against an external CandidateVerifier backend. */
    data object Searching : RealWifiAuditPhase

    data object Stopping : RealWifiAuditPhase

    data class Completed(
        val outcome: RealWifiAuditOutcome,
    ) : RealWifiAuditPhase

    data class Failed(
        val reason: RealWifiAuditFailureReason,
    ) : RealWifiAuditPhase
}

/** Diagnostic snapshot — no secrets. */
data class RealWifiAuditTechnicalDetails(
    val strategyName: String,
    val plannerReady: Boolean,
    val searchEngineReady: Boolean,
    val externalVerifierLabel: String,
)

sealed interface RealWifiSearchOutcome {
    data object Found : RealWifiSearchOutcome

    data object Exhausted : RealWifiSearchOutcome

    data object LimitReached : RealWifiSearchOutcome

    data object Cancelled : RealWifiSearchOutcome
}

sealed interface RealWifiAuditOutcome {
    data class Completed(
        val search: RealWifiSearchOutcome,
    ) : RealWifiAuditOutcome

    data object Cancelled : RealWifiAuditOutcome

    data object Unsupported : RealWifiAuditOutcome

    data class AssessmentOnly(
        val strategy: RealWifiAuditStrategy,
    ) : RealWifiAuditOutcome

    data class Unavailable(
        val reason: RealWifiAuditUnavailableReason,
    ) : RealWifiAuditOutcome
}

sealed interface RealWifiAuditUnavailableReason {
    data object NotImplemented : RealWifiAuditUnavailableReason

    data object MissingPermissions : RealWifiAuditUnavailableReason

    data object CapabilityUnavailable : RealWifiAuditUnavailableReason

    /**
     * Password-search strategies need an opaque external verification backend.
     * The application does **not** know the target secret.
     */
    data object ExternalVerifierUnavailable : RealWifiAuditUnavailableReason
}

sealed interface RealWifiAuditFailureReason {
    data object PlatformError : RealWifiAuditFailureReason

    data object SearchFailed : RealWifiAuditFailureReason

    data object Unknown : RealWifiAuditFailureReason
}

data class RealWifiAuditProgress(
    val strategyLabel: String,
    val candidatesProcessed: Long = 0,
    val elapsedMillis: Long = 0,
    val ratePerSecond: Double? = null,
    val workerCount: Int = 1,
    val statusMessage: String? = null,
)

fun interface RealWifiAuditProgressSink {
    fun onProgress(progress: RealWifiAuditProgress)
}

/**
 * Search Engine V2 frontier for RealWifiAudit.
 *
 * Candidate verification goes through [ExternalCandidateVerificationPort]
 * (black-box). Never takes a known secret. Never calls Android Wi‑Fi from Lab.
 */
interface RealWifiSearchPort {
    suspend fun search(
        plan: RealWifiAuditPlan,
        progress: RealWifiAuditProgressSink,
    ): RealWifiSearchExecutionResult

    fun cancel()
}

sealed interface RealWifiSearchExecutionResult {
    data class Found(
        val progress: RealWifiAuditProgress,
    ) : RealWifiSearchExecutionResult

    data class Exhausted(
        val progress: RealWifiAuditProgress,
    ) : RealWifiSearchExecutionResult

    data class LimitReached(
        val progress: RealWifiAuditProgress,
    ) : RealWifiSearchExecutionResult

    data class Cancelled(
        val progress: RealWifiAuditProgress,
    ) : RealWifiSearchExecutionResult

    data class Failed(
        val reason: RealWifiAuditFailureReason,
        val progress: RealWifiAuditProgress?,
    ) : RealWifiSearchExecutionResult

    data class Unavailable(
        val reason: RealWifiAuditUnavailableReason,
    ) : RealWifiSearchExecutionResult
}
