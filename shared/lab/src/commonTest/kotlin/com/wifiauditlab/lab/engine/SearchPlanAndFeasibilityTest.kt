package com.wifiauditlab.lab.engine

import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.Alphabet
import com.wifiauditlab.lab.domain.LabChallenge
import com.wifiauditlab.lab.domain.LengthPolicy
import com.wifiauditlab.lab.domain.SearchLimits
import com.wifiauditlab.lab.domain.engine.FeasibilityRating
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchPlanAndFeasibilityTest {

    private val optimizer = DefaultSearchPlanOptimizer()
    private val analyzer = DefaultSearchFeasibilityAnalyzer()

    @Test
    fun optimizer_builds_ascending_buckets_and_correct_space() {
        val challenge = LabChallenge.withHiddenSecret(Alphabet.DIGITS, LengthPolicy(1, 2), seed = 1)
        val plan = optimizer.optimize(challenge, BruteForceSearchStrategy.ID)

        assertEquals(2, plan.totalBuckets)
        assertEquals(listOf(1, 2), plan.buckets.map { it.length })
        assertEquals(listOf(0, 1), plan.buckets.map { it.index })
        assertEquals(CombinationCount.of(110), plan.searchSpace) // 10 + 100
        val weightSum = plan.buckets.sumOf { it.expectedRelativeWeight }
        assertTrue(weightSum > 0.99 && weightSum < 1.01)
    }

    @Test
    fun tiny_space_is_reasonable() {
        val challenge = LabChallenge.withHiddenSecret(Alphabet.DIGITS, LengthPolicy.exactly(6), seed = 1)
        val plan = optimizer.optimize(challenge, BruteForceSearchStrategy.ID)
        val feasibility = analyzer.analyze(
            plan,
            SearchLimits.of(maxAttempts = CombinationCount.of(1_000_000)),
            FixedThroughputEstimator(),
        )
        assertEquals(FeasibilityRating.Reasonable, feasibility.rating)
    }

    @Test
    fun medium_space_is_expensive() {
        // 10^9 combinations at 500k/s ≈ 2000 s → Expensive.
        val challenge = LabChallenge.withHiddenSecret(Alphabet.DIGITS, LengthPolicy.exactly(9), seed = 1)
        val plan = optimizer.optimize(challenge, BruteForceSearchStrategy.ID)
        val feasibility = analyzer.analyze(
            plan,
            SearchLimits.of(maxDuration = kotlin.time.Duration.parse("1h")),
            FixedThroughputEstimator(),
        )
        assertEquals(FeasibilityRating.Expensive, feasibility.rating)
    }

    @Test
    fun space_beyond_64_bits_is_impractical() {
        val challenge = LabChallenge.withHiddenSecret(Alphabet.ALPHANUMERIC, LengthPolicy.exactly(20), seed = 1)
        val plan = optimizer.optimize(challenge, BruteForceSearchStrategy.ID)
        val feasibility = analyzer.analyze(
            plan,
            SearchLimits.of(maxDuration = kotlin.time.Duration.parse("1h")),
            FixedThroughputEstimator(),
        )
        assertEquals(FeasibilityRating.Impractical, feasibility.rating)
        assertTrue(feasibility.reason.isNotBlank())
    }
}
