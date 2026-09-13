package com.wifiauditlab.android.ui.audit

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.wifiauditlab.android.R
import com.wifiauditlab.android.ui.lab.bandDisplayLabelRes
import com.wifiauditlab.android.ui.lab.standardLabel
import com.wifiauditlab.android.ui.security.familyLabelRes
import com.wifiauditlab.assessment.domain.audit.AuditOutcomeHeadline
import com.wifiauditlab.assessment.domain.audit.AuditRecommendationId
import com.wifiauditlab.assessment.domain.audit.ClassificationNote
import com.wifiauditlab.assessment.domain.audit.NetworkConfigPresentation
import com.wifiauditlab.assessment.domain.audit.PasswordResistanceRating
import com.wifiauditlab.assessment.domain.audit.PerformanceMetric
import com.wifiauditlab.assessment.domain.audit.PerformanceMetricKind
import com.wifiauditlab.assessment.domain.security.SecurityRating
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.WifiBand
import com.wifiauditlab.assessment.domain.wifi.WifiStandard
import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.audit.AutomaticPlanExplanation
import com.wifiauditlab.lab.domain.audit.PasswordAuditPlan
import com.wifiauditlab.lab.domain.audit.PlanExplanationDetail
import com.wifiauditlab.lab.domain.audit.PlanExplanationHeadline
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Composable
fun networkMetaLine(
    wifiStandard: WifiStandard?,
    band: WifiBand?,
): String =
    listOfNotNull(
        wifiStandard?.let { standardLabel(it) },
        band?.let { stringResource(bandDisplayLabelRes(it)) },
    ).joinToString(" · ").ifEmpty { "—" }

@Composable
fun PlanExplanationHeadline.label(): String =
    when (this) {
        PlanExplanationHeadline.AutomaticConfiguration -> stringResource(R.string.audit_plan_auto_config)
    }

@Composable
fun PlanExplanationDetail.line(): String =
    when (this) {
        is PlanExplanationDetail.WorkerCount ->
            pluralStringResource(R.plurals.audit_plan_workers, count, count)
        is PlanExplanationDetail.StageCount ->
            pluralStringResource(R.plurals.audit_plan_stages, count, count)
        is PlanExplanationDetail.BudgetLimit -> formatBudgetLimit(maxDuration, maxAttempts)
        PlanExplanationDetail.DeviceAdapted -> stringResource(R.string.audit_plan_device_adapted)
    }

@Composable
fun AutomaticPlanExplanation.noviceLines(plan: PasswordAuditPlan): List<String> {
    val lines = mutableListOf<String>()
    // Worker/parallelism counts stay in expanded plan details (advanced), not the ready summary.
    details.filterIsInstance<PlanExplanationDetail.StageCount>().firstOrNull()?.let {
        lines += it.line()
    }
    details.filterIsInstance<PlanExplanationDetail.BudgetLimit>().firstOrNull()?.let {
        lines += stringResource(R.string.audit_plan_budget_prefix, it.line())
    }
    return lines
}

@Composable
private fun formatBudgetLimit(
    maxDuration: Duration?,
    maxAttempts: CombinationCount?,
): String {
    val parts = mutableListOf<String>()
    maxDuration?.let { parts += formatDurationNovice(it) }
    maxAttempts?.let {
        val count = it.toLongOrNull()?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt()
        parts +=
            if (count != null) {
                pluralStringResource(R.plurals.audit_budget_attempts, count, count)
            } else {
                stringResource(R.string.audit_budget_attempts_abbr, it.toAbbreviatedString())
            }
    }
    return parts.joinToString(" · ")
}

@Composable
private fun formatDurationNovice(duration: Duration): String {
    val seconds = duration.inWholeSeconds
    return when {
        seconds < 60 -> stringResource(R.string.audit_duration_seconds, seconds)
        seconds % 60L == 0L -> {
            val minutes = (seconds / 60).toInt()
            pluralStringResource(R.plurals.audit_duration_minutes, minutes, minutes)
        }
        else -> duration.toString()
    }
}

@Composable
fun AuditOutcomeHeadline.label(): String =
    when (this) {
        AuditOutcomeHeadline.PasswordFound -> stringResource(R.string.audit_outcome_found)
        AuditOutcomeHeadline.HighResistanceWithinModel ->
            stringResource(R.string.audit_outcome_high_resistance)
        AuditOutcomeHeadline.NotFoundWithinLimit -> stringResource(R.string.audit_outcome_not_found_limit)
        AuditOutcomeHeadline.NotFoundWithinModel -> stringResource(R.string.audit_outcome_not_found_model)
        AuditOutcomeHeadline.AuditStopped -> stringResource(R.string.audit_outcome_stopped)
        AuditOutcomeHeadline.AuditFailed -> stringResource(R.string.audit_outcome_failed)
    }

@Composable
fun PasswordResistanceRating.label(): String =
    when (this) {
        PasswordResistanceRating.VERY_LOW -> stringResource(R.string.audit_resistance_very_low)
        PasswordResistanceRating.LOW -> stringResource(R.string.audit_resistance_low)
        PasswordResistanceRating.MODERATE -> stringResource(R.string.audit_resistance_moderate)
        PasswordResistanceRating.HIGH -> stringResource(R.string.audit_resistance_high)
        PasswordResistanceRating.VERY_HIGH -> stringResource(R.string.audit_resistance_very_high)
        PasswordResistanceRating.UNKNOWN -> stringResource(R.string.audit_resistance_unknown)
    }

