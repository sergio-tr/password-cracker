package com.wifiauditlab.assessment.domain.audit

import com.wifiauditlab.assessment.domain.security.SecurityAssessment
import com.wifiauditlab.assessment.domain.security.SecurityRating
import com.wifiauditlab.core.math.CombinationCount
import kotlin.time.Duration

/**
 * Qualifies how a figure was obtained so the UI never presents estimates as
 * exact attacker wall-clock time.
 */
enum class EvidenceQuality {
    Measured,
    Estimated,
    Modelled,
}

/** Terminal shape of the local known-password search (never claims absolute crackability). */
sealed interface PasswordSearchOutcomeKind {
    data class Found(
        val attempts: CombinationCount?,
        val duration: Duration?,
        val attemptsPerSecond: Double?,
    ) : PasswordSearchOutcomeKind

    data class LimitReached(
        val attempts: CombinationCount?,
        val duration: Duration?,
        val budgetSummary: LimitReachedBudgetSummary,
    ) : PasswordSearchOutcomeKind

    /** Plan candidate space was fully walked without a hit (still not "password does not exist"). */
    data class Exhausted(
        val attempts: CombinationCount?,
        val duration: Duration?,
    ) : PasswordSearchOutcomeKind

    data class Cancelled(
        val attempts: CombinationCount?,
        val duration: Duration?,
    ) : PasswordSearchOutcomeKind

    data class Failed(
        val sanitizedMessage: String?,
    ) : PasswordSearchOutcomeKind
}

data class PerformanceMetric(
    val kind: PerformanceMetricKind,
    val value: String,
    val quality: EvidenceQuality,
)

/**
 * Full known-password audit deliverable for UI.
 * Keeps Wi-Fi configuration assessment separate from password resistance.
 * Must never be fed back into the Search Engine planner.
 */
data class WifiPasswordAuditResult(
    val headline: AuditOutcomeHeadline,
    val searchOutcome: PasswordSearchOutcomeKind,
    val passwordResistance: PasswordResistanceRating,
    val passwordStrength: PasswordStrengthAssessment?,
    val networkAssessment: SecurityAssessment?,
    val networkConfig: NetworkConfigPresentation?,
    val performanceMetrics: List<PerformanceMetric>,
    val recommendations: List<AuditRecommendationId>,
    val classificationNotes: List<ClassificationNote>,
    val detailsExpandedDefault: Boolean = false,
)

/** @deprecated Prefer [WifiPasswordAuditResult]; kept as type alias for gradual migration. */
typealias PasswordAuditResultReport = WifiPasswordAuditResult

/**
 * Builds a novice-friendly report.
 *
 * Classification criteria (transparent, not absolute):
 * - Found within budget → observed resistance is at most Low (local model).
 * - Survived budget / exhausted plan space → at least Moderate, may rise with structure.
 * - Cancelled / failed → Unknown (incomplete evidence).
 * - Structure (length, classes, repetition, sequences) informs but never alone claims High.
 * - Network [SecurityAssessment] is reported separately and never upgrades password rating.
 */
object PasswordAuditResultComposer {
    fun compose(
        searchOutcome: PasswordSearchOutcomeKind,
        structural: PasswordStrengthAssessment?,
        networkAssessment: SecurityAssessment? = null,
        budgetedAttemptCapacity: CombinationCount? = null,
    ): WifiPasswordAuditResult {
        val passwordResistance = classifyPasswordResistance(searchOutcome, structural)
        val headline = headlineFor(searchOutcome, passwordResistance)
        val metrics = performanceMetrics(searchOutcome, budgetedAttemptCapacity, structural)
        val recommendations =
            recommendationsFor(
                searchOutcome = searchOutcome,
                passwordResistance = passwordResistance,
                structural = structural,
                network = networkAssessment,
            )
        val notes =
            classificationNotes(
                searchOutcome = searchOutcome,
                passwordResistance = passwordResistance,
                structural = structural,
            )

        return WifiPasswordAuditResult(
            headline = headline,
            searchOutcome = searchOutcome,
            passwordResistance = passwordResistance,
            passwordStrength = structural?.copy(measuredSearch = searchOutcome is PasswordSearchOutcomeKind.Found),
            networkAssessment = networkAssessment,
            networkConfig = networkConfigPresentation(networkAssessment),
            performanceMetrics = metrics,
            recommendations = recommendations,
            classificationNotes = notes,
        )
    }

