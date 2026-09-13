package com.wifiauditlab.assessment.domain.audit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeuristicSecretStrengthAnalyzerTest {
    private val analyzer = HeuristicSecretStrengthAnalyzer()

    @Test
    fun shortPassword_isVeryLow() {
        val result = analyzer.analyze("abc")
        assertEquals(PasswordResistanceRating.VERY_LOW, result.rating)
        assertEquals(3, result.length)
        assertFalseMeasured(result)
    }

    @Test
    fun longDiversePassword_isAtLeastHigh() {
        val result = analyzer.analyze("Correct-Horse-Battery-1")
        assertTrue(
            result.rating == PasswordResistanceRating.HIGH ||
                result.rating == PasswordResistanceRating.VERY_HIGH,
        )
        assertTrue(result.charsetDiversity >= 3)
        assertFalseMeasured(result)
    }

    @Test
    fun repeatedPassword_isVeryLow() {
        val result = analyzer.analyze("aaaaaaaa")
        assertEquals(PasswordResistanceRating.VERY_LOW, result.rating)
    }

    @Test
    fun summary_mentionsStructuralScope() {
        val result = analyzer.analyze("password")
        assertTrue(result.summary.contains("estructural") || result.summary.contains("no prioriza"))
    }

    private fun assertFalseMeasured(result: PasswordStrengthAssessment) {
        assertEquals(false, result.measuredSearch)
    }
}
