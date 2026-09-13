package com.wifiauditlab.assessment.domain.audit

import com.wifiauditlab.core.math.CombinationCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class PasswordAuditResultComposerTest {
    private val structural =
        PasswordStrengthAssessment(
            rating = PasswordResistanceRating.MODERATE,
            length = 10,
            charsetDiversity = 2,
            summary = "demo",
        )

    @Test
    fun discovered_within_budget_lowers_combined_resistance() {
        val report =
            PasswordAuditResultComposer.compose(
                discoveredWithinBudget = true,
                cancelled = false,
                failed = false,
                structural = structural,
                measuredAttempts = CombinationCount.of(42),
                measuredDuration = 1.seconds,
                budgetedAttemptCapacity = CombinationCount.of(1000),
            )
        assertEquals(PasswordResistanceRating.LOW, report.combinedResistance)
        assertTrue(report.discoveredWithinBudget)
        assertTrue(report.evidenceNotes.any { it.first == EvidenceQuality.Measured })
        assertTrue(report.structural!!.measuredSearch)
    }

    @Test
    fun surviving_budget_keeps_at_least_moderate() {
        val weak =
            structural.copy(rating = PasswordResistanceRating.VERY_LOW)
        val report =
            PasswordAuditResultComposer.compose(
                discoveredWithinBudget = false,
                cancelled = false,
                failed = false,
                structural = weak,
                measuredAttempts = CombinationCount.of(1000),
                measuredDuration = 5.seconds,
                budgetedAttemptCapacity = CombinationCount.of(1000),
            )
        assertEquals(PasswordResistanceRating.MODERATE, report.combinedResistance)
        assertFalse(report.discoveredWithinBudget)
    }

    @Test
    fun cancelled_yields_unknown() {
        val report =
            PasswordAuditResultComposer.compose(
                discoveredWithinBudget = false,
                cancelled = true,
                failed = false,
                structural = structural,
                measuredAttempts = CombinationCount.of(10),
                measuredDuration = 1.seconds,
                budgetedAttemptCapacity = null,
            )
        assertEquals(PasswordResistanceRating.UNKNOWN, report.combinedResistance)
    }
}