@Composable
fun PerformanceMetric.displayLabel(): String {
    val base =
        when (kind) {
            PerformanceMetricKind.CombinationsTried -> stringResource(R.string.audit_metric_combinations)
            PerformanceMetricKind.MeasuredTime -> stringResource(R.string.audit_metric_measured_time)
            PerformanceMetricKind.Speed -> stringResource(R.string.audit_metric_speed)
            PerformanceMetricKind.Attempts -> stringResource(R.string.audit_metric_attempts)
            PerformanceMetricKind.Time -> stringResource(R.string.audit_metric_time)
            PerformanceMetricKind.AttemptsPerformed -> stringResource(R.string.audit_metric_attempts_done)
            PerformanceMetricKind.BudgetCapacity -> stringResource(R.string.audit_metric_budget_capacity)
            PerformanceMetricKind.ModelledStructure -> stringResource(R.string.audit_metric_structure)
        }
    return "${evidenceLabel(quality)} · $base"
}

@Composable
fun PerformanceMetric.displayValue(): String =
    when (kind) {
        PerformanceMetricKind.MeasuredTime,
        PerformanceMetricKind.Time,
        -> formatDurationFromMillis(value.toLongOrNull())
        PerformanceMetricKind.Speed ->
            value.toLongOrNull()?.let {
                stringResource(R.string.audit_metric_speed_value, it)
            } ?: value
        PerformanceMetricKind.BudgetCapacity ->
            stringResource(R.string.audit_metric_budget_value, value)
        PerformanceMetricKind.ModelledStructure -> {
            val parts = value.split(":")
            if (parts.size == 2) {
                stringResource(R.string.audit_metric_structure_value, parts[0], parts[1])
            } else {
                value
            }
        }
        else -> value
    }

@Composable
fun AuditRecommendationId.label(): String =
    when (this) {
        AuditRecommendationId.LongerPassword -> stringResource(R.string.audit_recommend_longer)
        AuditRecommendationId.AvoidSimpleStructure -> stringResource(R.string.audit_recommend_structure)
        AuditRecommendationId.MixCharsets -> stringResource(R.string.audit_recommend_charset)
        AuditRecommendationId.UpgradeWifi -> stringResource(R.string.audit_recommend_upgrade_wifi)
        AuditRecommendationId.PreferWpa3 -> stringResource(R.string.audit_recommend_prefer_wpa3)
        AuditRecommendationId.AvoidTransition -> stringResource(R.string.audit_recommend_avoid_transition)
    }

@Composable
fun ClassificationNote.line(): String =
    when (this) {
        ClassificationNote.ResistanceMethodology -> stringResource(R.string.audit_note_resistance_method)
        ClassificationNote.NoAbsoluteClaim -> stringResource(R.string.audit_note_no_absolute)
        ClassificationNote.FoundWithinBudget -> stringResource(R.string.audit_note_found_budget)
        ClassificationNote.SurvivedBudget -> stringResource(R.string.audit_note_survived_budget)
        ClassificationNote.ExhaustedPlanSpace -> stringResource(R.string.audit_note_exhausted)
        ClassificationNote.CancelledIncomplete -> stringResource(R.string.audit_note_cancelled)
        ClassificationNote.EngineFailed -> stringResource(R.string.audit_note_engine_failed)
        is ClassificationNote.StructuralSummary ->
            stringResource(R.string.audit_note_structure, summary)
        is ClassificationNote.Verdict ->
            stringResource(R.string.audit_note_verdict, rating.label())
    }

@Composable
fun NetworkConfigPresentation?.displayLabel(family: SecurityFamily): String {
    if (this == null) return stringResource(familyLabelRes(family))
    val ratingLabel =
        rating?.let {
            when (it) {
                SecurityRating.HIGH -> stringResource(R.string.audit_network_rating_high)
                SecurityRating.MODERATE -> stringResource(R.string.audit_network_rating_moderate)
                SecurityRating.LOW -> stringResource(R.string.audit_network_rating_low)
                SecurityRating.INSECURE -> stringResource(R.string.audit_network_rating_insecure)
            }
        }
    return listOfNotNull(ratingLabel, stringResource(familyLabelRes(family))).joinToString(" · ")
}

@Composable
private fun evidenceLabel(quality: com.wifiauditlab.assessment.domain.audit.EvidenceQuality): String =
    when (quality) {
        com.wifiauditlab.assessment.domain.audit.EvidenceQuality.Measured ->
            stringResource(R.string.audit_evidence_measured)
        com.wifiauditlab.assessment.domain.audit.EvidenceQuality.Estimated ->
            stringResource(R.string.audit_evidence_estimated)
        com.wifiauditlab.assessment.domain.audit.EvidenceQuality.Modelled ->
            stringResource(R.string.audit_evidence_modelled)
    }

@Composable
private fun formatDurationFromMillis(ms: Long?): String {
    if (ms == null) return "—"
    val duration = ms.milliseconds
    val totalSeconds = duration.inWholeSeconds
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) {
        stringResource(R.string.audit_duration_min_sec, minutes, seconds)
    } else {
        val tenths = (ms + 50) / 100
        val whole = tenths / 10
        val frac = tenths % 10
        stringResource(R.string.audit_duration_sec_frac, whole, frac)
    }
}