    /**
     * Convenience bridge used by the Android ViewModel while migrating call sites.
     */
    fun compose(
        discoveredWithinBudget: Boolean,
        cancelled: Boolean,
        failed: Boolean,
        structural: PasswordStrengthAssessment?,
        measuredAttempts: CombinationCount?,
        measuredDuration: Duration?,
        budgetedAttemptCapacity: CombinationCount?,
        attemptsPerSecond: Double? = null,
        exhausted: Boolean = false,
        networkAssessment: SecurityAssessment? = null,
        failureMessage: String? = null,
    ): WifiPasswordAuditResult {
        val outcome =
            when {
                failed ->
                    PasswordSearchOutcomeKind.Failed(failureMessage?.take(240))
                cancelled ->
                    PasswordSearchOutcomeKind.Cancelled(measuredAttempts, measuredDuration)
                discoveredWithinBudget ->
                    PasswordSearchOutcomeKind.Found(measuredAttempts, measuredDuration, attemptsPerSecond)
                exhausted ->
                    PasswordSearchOutcomeKind.Exhausted(measuredAttempts, measuredDuration)
                else ->
                    PasswordSearchOutcomeKind.LimitReached(
                        attempts = measuredAttempts,
                        duration = measuredDuration,
                        budgetSummary =
                            LimitReachedBudgetSummary(
                                measuredAttempts = measuredAttempts,
                                measuredDuration = measuredDuration,
                                budgetedAttemptCapacity = budgetedAttemptCapacity,
                            ),
                    )
            }
        return compose(
            searchOutcome = outcome,
            structural = structural,
            networkAssessment = networkAssessment,
            budgetedAttemptCapacity = budgetedAttemptCapacity,
        )
    }

    fun classifyPasswordResistance(
        searchOutcome: PasswordSearchOutcomeKind,
        structural: PasswordStrengthAssessment?,
    ): PasswordResistanceRating {
        val structure = structural?.rating ?: PasswordResistanceRating.MODERATE
        return when (searchOutcome) {
            is PasswordSearchOutcomeKind.Failed,
            is PasswordSearchOutcomeKind.Cancelled,
            -> PasswordResistanceRating.UNKNOWN
            is PasswordSearchOutcomeKind.Found ->
                weakerOf(structure, PasswordResistanceRating.LOW).let { base ->
                    val attempts = searchOutcome.attempts
                    if (attempts != null && !attempts.isZero && attempts < CombinationCount.of(10_000)) {
                        weakerOf(base, PasswordResistanceRating.VERY_LOW)
                    } else {
                        base
                    }
                }
            is PasswordSearchOutcomeKind.LimitReached ->
                strongerOf(structure, PasswordResistanceRating.MODERATE).let { base ->
                    if (structure == PasswordResistanceRating.HIGH ||
                        structure == PasswordResistanceRating.VERY_HIGH
                    ) {
                        strongerOf(base, PasswordResistanceRating.HIGH)
                    } else {
                        base
                    }
                }
            is PasswordSearchOutcomeKind.Exhausted ->
                strongerOf(structure, PasswordResistanceRating.HIGH)
        }
    }

    private fun headlineFor(
        outcome: PasswordSearchOutcomeKind,
        resistance: PasswordResistanceRating,
    ): AuditOutcomeHeadline =
        when (outcome) {
            is PasswordSearchOutcomeKind.Found -> AuditOutcomeHeadline.PasswordFound
            is PasswordSearchOutcomeKind.LimitReached ->
                if (resistance == PasswordResistanceRating.HIGH ||
                    resistance == PasswordResistanceRating.VERY_HIGH
                ) {
                    AuditOutcomeHeadline.HighResistanceWithinModel
                } else {
                    AuditOutcomeHeadline.NotFoundWithinLimit
                }
            is PasswordSearchOutcomeKind.Exhausted -> AuditOutcomeHeadline.NotFoundWithinModel
            is PasswordSearchOutcomeKind.Cancelled -> AuditOutcomeHeadline.AuditStopped
            is PasswordSearchOutcomeKind.Failed -> AuditOutcomeHeadline.AuditFailed
        }

    private fun performanceMetrics(
        outcome: PasswordSearchOutcomeKind,
        budgeted: CombinationCount?,
        structural: PasswordStrengthAssessment?,
    ): List<PerformanceMetric> {
        val list = mutableListOf<PerformanceMetric>()

        fun addMeasured(
            kind: PerformanceMetricKind,
            value: String,
        ) {
            list += PerformanceMetric(kind, value, EvidenceQuality.Measured)
        }

        when (outcome) {
            is PasswordSearchOutcomeKind.Found -> {
                outcome.attempts?.let { addMeasured(PerformanceMetricKind.CombinationsTried, it.toExactString()) }
                outcome.duration?.let { addMeasured(PerformanceMetricKind.MeasuredTime, formatDurationRaw(it)) }
                outcome.attemptsPerSecond?.let {
                    addMeasured(PerformanceMetricKind.Speed, it.toLong().toString())
                }
            }
            is PasswordSearchOutcomeKind.LimitReached -> {
                outcome.attempts?.let { addMeasured(PerformanceMetricKind.Attempts, it.toExactString()) }
                outcome.duration?.let { addMeasured(PerformanceMetricKind.Time, formatDurationRaw(it)) }
            }
            is PasswordSearchOutcomeKind.Exhausted -> {
                outcome.attempts?.let { addMeasured(PerformanceMetricKind.Attempts, it.toExactString()) }
                outcome.duration?.let { addMeasured(PerformanceMetricKind.Time, formatDurationRaw(it)) }
            }
            is PasswordSearchOutcomeKind.Cancelled -> {
                outcome.attempts?.let { addMeasured(PerformanceMetricKind.AttemptsPerformed, it.toExactString()) }
                outcome.duration?.let { addMeasured(PerformanceMetricKind.Time, formatDurationRaw(it)) }
            }
            is PasswordSearchOutcomeKind.Failed -> Unit
        }
        budgeted?.let {
            list +=
                PerformanceMetric(
                    kind = PerformanceMetricKind.BudgetCapacity,
                    value = it.toAbbreviatedString(),
                    quality = EvidenceQuality.Estimated,
                )
        }
        structural?.let {
            list +=
                PerformanceMetric(
                    kind = PerformanceMetricKind.ModelledStructure,
                    value = "${it.length}:${it.charsetDiversity}",
                    quality = EvidenceQuality.Modelled,
                )
        }
        return list
    }

