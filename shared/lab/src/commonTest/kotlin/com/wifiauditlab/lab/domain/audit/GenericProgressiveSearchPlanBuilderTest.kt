package com.wifiauditlab.lab.domain.audit

import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.Alphabet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class GenericProgressiveSearchPlanBuilderTest {
    private val builder = GenericProgressiveSearchPlanBuilder()

    @Test
    fun build_uses_all_generic_stages_and_weight_sum_100() {
        val plan =
            builder.build(
                maxAttempts = CombinationCount.of(1_000_000),
                maxDuration = 30.seconds,
                calibratedThroughput = 50_000.0,
            )

        assertEquals(GenericProgressiveAuditPolicy.STAGES.size, plan.stages.size)
        assertEquals(
            GenericProgressiveAuditPolicy.TOTAL_WEIGHT,
            plan.stages.sumOf { it.budget.weight },
        )
        assertEquals(
            GenericProgressiveAuditPolicy.STAGES.map { it.id },
            plan.stages.map { it.id.value },
        )
        assertEquals(GenericProgressiveSearchPlanBuilder.STRATEGY_ID, plan.searchPlan.strategyId)
        assertEquals(GenericProgressiveSearchPlanBuilder.DETERMINISTIC_SEED, plan.searchPlan.seed)
        assertTrue(plan.searchPlan.buckets.isNotEmpty())
        assertTrue(plan.stagesMayOverlap)
        assertNotNull(plan.budgetedAttemptCapacity)
    }

    @Test
    fun buckets_span_multiple_alphabets_and_lengths() {
        val plan =
            builder.build(
                maxAttempts = CombinationCount.of(500_000),
                maxDuration = null,
            )

        val alphabets = plan.searchPlan.buckets.map { it.alphabet }.toSet()
        val lengths = plan.searchPlan.buckets.map { it.length }.toSet()
        assertTrue(alphabets.size > 1, "expected multi-alphabet progressive buckets, got $alphabets")
        assertTrue(lengths.size > 1, "expected multi-length progressive buckets, got $lengths")
        assertTrue(Alphabet.DIGITS in alphabets)
        assertTrue(Alphabet.LOWERCASE in alphabets)
        assertFalse(plan.searchPlan.buckets.any { it.searchSpaceSize.isZero })
    }

    @Test
    fun identical_budgets_are_deterministic() {
        val a =
            builder.build(
                maxAttempts = CombinationCount.of(2_000_000),
                maxDuration = 45.seconds,
                calibratedThroughput = 20_000.0,
            )
        val b =
            builder.build(
                maxAttempts = CombinationCount.of(2_000_000),
                maxDuration = 45.seconds,
                calibratedThroughput = 20_000.0,
            )
        assertEquals(a.searchPlan.strategyId, b.searchPlan.strategyId)
        assertEquals(a.searchPlan.seed, b.searchPlan.seed)
        assertEquals(a.searchPlan.buckets.size, b.searchPlan.buckets.size)
        assertEquals(a.searchPlan.searchSpace, b.searchPlan.searchSpace)
        assertEquals(a.stages.map { it.id }, b.stages.map { it.id })
        assertEquals(a.budgetedAttemptCapacity, b.budgetedAttemptCapacity)
    }
}
