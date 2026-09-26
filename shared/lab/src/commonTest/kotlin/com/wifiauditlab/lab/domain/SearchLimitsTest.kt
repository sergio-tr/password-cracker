package com.wifiauditlab.lab.domain

import com.wifiauditlab.core.math.CombinationCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SearchLimitsTest {
    @Test
    fun unbounded_search_is_invalid_by_default() {
        val violations = SearchLimits.validate(maxDuration = null, maxAttempts = null)
        assertTrue(SearchLimits.Violation.UNBOUNDED in violations)
    }

    @Test
    fun constructing_an_unbounded_search_throws() {
        assertFailsWith<IllegalArgumentException> {
            SearchLimits.of(maxDuration = null, maxAttempts = null)
        }
    }

    @Test
    fun untilCancelled_allows_unbounded_explicitly() {
        val limits = SearchLimits.untilCancelled()
        assertEquals(null, limits.maxDuration)
        assertEquals(null, limits.maxAttempts)
        assertTrue(limits.runUntilCancelled)
    }

    @Test
    fun untilCancelled_validate_has_no_unbounded_violation() {
        val violations =
            SearchLimits.validate(
                maxDuration = null,
                maxAttempts = null,
                runUntilCancelled = true,
            )
        assertTrue(violations.isEmpty())
    }

    @Test
    fun duration_only_is_valid() {
        val limits = SearchLimits.of(maxDuration = 30.seconds)
        assertEquals(30.seconds, limits.maxDuration)
    }

    @Test
    fun attempts_only_is_valid() {
        val limits = SearchLimits.of(maxAttempts = CombinationCount.of(1_000))
        assertEquals(CombinationCount.of(1_000), limits.maxAttempts)
    }

    @Test
    fun non_positive_batch_size_is_invalid() {
        val violations = SearchLimits.validate(maxAttempts = CombinationCount.of(1), batchSize = 0)
        assertTrue(SearchLimits.Violation.NON_POSITIVE_BATCH_SIZE in violations)
    }

    @Test
    fun zero_attempts_is_invalid() {
        val violations = SearchLimits.validate(maxAttempts = CombinationCount.ZERO)
        assertTrue(SearchLimits.Violation.NON_POSITIVE_ATTEMPTS in violations)
    }
}