    private fun recommendationsFor(
        searchOutcome: PasswordSearchOutcomeKind,
        passwordResistance: PasswordResistanceRating,
        structural: PasswordStrengthAssessment?,
        network: SecurityAssessment?,
    ): List<AuditRecommendationId> {
        val out = mutableListOf<AuditRecommendationId>()
        val weakPassword =
            passwordResistance == PasswordResistanceRating.VERY_LOW ||
                passwordResistance == PasswordResistanceRating.LOW ||
                searchOutcome is PasswordSearchOutcomeKind.Found
        if (weakPassword) {
            out += AuditRecommendationId.LongerPassword
            out += AuditRecommendationId.AvoidSimpleStructure
        }
        if (structural != null && structural.charsetDiversity < 3 && structural.length < 14) {
            out += AuditRecommendationId.MixCharsets
        }
        when (network?.rating) {
            SecurityRating.INSECURE, SecurityRating.LOW -> out += AuditRecommendationId.UpgradeWifi
            SecurityRating.MODERATE -> out += AuditRecommendationId.PreferWpa3
            SecurityRating.HIGH ->
                if (network.findings.any {
                        it.title.contains("transición", ignoreCase = true) ||
                            it.title.contains("transition", ignoreCase = true) ||
                            it.explanation.contains("transición", ignoreCase = true) ||
                            it.explanation.contains("transition", ignoreCase = true)
                    }
                ) {
                    out += AuditRecommendationId.AvoidTransition
                }
            null -> Unit
        }
        return out.distinct()
    }

    private fun classificationNotes(
        searchOutcome: PasswordSearchOutcomeKind,
        passwordResistance: PasswordResistanceRating,
        structural: PasswordStrengthAssessment?,
    ): List<ClassificationNote> =
        buildList {
            add(ClassificationNote.ResistanceMethodology)
            add(ClassificationNote.NoAbsoluteClaim)
            when (searchOutcome) {
                is PasswordSearchOutcomeKind.Found -> add(ClassificationNote.FoundWithinBudget)
                is PasswordSearchOutcomeKind.LimitReached -> add(ClassificationNote.SurvivedBudget)
                is PasswordSearchOutcomeKind.Exhausted -> add(ClassificationNote.ExhaustedPlanSpace)
                is PasswordSearchOutcomeKind.Cancelled -> add(ClassificationNote.CancelledIncomplete)
                is PasswordSearchOutcomeKind.Failed -> add(ClassificationNote.EngineFailed)
            }
            structural?.let { add(ClassificationNote.StructuralSummary(it.summary)) }
            add(ClassificationNote.Verdict(passwordResistance))
        }

    private fun networkConfigPresentation(assessment: SecurityAssessment?): NetworkConfigPresentation? {
        if (assessment == null) return null
        return NetworkConfigPresentation(rating = assessment.rating)
    }

    private fun formatDurationRaw(duration: Duration): String = duration.inWholeMilliseconds.toString()

    private fun weakerOf(
        a: PasswordResistanceRating,
        b: PasswordResistanceRating,
    ): PasswordResistanceRating = if (rank(a) <= rank(b)) a else b

    private fun strongerOf(
        a: PasswordResistanceRating,
        b: PasswordResistanceRating,
    ): PasswordResistanceRating = if (rank(a) >= rank(b)) a else b

    private fun rank(r: PasswordResistanceRating): Int =
        when (r) {
            PasswordResistanceRating.VERY_LOW -> 0
            PasswordResistanceRating.LOW -> 1
            PasswordResistanceRating.MODERATE -> 2
            PasswordResistanceRating.HIGH -> 3
            PasswordResistanceRating.VERY_HIGH -> 4
            PasswordResistanceRating.UNKNOWN -> -1
        }
}
