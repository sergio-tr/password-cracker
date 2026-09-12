package com.wifiauditlab.lab.engine

import com.wifiauditlab.core.math.CombinationCount
import kotlin.test.Test
import kotlin.test.assertTrue

class PlanScorerTest {
    @Test
    fun higher_probability_or_lower_cost_increases_score() {
        val scorer = PlanScorer()
        val cheap = scorer.score(0.2, CombinationCount.of(10))
        val expensive = scorer.score(0.2, CombinationCount.of(1_000))
        val likelier = scorer.score(0.8, CombinationCount.of(10))
        assertTrue(cheap > expensive)
        assertTrue(likelier > cheap)
    }

    @Test
    fun weights_belong_to_the_scorer_not_the_engine() {
        val cheapBias = PlanScorer(PlanScoreWeights(probability = 1.0, cost = 10.0))
        val default = PlanScorer()
        val space = CombinationCount.of(100)
        assertTrue(cheapBias.score(0.5, space) < default.score(0.5, space))
    }
}
