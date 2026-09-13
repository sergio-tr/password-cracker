package com.wifiauditlab.assessment.domain.audit

import com.wifiauditlab.assessment.domain.security.SecurityAssessment
import com.wifiauditlab.assessment.domain.security.SecurityRating
import com.wifiauditlab.core.math.CombinationCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class PasswordAuditResultComposerTest {
    private val structuralModerate =
        PasswordStrengthAssessment(
            rating = PasswordResistanceRating.MODERATE,
            length = 10,
            charsetDiversity = 2,
            summary = "demo",
        )

    private val structuralWeak =
        structuralModerate.copy(rating = PasswordResistanceRating.VERY_LOW, length = 4)

    private val structuralStrong =
        structuralModerate.copy(
            rating = PasswordResistanceRating.HIGH,
            length = 14,
            charsetDiversity = 3,
        )

    private val highConfig =
        SecurityAssessment(
            rating = SecurityRating.HIGH,
            headline = "WPA3 Personal",
            plainExplanation = "Configuración moderna",
            technicalSummary = "SAE",
            findings = emptyList(),
        )

    private val insecureConfig =
        SecurityAssessment(
            rating = SecurityRating.INSECURE,
            headline = "WEP",
            plainExplanation = "Obsoleto",
            technicalSummary = "WEP",
            findings = emptyList(),
        )

    @Test
    fun found_low_resistance_with_measured_metrics() {
        val report =
            PasswordAuditResultComposer.compose(
                searchOutcome =
                    PasswordSearchOutcomeKind.Found(
                        attempts = CombinationCount.of(284_193),
                        duration = 4.seconds,
                        attemptsPerSecond = 74_787.0,
                    ),
                structural = structuralWeak,
                networkAssessment = highConfig,
                familyDisplayLabel = "WPA3 Personal",
            )
        assertEquals(PasswordResistanceRating.VERY_LOW, report.passwordResistance)
        assertEquals("Contraseña encontrada", report.headline)
        assertTrue(report.performanceMetrics.any { it.quality == EvidenceQuality.Measured })
        assertTrue(report.networkConfigLabel!!.contains("Alta"))
        assertTrue(report.recommendations.any { it.id == "longer" })
        // High Wi-Fi config must not upgrade weak password.
        assertTrue(report.passwordResistance.ordinal <= PasswordResistanceRating.LOW.ordinal)
    }

    @Test
    fun found_after_large_budget_still_caps_at_low() {
        val report =
            PasswordAuditResultComposer.compose(
                searchOutcome =
                    PasswordSearchOutcomeKind.Found(
                        attempts = CombinationCount.of(5_000_000),
                        duration = 60.seconds,
                        attemptsPerSecond = 80_000.0,
                    ),
                structural = structuralStrong,
            )
        assertEquals(PasswordResistanceRating.LOW, report.passwordResistance)
    }

    @Test
    fun limit_reached_survived_budget() {
        val report =
            PasswordAuditResultComposer.compose(
                searchOutcome =
                    PasswordSearchOutcomeKind.LimitReached(
                        attempts = CombinationCount.of(100_000_000),
                        duration = 300.seconds,
                        budgetSummary = "100M · 5 min",
                    ),
                structural = structuralModerate,
                budgetedAttemptCapacity = CombinationCount.of(100_000_000),
            )
        assertEquals(PasswordResistanceRating.MODERATE, report.passwordResistance)
        assertTrue(report.headline.contains("límite") || report.headline.contains("resistencia"))
        assertTrue(report.performanceMetrics.any { it.quality == EvidenceQuality.Estimated })
        assertTrue(report.classificationNotes.any { it.contains("presupuesto") })
    }

    @Test
    fun limit_reached_with_strong_structure_reports_high_within_model() {
        val report =
            PasswordAuditResultComposer.compose(
                searchOutcome =
                    PasswordSearchOutcomeKind.LimitReached(
                        attempts = CombinationCount.of(50_000_000),
                        duration = 300.seconds,
                        budgetSummary = "50M",
                    ),
                structural = structuralStrong,
            )
        assertEquals(PasswordResistanceRating.HIGH, report.passwordResistance)
        assertEquals("Alta resistencia dentro del modelo probado", report.headline)
    }

    @Test
    fun cancelled_yields_unknown_without_full_conclusion() {
        val report =
            PasswordAuditResultComposer.compose(
                searchOutcome =
                    PasswordSearchOutcomeKind.Cancelled(
                        attempts = CombinationCount.of(1_822_301),
                        duration = 18.seconds,
                    ),
                structural = structuralModerate,
            )
        assertEquals(PasswordResistanceRating.UNKNOWN, report.passwordResistance)
        assertEquals("Auditoría detenida", report.headline)
        assertTrue(report.classificationNotes.any { it.contains("cancelada") })
    }

    @Test
    fun exhausted_is_not_does_not_exist() {
        val report =
            PasswordAuditResultComposer.compose(
                searchOutcome =
                    PasswordSearchOutcomeKind.Exhausted(
                        attempts = CombinationCount.of(1_000_000),
                        duration = 20.seconds,
                    ),
                structural = structuralStrong,
            )
        assertEquals("No encontrada dentro del modelo probado", report.headline)
        assertTrue(report.classificationNotes.any { it.contains("no exista") })
        assertEquals(PasswordResistanceRating.HIGH, report.passwordResistance)
    }

    @Test
    fun engine_failure_unknown() {
        val report =
            PasswordAuditResultComposer.compose(
                searchOutcome = PasswordSearchOutcomeKind.Failed("boom"),
                structural = structuralModerate,
            )
        assertEquals(PasswordResistanceRating.UNKNOWN, report.passwordResistance)
        assertTrue(report.headline.contains("error"))
    }

    @Test
    fun wpa3_high_config_with_weak_password_keeps_split() {
        val report =
            PasswordAuditResultComposer.compose(
                discoveredWithinBudget = true,
                cancelled = false,
                failed = false,
                structural = structuralWeak,
                measuredAttempts = CombinationCount.of(100),
                measuredDuration = 1.seconds,
                budgetedAttemptCapacity = CombinationCount.of(1_000_000),
                networkAssessment = highConfig,
                familyDisplayLabel = "WPA3 Personal",
            )
        assertTrue(report.networkConfigLabel!!.startsWith("Alta"))
        assertTrue(
            report.passwordResistance == PasswordResistanceRating.VERY_LOW ||
                report.passwordResistance == PasswordResistanceRating.LOW,
        )
    }

    @Test
    fun insecure_config_with_strong_observed_limit_keeps_password_high() {
        val report =
            PasswordAuditResultComposer.compose(
                searchOutcome =
                    PasswordSearchOutcomeKind.LimitReached(
                        attempts = CombinationCount.of(10_000_000),
                        duration = 60.seconds,
                        budgetSummary = "10M",
                    ),
                structural = structuralStrong,
                networkAssessment = insecureConfig,
                familyDisplayLabel = "WEP",
            )
        assertEquals(PasswordResistanceRating.HIGH, report.passwordResistance)
        assertTrue(report.recommendations.any { it.id == "upgrade-wifi" })
        assertTrue(report.networkConfigLabel!!.contains("Insegura"))
    }

    @Test
    fun measured_estimated_modelled_labels_present() {
        val report =
            PasswordAuditResultComposer.compose(
                searchOutcome =
                    PasswordSearchOutcomeKind.Found(
                        attempts = CombinationCount.of(42),
                        duration = 1.seconds,
                        attemptsPerSecond = 40.0,
                    ),
                structural = structuralModerate,
                budgetedAttemptCapacity = CombinationCount.of(1000),
            )
        val qualities = report.performanceMetrics.map { it.quality }.toSet()
        assertTrue(EvidenceQuality.Measured in qualities)
        assertTrue(EvidenceQuality.Estimated in qualities)
        assertTrue(EvidenceQuality.Modelled in qualities)
    }

    @Test
    fun bridge_compose_preserves_discovered_behaviour() {
        val report =
            PasswordAuditResultComposer.compose(
                discoveredWithinBudget = true,
                cancelled = false,
                failed = false,
                structural = structuralModerate,
                measuredAttempts = CombinationCount.of(42),
                measuredDuration = 1.seconds,
                budgetedAttemptCapacity = CombinationCount.of(1000),
            )
        assertTrue(report.searchOutcome is PasswordSearchOutcomeKind.Found)
        assertTrue(
            report.passwordResistance == PasswordResistanceRating.LOW ||
                report.passwordResistance == PasswordResistanceRating.VERY_LOW,
        )
    }
}
