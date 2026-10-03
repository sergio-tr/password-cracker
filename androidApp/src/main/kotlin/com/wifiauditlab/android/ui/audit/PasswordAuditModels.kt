package com.wifiauditlab.android.ui.audit

import com.wifiauditlab.android.ui.lab.PrototypeSecurityPreset
import com.wifiauditlab.android.ui.nearby.NearbyItem
import com.wifiauditlab.assessment.domain.audit.PasswordAuditNetworkContext
import com.wifiauditlab.assessment.domain.audit.PasswordAuditResultReport
import com.wifiauditlab.assessment.domain.audit.PasswordStrengthAssessment
import com.wifiauditlab.assessment.domain.audit.toPasswordAuditInapplicableReason
import com.wifiauditlab.assessment.domain.security.SecurityAssessment
import com.wifiauditlab.assessment.domain.vault.SavedNetworkId
import com.wifiauditlab.assessment.domain.vault.SavedWifiNetwork
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.Ssid
import com.wifiauditlab.assessment.domain.wifi.WifiBand
import com.wifiauditlab.assessment.domain.wifi.WifiObservation
import com.wifiauditlab.assessment.domain.wifi.WifiStandard
import com.wifiauditlab.core.audit.PasswordAuditInapplicableReason
import com.wifiauditlab.lab.domain.SearchMetrics
import com.wifiauditlab.lab.domain.SearchOutcome
import com.wifiauditlab.lab.domain.SearchState
import com.wifiauditlab.lab.domain.audit.AutomaticPlanExplanation
import com.wifiauditlab.lab.domain.audit.PasswordAuditBudgetPreset
import com.wifiauditlab.lab.domain.audit.PasswordAuditPlan
import com.wifiauditlab.lab.domain.engine.FeasibilityRating

/** Where the known password comes from. Vault plaintext is not held in UI state. */
enum class PasswordAuditSecretSource {
    Vault,
    Manual,
}

/** Novice vs advanced configuration surface. */
enum class PasswordAuditInteractionMode {
    Automatic,
    Advanced,
}

/** Coarse screen phase for Quick Audit (execution lives alongside Ready). */
enum class PasswordAuditScreenPhase {
    Loading,
    Ready,
    Invalid,
    Starting,
}

/** Payload from Nearby detail → Quick Audit (no password). */
data class PasswordAuditRequest(
    val network: PasswordAuditNetworkContext,
    val savedNetworkId: SavedNetworkId?,
    /** Used to re-check connection eligibility before start. */
    val observation: WifiObservation? = null,
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
    val securityFamily: SecurityFamily = SecurityFamily.UNKNOWN,
    val wifiStandard: WifiStandard? = null,
    val band: WifiBand? = null,
    val isConnected: Boolean = true,
    val savedNetworkId: SavedNetworkId? = null,
    val vaultSecretAvailable: Boolean = false,
    val secretSource: PasswordAuditSecretSource = PasswordAuditSecretSource.Manual,
    val passwordInput: String = "",
    val passwordVisible: Boolean = false,
    val saveToVault: Boolean = false,
    val passwordError: PasswordAuditUiError? = null,
    val mode: PasswordAuditInteractionMode = PasswordAuditInteractionMode.Automatic,
    val preset: PasswordAuditBudgetPreset = PasswordAuditBudgetPreset.Standard,
    val advancedExpanded: Boolean = false,
    val planDetailsExpanded: Boolean = false,
    val searchStagesExpanded: Boolean = false,
    val customDurationSeconds: String = "60",
    val customMaxAttempts: String = "",
    val plan: PasswordAuditPlan? = null,
    val planNotApplicableReason: PasswordAuditInapplicableReason? = null,
    val explanation: AutomaticPlanExplanation? = null,
    val feasibilityRating: FeasibilityRating? = null,
    val feasibilityReason: String? = null,
    val strength: PasswordStrengthAssessment? = null,
    val startBlockedReason: PasswordAuditUiError? = null,
    val infoMessage: PasswordAuditUiError? = null,
    val connectionLostMessage: PasswordAuditUiError? = null,
    val searchState: SearchState = SearchState.Idle,
    val metrics: SearchMetrics? = null,
    val outcome: SearchOutcome? = null,
    val discoveredWithinBudget: Boolean = false,
    val errorMessage: PasswordAuditUiError? = null,
    val errorDetails: String? = null,
    val showErrorDetails: Boolean = false,
    val networkAssessment: SecurityAssessment? = null,
    val improveGuideExpanded: Boolean = false,
    val resultDetailsExpanded: Boolean = false,
    val resultReport: PasswordAuditResultReport? = null,
) {
    val isActive: Boolean
        get() =
            searchState == SearchState.Preparing ||
                searchState == SearchState.Running ||
                searchState == SearchState.Cancelling

    val hasSecretReady: Boolean
        get() =
            when (secretSource) {
                PasswordAuditSecretSource.Vault -> vaultSecretAvailable
                PasswordAuditSecretSource.Manual -> passwordInput.isNotEmpty()
            }

    val phase: PasswordAuditScreenPhase
        get() =
            when {
                missingTarget -> PasswordAuditScreenPhase.Invalid
                loadingPlan && plan == null && planNotApplicableReason == null ->
                    PasswordAuditScreenPhase.Loading
                searchState == SearchState.Preparing -> PasswordAuditScreenPhase.Starting
                planNotApplicableReason != null && plan == null -> PasswordAuditScreenPhase.Invalid
                else -> PasswordAuditScreenPhase.Ready
            }
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
        observation = observation,
    )
}

/**
 * Quick Audit entry from Vault. Uses family → preset profile; no live [WifiObservation]
 * (connection eligibility is skipped until a Nearby observation is available).
 */
fun passwordAuditRequestFromVault(network: SavedWifiNetwork): PasswordAuditRequest {
    val preset =
        PrototypeSecurityPreset.forFamily(network.securityFamily)
            ?: PrototypeSecurityPreset.WPA2_PERSONAL
    return PasswordAuditRequest(
        network =
            PasswordAuditNetworkContext(
                displayName = network.alias,
                ssid = Ssid(network.ssid),
                bssid = network.knownBssids.firstOrNull(),
                securityProfile = preset.toProfile(),
                wifiStandard = null,
                band = null,
            ),
        savedNetworkId = network.id,
        observation = null,
    )
}

fun PasswordAuditInapplicableReason.toUiError(): PasswordAuditUiError.PlanNotApplicable =
    PasswordAuditUiError.PlanNotApplicable(this)

fun SecurityFamily.planNotApplicableReasonOrNull(): PasswordAuditInapplicableReason? =
    toPasswordAuditInapplicableReason()
