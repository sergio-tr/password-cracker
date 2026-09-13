package com.wifiauditlab.android.ui.audit

import com.wifiauditlab.android.ui.lab.bandDisplayLabel
import com.wifiauditlab.android.ui.lab.standardLabel
import com.wifiauditlab.android.ui.nearby.NearbyItem
import com.wifiauditlab.android.ui.security.familyLabel
import com.wifiauditlab.assessment.domain.audit.PasswordAuditNetworkContext
import com.wifiauditlab.assessment.domain.audit.PasswordStrengthAssessment
import com.wifiauditlab.assessment.domain.vault.SavedNetworkId
import com.wifiauditlab.assessment.domain.wifi.WifiBand
import com.wifiauditlab.assessment.domain.wifi.WifiStandard
import com.wifiauditlab.lab.domain.SearchMetrics
import com.wifiauditlab.lab.domain.SearchOutcome
import com.wifiauditlab.lab.domain.SearchState
import com.wifiauditlab.lab.domain.audit.AutomaticPlanExplanation
import com.wifiauditlab.lab.domain.audit.PasswordAuditBudgetPreset
import com.wifiauditlab.lab.domain.audit.PasswordAuditPlan
import com.wifiauditlab.lab.domain.engine.FeasibilityRating

/** Payload from Nearby detail → Quick Audit (no password). */
data class PasswordAuditRequest(
    val network: PasswordAuditNetworkContext,
    val savedNetworkId: SavedNetworkId?,
)

class PasswordAuditTargetStore {
    @Volatile
    var current: PasswordAuditRequest? = null
        private set

    fun set(request: PasswordAuditRequest) {
        current = request
    }

    fun clear() {
        current = null
    }
}

data class PasswordAuditUiState(
    val missingTarget: Boolean = false,
    val loadingPlan: Boolean = true,
    val displayName: String = "",
    val ssidLabel: String = "",
    val familyLabel: String = "",
    val metaLine: String = "",
    val savedNetworkId: SavedNetworkId? = null,
    val vaultSecretAvailable: Boolean = false,
    val passwordInput: String = "",
    val passwordVisible: Boolean = false,
    val passwordFromVault: Boolean = false,
    val passwordError: String? = null,
    val preset: PasswordAuditBudgetPreset = PasswordAuditBudgetPreset.Standard,
    val advancedExpanded: Boolean = false,
    val customDurationSeconds: String = "300",
    val customMaxAttempts: String = "",
    val plan: PasswordAuditPlan? = null,
    val planNotApplicableReason: String? = null,
    val explanation: AutomaticPlanExplanation? = null,
    val feasibilityRating: FeasibilityRating? = null,
    val feasibilityReason: String? = null,
    val strength: PasswordStrengthAssessment? = null,
    val startBlockedReason: String? = null,
    val infoMessage: String? = null,
    val searchState: SearchState = SearchState.Idle,
    val metrics: SearchMetrics? = null,
    val outcome: SearchOutcome? = null,
    val discoveredWithinBudget: Boolean = false,
    val errorMessage: String? = null,
) {
    val isActive: Boolean
        get() =
            searchState == SearchState.Preparing ||
                searchState == SearchState.Running ||
                searchState == SearchState.Cancelling
}

fun passwordAuditRequestFromNearby(item: NearbyItem): PasswordAuditRequest {
    val observation = item.observation
    return PasswordAuditRequest(
        network =
            PasswordAuditNetworkContext(
                displayName = item.alias ?: observation.ssid.toString(),
                ssid = observation.ssid,
                bssid = observation.bssid,
                securityProfile = observation.securityProfile,
                wifiStandard = observation.standard.takeUnless { it == WifiStandard.UNKNOWN },
                band = observation.channel.band.takeUnless { it == WifiBand.UNKNOWN },
            ),
        savedNetworkId = item.savedNetworkId,
    )
}

fun PasswordAuditNetworkContext.familyDisplayLabel(): String = familyLabel(securityProfile.family)

fun PasswordAuditNetworkContext.metaLine(): String =
    listOfNotNull(
        wifiStandard?.let { standardLabel(it) },
        band?.let { bandDisplayLabel(it) },
    ).joinToString(" · ").ifEmpty { "—" }

fun PasswordAuditBudgetPreset.chipLabel(): String =
    when (this) {
        PasswordAuditBudgetPreset.Quick -> "Rápida"
        PasswordAuditBudgetPreset.Standard -> "Estándar"
        PasswordAuditBudgetPreset.Deep -> "Profunda"
        PasswordAuditBudgetPreset.Custom -> "Personalizada"
    }
