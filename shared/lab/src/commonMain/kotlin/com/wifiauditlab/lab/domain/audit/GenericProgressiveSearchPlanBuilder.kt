package com.wifiauditlab.lab.domain.audit

import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.LabSearchPlan
import com.wifiauditlab.lab.domain.SearchBucket
import com.wifiauditlab.lab.domain.SearchLimits
import com.wifiauditlab.lab.domain.SearchStrategyId
import com.wifiauditlab.lab.engine.FixedThroughputEstimator
import kotlin.time.Duration

/**
 * Builds a multi-stage [LabSearchPlan] for **synthetic** Guided Lab experiments
 * from [GenericProgressiveAuditPolicy.STAGES]. Product PSK audits use
 * [DefaultAutomaticPasswordAuditPlanner] instead.
 *
 * Stage weights are relative heuristics (sum 100), not empirical ASAP priors.
 */
class GenericProgressiveSearchPlanBuilder(
    private val defaultThroughput: Double = FixedThroughputEstimator.DEFAULT_THROUGHPUT,
) {
    fun build(
        maxAttempts: CombinationCount?,
        maxDuration: Duration?,
        calibratedThroughput: Double? = null,
    ): GenericProgressiveSearchPlan {
        require(maxAttempts != null || maxDuration != null) {
            "synthetic progressive plan requires an attempt and/or duration budget"
        }

        val specs = GenericProgressiveAuditPolicy.STAGES
        val weights = specs.map { it.budgetWeight }
        val throughput = calibratedThroughput ?: defaultThroughput

        val estimatedFromDuration =
            DefaultAutomaticPasswordAuditPlanner.estimateAttemptCapacity(throughput, maxDuration)
        val budgetedAttemptCapacity =
            DefaultAutomaticPasswordAuditPlanner.coalesceCapacity(maxAttempts, estimatedFromDuration)

        val attemptShares = StageBudgetAllocator.allocateAttempts(budgetedAttemptCapacity, weights)
        val durationShares =
            DefaultAutomaticPasswordAuditPlanner.allocateDurations(maxDuration, weights)

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

        require(stages.none { it.estimatedSpace.isZero }) { "generic policy produced an empty stage" }

        val buckets = buildBuckets(stages)
        val searchPlan =
            LabSearchPlan(
                strategyId = STRATEGY_ID,
                buckets = buckets,
                seed = DETERMINISTIC_SEED,
            )

        val searchLimits =
            SearchLimits.of(
                maxDuration = maxDuration,
                maxAttempts = maxAttempts ?: budgetedAttemptCapacity,
            )

        return GenericProgressiveSearchPlan(
            searchPlan = searchPlan,
            searchLimits = searchLimits,
            stages = stages,
            budgetedAttemptCapacity = budgetedAttemptCapacity,
            stagesMayOverlap = stages.any { it.mayOverlapPriorStages },
        )
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
        require(buckets.isNotEmpty()) { "generic progressive plan produced no buckets" }
        return buckets
    }

    companion object {
        val STRATEGY_ID: SearchStrategyId = SearchStrategyId("generic-progressive-synthetic")

        /** Fixed seed so identical budgets always yield an identical plan. */
        const val DETERMINISTIC_SEED: Long = 0L
    }
}

/**
 * Synthetic progressive plan output. Contains no password material.
 * [searchPlanSummary] for PSK explainability is intentionally omitted — stages
 * here are lab heuristics, not [SharedPasswordSearchProfile]-backed audits.
 */
data class GenericProgressiveSearchPlan(
    val searchPlan: LabSearchPlan,
    val searchLimits: SearchLimits,
    val stages: List<PasswordAuditStage>,
    val budgetedAttemptCapacity: CombinationCount?,
    val stagesMayOverlap: Boolean,
)
