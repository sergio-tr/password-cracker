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
    fun longDiversePassword_isHigh() {
        val result = analyzer.analyze("Correct-Horse-Battery-1")
        assertEquals(PasswordResistanceRating.HIGH, result.rating)
        assertTrue(result.charsetDiversity >= 3)
        assertFalseMeasured(result)
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
