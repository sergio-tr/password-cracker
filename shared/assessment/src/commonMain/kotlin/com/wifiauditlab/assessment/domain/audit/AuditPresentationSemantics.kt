package com.wifiauditlab.assessment.domain.audit

import com.wifiauditlab.assessment.domain.security.SecurityRating
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.core.audit.PasswordAuditInapplicableReason
import com.wifiauditlab.core.math.CombinationCount
import kotlin.time.Duration

fun SecurityFamily.toPasswordAuditInapplicableReason(): PasswordAuditInapplicableReason? =
    when (this) {
        SecurityFamily.OPEN -> PasswordAuditInapplicableReason.OpenNetwork
        SecurityFamily.OWE -> PasswordAuditInapplicableReason.Owe
        SecurityFamily.WPA2_ENTERPRISE,
        SecurityFamily.WPA3_ENTERPRISE,
        -> PasswordAuditInapplicableReason.Enterprise
        SecurityFamily.PASSPOINT -> PasswordAuditInapplicableReason.Passpoint
        SecurityFamily.DPP -> PasswordAuditInapplicableReason.Dpp
        SecurityFamily.WEP -> PasswordAuditInapplicableReason.Wep
        SecurityFamily.UNKNOWN -> PasswordAuditInapplicableReason.UnknownFamily
        SecurityFamily.WPA_PERSONAL,
        SecurityFamily.WPA2_PERSONAL,
        SecurityFamily.WPA3_PERSONAL,
        SecurityFamily.WPA2_WPA3_PERSONAL,
        -> null
    }

/** Terminal report headline — resolved to localized copy in the UI layer. */
enum class AuditOutcomeHeadline {
    PasswordFound,
    HighResistanceWithinModel,
    NotFoundWithinLimit,
    NotFoundWithinModel,
    AuditStopped,
    AuditFailed,
}

enum class PerformanceMetricKind {
    CombinationsTried,
    MeasuredTime,
    Speed,
    Attempts,
    Time,
    AttemptsPerformed,
    BudgetCapacity,
    ModelledStructure,
}

enum class AuditRecommendationId {
    LongerPassword,
    AvoidSimpleStructure,
    MixCharsets,
    UpgradeWifi,
    PreferWpa3,
    AvoidTransition,
}

sealed interface ClassificationNote {
    data object ResistanceMethodology : ClassificationNote

    data object NoAbsoluteClaim : ClassificationNote

    data object FoundWithinBudget : ClassificationNote

    data object SurvivedBudget : ClassificationNote

    data object ExhaustedPlanSpace : ClassificationNote

    data object CancelledIncomplete : ClassificationNote

    data object EngineFailed : ClassificationNote

    data class StructuralSummary(
        val summary: String,
    ) : ClassificationNote

    data class Verdict(
        val rating: PasswordResistanceRating,
    ) : ClassificationNote
}

data class NetworkConfigPresentation(
    val rating: SecurityRating?,
)

data class LimitReachedBudgetSummary(
    val measuredAttempts: CombinationCount?,
    val measuredDuration: Duration?,
    val budgetedAttemptCapacity: CombinationCount?,
)
