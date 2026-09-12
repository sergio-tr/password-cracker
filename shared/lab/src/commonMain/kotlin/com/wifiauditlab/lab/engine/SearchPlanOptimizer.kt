package com.wifiauditlab.lab.engine

import com.wifiauditlab.lab.domain.LabChallenge
import com.wifiauditlab.lab.domain.LabSearchPlan
import com.wifiauditlab.lab.domain.SearchLimits
import com.wifiauditlab.lab.domain.SearchStrategyId
import com.wifiauditlab.lab.domain.engine.SearchPlanOptimizer

/**
 * Default optimizer used by the baseline engine. Delegates to
 * [LengthPrioritizedStrategy] and stamps the caller-requested strategy id so
 * existing brute-force sessions keep a stable identifier.
 */
class DefaultSearchPlanOptimizer : SearchPlanOptimizer {
    private val delegate = LengthPrioritizedStrategy()

    override fun optimize(
        challenge: LabChallenge,
        strategyId: SearchStrategyId,
    ): LabSearchPlan {
        val planned = delegate.optimize(challenge, strategyId)
        return LabSearchPlan(strategyId, planned.buckets, planned.seed)
    }
}

/**
 * Exhaustive brute-force strategy over the challenge's alphabet and length policy.
 * It is the deterministic single-worker baseline required before any parallelism.
 */
class BruteForceSearchStrategy(
    private val optimizer: SearchPlanOptimizer = DefaultSearchPlanOptimizer(),
) : com.wifiauditlab.lab.domain.engine.LabSearchStrategy {
    override val id: SearchStrategyId = ID

    override fun supports(challenge: LabChallenge): Boolean = true

    override suspend fun createPlan(
        challenge: LabChallenge,
        limits: SearchLimits,
    ): LabSearchPlan = optimizer.optimize(challenge, id)

    companion object {
        val ID: SearchStrategyId = SearchStrategyId("brute-force")
    }
}
