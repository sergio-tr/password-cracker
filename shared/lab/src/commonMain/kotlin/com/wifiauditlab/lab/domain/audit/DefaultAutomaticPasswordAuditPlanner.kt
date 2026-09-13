package com.wifiauditlab.lab.domain.audit

import com.wifiauditlab.core.audit.PasswordAuditInapplicableReason
import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.Alphabet
import com.wifiauditlab.lab.domain.LabSearchPlan
import com.wifiauditlab.lab.domain.LabSecretPolicy
import com.wifiauditlab.lab.domain.LengthPolicy
import com.wifiauditlab.lab.domain.SearchBucket
import com.wifiauditlab.lab.domain.SearchLimits
import com.wifiauditlab.lab.domain.SearchStrategyId
import com.wifiauditlab.lab.domain.engine.SearchFeasibility
import com.wifiauditlab.lab.domain.engine.SearchFeasibilityAnalyzer
import com.wifiauditlab.lab.domain.engine.SearchPerformanceEstimator
import com.wifiauditlab.lab.engine.DefaultSearchFeasibilityAnalyzer
import com.wifiauditlab.lab.engine.FixedThroughputEstimator
import com.wifiauditlab.lab.engine.LabParallelEngineVersion
import com.wifiauditlab.lab.engine.WorkerPoolConfig
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Target-blind automatic planner. Inputs are only context applicability,
 * performance profile, and budget — never a password or strength analysis.
 */
