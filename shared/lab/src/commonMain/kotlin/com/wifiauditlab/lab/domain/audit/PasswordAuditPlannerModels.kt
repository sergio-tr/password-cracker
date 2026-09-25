package com.wifiauditlab.lab.domain.audit

import com.wifiauditlab.core.audit.PasswordAuditInapplicableReason
import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.Alphabet
import com.wifiauditlab.lab.domain.LabSearchPlan
import com.wifiauditlab.lab.domain.LabSecretPolicy
import com.wifiauditlab.lab.domain.LengthPolicy
import com.wifiauditlab.lab.domain.SearchLimits
import com.wifiauditlab.lab.domain.engine.SearchFeasibility
import com.wifiauditlab.lab.engine.LabParallelEngineVersion
import kotlin.jvm.JvmInline
import kotlin.time.Duration

/**
 * Blind audit context: applicability only. Never carries a password, length,
 * character set derived from a target, or strength-analysis output.
 */
data class PasswordAuditContext(
    val sharedPasswordApplicable: Boolean,
    val inapplicableReason: PasswordAuditInapplicableReason? = null,
    /** Required when [sharedPasswordApplicable] is true; selects Wi‑Fi PSK stage policy. */
    val searchProfile: SharedPasswordSearchProfile? = null,
)

/**
 * Device performance inputs for automatic planning.
 * [calibratedAttemptsPerSecond] is measured throughput when available.
 */
data class PasswordAuditPerformanceProfile(
    val calibratedAttemptsPerSecond: Double? = null,
    val availableProcessors: Int = 1,
) {
    init {
        require(availableProcessors >= 1) { "availableProcessors must be >= 1" }
        require(calibratedAttemptsPerSecond == null || calibratedAttemptsPerSecond > 0.0) {
            "calibratedAttemptsPerSecond must be positive when present"
        }
    }
}

@JvmInline
value class PasswordAuditStageId(val value: String) {
    init {
        require(value.isNotBlank())
    }

    override fun toString(): String = value
}

/** Generic candidate model for a progressive stage (alphabet × length range). */
data class CandidateModel(
    val alphabet: Alphabet,
    val lengthPolicy: LengthPolicy,
)

data class StageBudget(
    val maxAttempts: CombinationCount?,
    val maxDuration: Duration?,
    val weight: Int,
)

data class PasswordAuditStage(
    val id: PasswordAuditStageId,
    val candidateModel: CandidateModel,
    val priority: Int,
    val budget: StageBudget,
    val estimatedSpace: CombinationCount,
    /** True when this stage's space may overlap earlier reduced stages. */
    val mayOverlapPriorStages: Boolean,
)

data class AutomaticPlanExplanation(
    val headline: PlanExplanationHeadline = PlanExplanationHeadline.AutomaticConfiguration,
    val details: List<PlanExplanationDetail>,
)

sealed interface PasswordAuditEngineChoice {
    data object BaselineSequential : PasswordAuditEngineChoice

    data class Parallel(
        val version: LabParallelEngineVersion,
    ) : PasswordAuditEngineChoice
}

/**
 * Complete automatic plan. Contains no password material.
 * [totalCandidateSpace] is the union of stage spaces (may over-count if stages overlap).
 * [budgetedAttemptCapacity] is what the budget allows to try (exact and/or estimated).
 */
data class PasswordAuditPlan(
    val searchPlan: LabSearchPlan,
    val searchLimits: SearchLimits,
    val stages: List<PasswordAuditStage>,
    val workerCount: Int,
    val workerSelectionReason: String,
    val engine: PasswordAuditEngineChoice,
    val engineSelectionReason: String,
    val totalCandidateSpace: CombinationCount,
    val budgetedAttemptCapacity: CombinationCount?,
    val feasibility: SearchFeasibility,
    val explanation: AutomaticPlanExplanation,
    val stagesMayOverlap: Boolean,
    /** Policy used when attaching an [com.wifiauditlab.lab.domain.engine.CandidateVerifier]. */
    val blindChallengePolicy: LabSecretPolicy,
)

sealed interface PasswordAuditPlanResult {
    data class Ready(
        val plan: PasswordAuditPlan,
    ) : PasswordAuditPlanResult

    data class NotApplicable(
        val reason: PasswordAuditInapplicableReason,
    ) : PasswordAuditPlanResult
}

/**
 * Builds a blind, target-free search plan for known-password audits.
 * Implementations must not accept password material of any kind.
 */
interface AutomaticPasswordAuditPlanner {
    fun createPlan(
        context: PasswordAuditContext,
        performance: PasswordAuditPerformanceProfile,
        budget: PasswordAuditBudget,
    ): PasswordAuditPlanResult
}

/**
 * Optional protocol-cost port. Security family applicability lives in assessment;
 * this only estimates local verification cost interpretation for reporting.
 */
fun interface AuthenticationCostModel {
    fun estimateLocalVerificationCost(performance: PasswordAuditPerformanceProfile): AuthenticationCostEstimate
}

data class AuthenticationCostEstimate(
    val attemptsPerSecond: Double?,
    val quality: CostEstimateQuality,
    val notes: String,
)

enum class CostEstimateQuality {
    Measured,
    Estimated,
    Modelled,
}
