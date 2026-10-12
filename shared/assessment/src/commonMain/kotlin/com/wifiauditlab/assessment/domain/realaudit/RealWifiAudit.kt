package com.wifiauditlab.assessment.domain.realaudit

import com.wifiauditlab.assessment.domain.real.RealNetworkConsent
import com.wifiauditlab.assessment.domain.real.RealNetworkReference
import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile

/**
 * Application request to audit a **user-selected** real Wi‑Fi network.
 * Contains no password / known-secret material.
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

sealed interface RealWifiAuditPhase {
    data object Idle : RealWifiAuditPhase

    data object Preparing : RealWifiAuditPhase

    data object Ready : RealWifiAuditPhase

    data object Running : RealWifiAuditPhase

    data object Stopping : RealWifiAuditPhase

    data class Completed(
        val outcome: RealWifiAuditOutcome,
    ) : RealWifiAuditPhase

    data class Failed(
        val reason: RealWifiAuditFailureReason,
    ) : RealWifiAuditPhase
}

sealed interface RealWifiAuditOutcome {
    data object Completed : RealWifiAuditOutcome

    data object Cancelled : RealWifiAuditOutcome

    data object Unsupported : RealWifiAuditOutcome

    data class Unavailable(
        val reason: RealWifiAuditUnavailableReason,
    ) : RealWifiAuditOutcome
}

sealed interface RealWifiAuditUnavailableReason {
    data object NotImplemented : RealWifiAuditUnavailableReason

    data object MissingPermissions : RealWifiAuditUnavailableReason

    data object CapabilityUnavailable : RealWifiAuditUnavailableReason
}

sealed interface RealWifiAuditFailureReason {
    data object PlatformError : RealWifiAuditFailureReason

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

sealed interface RealWifiAuditBackendResult {
    data object Completed : RealWifiAuditBackendResult

    data object Cancelled : RealWifiAuditBackendResult

    data class Unavailable(
        val reason: RealWifiAuditUnavailableReason,
    ) : RealWifiAuditBackendResult

    data class Failed(
        val reason: RealWifiAuditFailureReason,
    ) : RealWifiAuditBackendResult
}

/**
 * Platform-neutral backend frontier: orchestration / algorithms vs experimental
 * platform verification. Implementations live outside `:shared:lab`.
 */
interface RealWifiAuditBackend {
    suspend fun execute(
        plan: RealWifiAuditPlan,
        progress: RealWifiAuditProgressSink,
    ): RealWifiAuditBackendResult

    fun cancel()
}