class DefaultAutomaticPasswordAuditPlanner(
    private val feasibilityAnalyzer: SearchFeasibilityAnalyzer = DefaultSearchFeasibilityAnalyzer(),
    private val defaultThroughput: Double = FixedThroughputEstimator.DEFAULT_THROUGHPUT,
    private val maxWorkers: Int = WorkerPoolConfig.DEFAULT_MAX_WORKERS,
) : AutomaticPasswordAuditPlanner {
    override fun createPlan(
        context: PasswordAuditContext,
        performance: PasswordAuditPerformanceProfile,
        budget: PasswordAuditBudget,
    ): PasswordAuditPlanResult {
        if (!context.sharedPasswordApplicable) {
            return PasswordAuditPlanResult.NotApplicable(
                context.inapplicableReason ?: PasswordAuditInapplicableReason.UnsupportedAuth,
            )
        }

        val specs = GenericProgressiveAuditPolicy.STAGES
        val weights = specs.map { it.budgetWeight }
        val throughput = performance.calibratedAttemptsPerSecond ?: defaultThroughput
        val estimator: SearchPerformanceEstimator = FixedThroughputEstimator(throughput)

        val estimatedFromDuration = estimateAttemptCapacity(throughput, budget.maxDuration)
        val budgetedAttemptCapacity =
            coalesceCapacity(budget.maxAttempts, estimatedFromDuration)

        val attemptShares = StageBudgetAllocator.allocateAttempts(budgetedAttemptCapacity, weights)
        val durationShares = allocateDurations(budget.maxDuration, weights)

        val stages =
            specs.mapIndexed { index, spec ->
                val space = GenericProgressiveAuditPolicy.exactSpace(spec.alphabet, spec.lengthPolicy)
                PasswordAuditStage(
                    id = PasswordAuditStageId(spec.id),
                    candidateModel = CandidateModel(spec.alphabet, spec.lengthPolicy),
                    priority = spec.priority,
                    budget =
                        StageBudget(
                            maxAttempts = attemptShares[index],
                            maxDuration = durationShares[index],
                            weight = spec.budgetWeight,
                        ),
                    estimatedSpace = space,
                    mayOverlapPriorStages = spec.mayOverlapPriorStages,
                )
            }

        require(stages.none { it.estimatedSpace.isZero }) { "policy produced an empty stage" }

        val buckets = buildBuckets(stages)
        val searchPlan =
            LabSearchPlan(
                strategyId = AUTOMATIC_STRATEGY_ID,
                buckets = buckets,
                seed = DETERMINISTIC_SEED,
            )
        val totalCandidateSpace = searchPlan.searchSpace
        val stagesMayOverlap = stages.any { it.mayOverlapPriorStages }

        val workerCount =
            WorkerPoolConfig.recommendedWorkerCount(
                availableProcessors = performance.availableProcessors,
                calibratedAttemptsPerSecond = performance.calibratedAttemptsPerSecond,
                maxWorkers = maxWorkers,
            )
        require(workerCount in 1..maxWorkers)
        val workerSelectionReason =
            when {
                performance.calibratedAttemptsPerSecond == null ->
                    "no calibration yet; using half of clamped CPU count (min 1)"
                performance.calibratedAttemptsPerSecond < WorkerPoolConfig.LOW_THROUGHPUT_THRESHOLD ->
                    "calibrated throughput below parallel threshold; using 1 worker"
                performance.calibratedAttemptsPerSecond < WorkerPoolConfig.HIGH_THROUGHPUT_THRESHOLD ->
                    "moderate calibrated throughput; using half of clamped CPU count"
                else -> "high calibrated throughput; using full clamped CPU count"
            }

        val (engine, engineReason) =
            if (workerCount == 1) {
                PasswordAuditEngineChoice.BaselineSequential to
                    "single worker uses baseline/indexed sequential engine as reference"
            } else {
                PasswordAuditEngineChoice.Parallel(LabParallelEngineVersion.V2) to
                    "multi-worker defaults to Search Engine V2 (indexed + dynamic range)"
            }

        val searchLimits =
            SearchLimits.of(
                maxDuration = budget.maxDuration,
                maxAttempts = budget.maxAttempts ?: budgetedAttemptCapacity,
                progressInterval = progressIntervalFor(budget),
                batchSize = SearchLimits.DEFAULT_BATCH_SIZE,
            )

        val totalFeasibility =
            feasibilityAnalyzer.analyze(searchPlan, searchLimits, estimator)
        val feasibility =
            budgetedFeasibility(budgetedAttemptCapacity, searchLimits, estimator)
                ?: totalFeasibility

        val explanation =
            AutomaticPlanExplanation(
                details =
                    listOf(
                        PlanExplanationDetail.WorkerCount(workerCount),
                        PlanExplanationDetail.StageCount(stages.size),
                        PlanExplanationDetail.BudgetLimit(budget.maxDuration, budget.maxAttempts),
                        PlanExplanationDetail.DeviceAdapted,
                    ),
            )

        return PasswordAuditPlanResult.Ready(
            PasswordAuditPlan(
                searchPlan = searchPlan,
                searchLimits = searchLimits,
                stages = stages,
                workerCount = workerCount,
                workerSelectionReason = workerSelectionReason,
                engine = engine,
                engineSelectionReason = engineReason,
                totalCandidateSpace = totalCandidateSpace,
                budgetedAttemptCapacity = budgetedAttemptCapacity,
                feasibility = feasibility,
                explanation = explanation,
                stagesMayOverlap = stagesMayOverlap,
                blindChallengePolicy = BLIND_CHALLENGE_POLICY,
            ),
        )
    }

    private fun budgetedFeasibility(
        budgeted: CombinationCount?,
        limits: SearchLimits,
        estimator: SearchPerformanceEstimator,
    ): SearchFeasibility? {
        if (budgeted == null || budgeted.isZero) return null
        val probe =
            LabSearchPlan(
                strategyId = AUTOMATIC_STRATEGY_ID,
                buckets =
                    listOf(
                        SearchBucket(
                            index = 0,
                            length = 1,
                            alphabet = Alphabet.DIGITS,
                            expectedRelativeWeight = 1.0,
                            searchSpaceSize = budgeted,
                        ),
                    ),
                seed = DETERMINISTIC_SEED,
            )
        return feasibilityAnalyzer.analyze(probe, limits, estimator)
    }

    private fun buildBuckets(stages: List<PasswordAuditStage>): List<SearchBucket> {
        var index = 0
        val buckets = mutableListOf<SearchBucket>()
        for (stage in stages.sortedBy { it.priority }) {
            val model = stage.candidateModel
            for (length in model.lengthPolicy.lengths) {
                val size = CombinationCount.alphabetPower(model.alphabet.size, length)
                if (size.isZero) continue
                buckets +=
                    SearchBucket(
                        index = index++,
                        length = length,
                        alphabet = model.alphabet,
                        expectedRelativeWeight = stage.budget.weight.toDouble(),
                        searchSpaceSize = size,
                        relativePriority = (1000 - stage.priority).toDouble(),
                    )
            }
        }
        require(buckets.isNotEmpty()) { "automatic plan produced no buckets" }
        return buckets
    }

    companion object {
        val AUTOMATIC_STRATEGY_ID: SearchStrategyId = SearchStrategyId("automatic-password-audit")

        /** Fixed seed so identical inputs always yield an identical plan. */
        const val DETERMINISTIC_SEED: Long = 0L

        val BLIND_CHALLENGE_POLICY: LabSecretPolicy =
            LabSecretPolicy(Alphabet.ALPHANUMERIC, LengthPolicy(1, 8))

        fun estimateAttemptCapacity(
            throughputAttemptsPerSecond: Double,
            duration: Duration?,
        ): CombinationCount? {
            if (duration == null || duration <= Duration.ZERO) return null
            require(throughputAttemptsPerSecond > 0.0)
            val seconds = duration.inWholeMilliseconds / 1_000.0
            if (seconds <= 0.0) return null
            val product = throughputAttemptsPerSecond * seconds
            if (product >= Long.MAX_VALUE.toDouble()) {
                return CombinationCount.of(Long.MAX_VALUE)
            }
            return CombinationCount.of(product.toLong().coerceAtLeast(1L))
        }

        fun coalesceCapacity(
            maxAttempts: CombinationCount?,
            estimatedFromDuration: CombinationCount?,
        ): CombinationCount? =
            when {
                maxAttempts != null && estimatedFromDuration != null ->
                    minOf(maxAttempts, estimatedFromDuration)
                maxAttempts != null -> maxAttempts
                else -> estimatedFromDuration
            }

        fun allocateDurations(
            total: Duration?,
            weights: List<Int>,
        ): List<Duration?> {
            if (total == null) return List(weights.size) { null }
            require(weights.isNotEmpty() && weights.all { it > 0 })
            val sum = weights.sum()
            val totalNanos = total.inWholeNanoseconds
            var remaining = totalNanos
            return weights.mapIndexed { index, weight ->
                if (index == weights.lastIndex) {
                    remaining.nanoseconds
                } else {
                    val share = (totalNanos * weight) / sum
                    remaining -= share
                    share.nanoseconds
                }
            }
        }

        private fun progressIntervalFor(budget: PasswordAuditBudget): Duration =
            when (budget.preset) {
                PasswordAuditBudgetPreset.Quick -> 200.milliseconds
                else -> SearchLimits.DEFAULT_PROGRESS_INTERVAL
            }
    }
}

/** Conservative cost model that mirrors calibrated throughput when present. */
class ThroughputAuthenticationCostModel(
    private val fallbackThroughput: Double = FixedThroughputEstimator.DEFAULT_THROUGHPUT,
) : AuthenticationCostModel {
    override fun estimateLocalVerificationCost(
        performance: PasswordAuditPerformanceProfile,
    ): AuthenticationCostEstimate {
        val measured = performance.calibratedAttemptsPerSecond
        return if (measured != null) {
            AuthenticationCostEstimate(
                attemptsPerSecond = measured,
                quality = CostEstimateQuality.Measured,
                notes = "local calibrated throughput",
            )
        } else {
            AuthenticationCostEstimate(
                attemptsPerSecond = fallbackThroughput,
                quality = CostEstimateQuality.Estimated,
                notes = "default lab throughput until device calibration exists",
            )
        }
    }
}
