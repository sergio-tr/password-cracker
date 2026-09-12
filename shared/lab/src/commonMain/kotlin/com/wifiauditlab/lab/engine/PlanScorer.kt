package com.wifiauditlab.lab.engine

import com.wifiauditlab.core.math.CombinationCount
import kotlin.math.ln
import kotlin.math.max

/**
 * Weights for plan scoring. They live on the strategy/config, never inside the
 * search engine.
 */
data class PlanScoreWeights(
    val probability: Double = 1.0,
    val cost: Double = 1.0,
) {
    init {
        require(probability > 0.0) { "probability weight must be positive" }
        require(cost > 0.0) { "cost weight must be positive" }
    }
}

/**
 * Conceptual score: expected synthetic probability / estimated computational cost.
 * Huge spaces use a log scale so scores stay comparable without overflowing.
 */
class PlanScorer(private val weights: PlanScoreWeights = PlanScoreWeights()) {
    fun score(
        expectedProbability: Double,
        estimatedCost: CombinationCount,
    ): Double {
        val cost = costScale(estimatedCost) * weights.cost
        return (expectedProbability * weights.probability) / max(cost, 1e-12)
    }

    private fun costScale(cost: CombinationCount): Double {
        val exact = cost.toLongOrNull()
        if (exact != null) return max(exact.toDouble(), 1.0)
        val digits = cost.toExactString().count { it.isDigit() }
        return ln(10.0) * digits
    }
}
