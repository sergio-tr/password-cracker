package com.wifiauditlab.lab.engine

import com.wifiauditlab.lab.domain.Alphabet
import com.wifiauditlab.lab.domain.LabChallenge
import com.wifiauditlab.lab.domain.LengthPolicy
import com.wifiauditlab.lab.domain.SearchStrategyId
import com.wifiauditlab.lab.domain.engine.SearchPlanOptimizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SearchStrategyCoverageTest {
    private val challenge =
        LabChallenge.withHiddenSecret(
            alphabet = Alphabet.DIGITS,
            lengthPolicy = LengthPolicy(1, 3),
            seed = 9,
            syntheticLengthWeights = mapOf(1 to 0.05, 2 to 0.15, 3 to 0.80),
        )

    private val strategies: List<Pair<SearchStrategyId, SearchPlanOptimizer>> =
        listOf(
            UniformBaselineStrategy.ID to UniformBaselineStrategy(),
            LengthPrioritizedStrategy.ID to LengthPrioritizedStrategy(),
            TieredAlphabetStrategy.ID to TieredAlphabetStrategy(),
            SyntheticProbabilityWeightedStrategy.ID to SyntheticProbabilityWeightedStrategy(),
            AdaptiveSyntheticStrategy.ID to AdaptiveSyntheticStrategy(),
        )

    @Test
    fun every_strategy_covers_the_declared_space_without_duplicate_lengths() {
        strategies.forEach { (id, optimizer) ->
            val plan = optimizer.optimize(challenge, id)
            assertEquals(challenge.policy.searchSpace, plan.searchSpace, id.value)
            assertEquals(listOf(1, 2, 3).toSet(), plan.buckets.map { it.length }.toSet(), id.value)
            assertEquals(plan.buckets.size, plan.buckets.map { it.length }.distinct().size, id.value)
            plan.buckets.forEach { bucket ->
                assertEquals(challenge.alphabet, bucket.alphabet)
                assertEquals(bucket.searchSpaceSize, bucket.estimatedCost)
                assertEquals(bucket.policy.length.minLength, bucket.length)
            }
        }
    }

    @Test
    fun bucket_order_is_deterministic() {
        strategies.forEach { (id, optimizer) ->
            val first = optimizer.optimize(challenge, id).buckets.map { it.length }
            val second = optimizer.optimize(challenge, id).buckets.map { it.length }
            assertEquals(first, second, id.value)
        }
    }

    @Test
    fun strategy_priorities_and_orders_differ_as_expected() {
        val uniform = UniformBaselineStrategy().optimize(challenge, UniformBaselineStrategy.ID)
        val lengthFirst = LengthPrioritizedStrategy().optimize(challenge, LengthPrioritizedStrategy.ID)
        val weighted =
            SyntheticProbabilityWeightedStrategy().optimize(challenge, SyntheticProbabilityWeightedStrategy.ID)
        val adaptive = AdaptiveSyntheticStrategy().optimize(challenge, AdaptiveSyntheticStrategy.ID)
        val tiered = TieredAlphabetStrategy().optimize(challenge, TieredAlphabetStrategy.ID)

        assertEquals(listOf(1, 2, 3), uniform.buckets.map { it.length })
        assertEquals(listOf(1, 2, 3), lengthFirst.buckets.map { it.length })
        assertEquals(listOf(3, 2, 1), weighted.buckets.map { it.length })
        assertEquals(1, adaptive.buckets.first().length)
        assertEquals(uniform.buckets.map { it.length }, tiered.buckets.map { it.length })
        assertNotEquals(
            lengthFirst.buckets.first().relativePriority,
            tiered.buckets.first().relativePriority,
        )
        assertTrue(uniform.buckets.map { it.relativePriority }.distinct().size == 1)
        assertTrue(weighted.buckets.first().relativePriority > weighted.buckets.last().relativePriority)
    }
}
