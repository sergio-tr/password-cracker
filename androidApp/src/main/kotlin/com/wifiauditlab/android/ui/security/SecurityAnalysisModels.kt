package com.wifiauditlab.android.ui.security

import androidx.annotation.StringRes
import com.wifiauditlab.android.R
import com.wifiauditlab.assessment.domain.security.SecurityAssessment
import com.wifiauditlab.assessment.domain.security.SecurityRating
import com.wifiauditlab.assessment.domain.wifi.ManagementFrameProtection
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile

/** Payload handed from Network Detail to the dedicated analysis screen. */
data class SecurityAnalysisRequest(
    val displayName: String,
    val ssidLabel: String,
    val profile: WifiSecurityProfile,
)

/** Process-scoped holder so navigation does not serialize secrets or large graphs. */
class SecurityAnalysisTargetStore {
    @Volatile
    var current: SecurityAnalysisRequest? = null
        private set

    fun set(request: SecurityAnalysisRequest) {
        current = request
    }

    fun clear() {
        current = null
    }
}

data class AuthenticationSummary(
    @StringRes val familyLabelRes: Int,
    @StringRes val modeLabelRes: Int,
    @StringRes val encryptionLabelRes: Int? = null,
    val encryptionDynamic: String? = null,
    @StringRes val pmfLabelRes: Int? = null,
    @StringRes val transitionLabelRes: Int? = null,
)

data class SecurityAnalysisUiState(
    val displayName: String = "",
    val ssidLabel: String = "",
    val loading: Boolean = true,
    val missingTarget: Boolean = false,
    val assessment: SecurityAssessment? = null,
    val authentication: AuthenticationSummary? = null,
    val recommendations: List<Int> = emptyList(),
    val technicalExpanded: Boolean = false,
)

@StringRes
fun ratingLabelRes(rating: SecurityRating): Int =
    when (rating) {
        SecurityRating.HIGH -> R.string.security_rating_high
        SecurityRating.MODERATE -> R.string.security_rating_moderate
        SecurityRating.LOW -> R.string.security_rating_low
        SecurityRating.INSECURE -> R.string.security_rating_insecure
    }

@StringRes
fun familyLabelRes(family: SecurityFamily): Int =
    when (family) {
        SecurityFamily.OPEN -> R.string.security_family_open
        SecurityFamily.WEP -> R.string.security_family_wep
        SecurityFamily.WPA_PERSONAL -> R.string.security_family_wpa
        SecurityFamily.WPA2_PERSONAL -> R.string.security_family_wpa2
        SecurityFamily.WPA3_PERSONAL -> R.string.security_family_wpa3
        SecurityFamily.WPA2_WPA3_PERSONAL -> R.string.security_family_wpa2_wpa3
        SecurityFamily.WPA2_ENTERPRISE -> R.string.security_family_wpa2_enterprise
        SecurityFamily.WPA3_ENTERPRISE -> R.string.security_family_wpa3_enterprise
        SecurityFamily.OWE -> R.string.security_family_owe
        SecurityFamily.PASSPOINT -> R.string.security_family_passpoint
        SecurityFamily.DPP -> R.string.security_family_dpp
        SecurityFamily.UNKNOWN -> R.string.security_family_unknown
    }

fun buildAuthenticationSummary(profile: WifiSecurityProfile): AuthenticationSummary {
    val modeRes =
        when (profile.family) {
            SecurityFamily.WPA2_ENTERPRISE, SecurityFamily.WPA3_ENTERPRISE, SecurityFamily.PASSPOINT ->
                R.string.security_mode_enterprise
            SecurityFamily.OPEN, SecurityFamily.OWE -> R.string.security_mode_open
            SecurityFamily.UNKNOWN -> R.string.security_mode_unknown
            else -> R.string.security_mode_personal
        }
    val encryptionDynamic =
        when {
            profile.keyManagements.isNotEmpty() -> profile.keyManagements.sorted().joinToString(", ")
            profile.rawCapabilities != null -> profile.rawCapabilities
            else -> null
        }
    val encryptionRes =
        when {
            encryptionDynamic != null -> null
            profile.family == SecurityFamily.OPEN -> R.string.security_enc_open
            profile.family == SecurityFamily.WEP -> R.string.security_enc_wep
            else -> R.string.security_enc_unknown
        }
    val pmfRes =
        when (profile.managementFrameProtection) {
            ManagementFrameProtection.REQUIRED -> R.string.security_pmf_required
            ManagementFrameProtection.CAPABLE -> R.string.security_pmf_capable
            ManagementFrameProtection.DISABLED -> R.string.security_pmf_disabled
            ManagementFrameProtection.UNKNOWN -> null
        }
    val transitionRes =
        when {
            profile.isTransitionMode || profile.family == SecurityFamily.WPA2_WPA3_PERSONAL ->
                R.string.security_transition
            else -> null
        }
    return AuthenticationSummary(
        familyLabelRes = familyLabelRes(profile.family),
        modeLabelRes = modeRes,
        encryptionLabelRes = encryptionRes,
        encryptionDynamic = encryptionDynamic,
        pmfLabelRes = pmfRes,
        transitionLabelRes = transitionRes,
    )
}

/**
 * Defensive configuration tips only — never attack steps, password generation,
 * candidates, or lab linkage.
 */
fun defensiveRecommendationRes(
    profile: WifiSecurityProfile,
    assessment: SecurityAssessment,
): List<Int> {
    val tips = linkedSetOf<Int>()
    when (profile.family) {
        SecurityFamily.OPEN -> {
            tips += R.string.security_rec_open_no_passwords
            tips += R.string.security_rec_open_upgrade
        }
        SecurityFamily.WEP, SecurityFamily.WPA_PERSONAL -> {
            tips += R.string.security_rec_legacy_upgrade
            tips += R.string.security_rec_legacy_admin
        }
        SecurityFamily.WPA2_PERSONAL -> {
            tips += R.string.security_rec_wpa2_passphrase
            tips += R.string.security_rec_wpa2_wpa3
            if (profile.managementFrameProtection == ManagementFrameProtection.DISABLED) {
                tips += R.string.security_rec_wpa2_pmf
            }
        }
        SecurityFamily.WPA2_WPA3_PERSONAL -> {
            tips += R.string.security_rec_transition_wpa3
            tips += R.string.security_rec_transition_disable_wpa2
        }
        SecurityFamily.WPA3_PERSONAL -> {
            tips += R.string.security_rec_wpa3_firmware
            tips += R.string.security_rec_wpa3_passphrase
        }
        SecurityFamily.WPA2_ENTERPRISE, SecurityFamily.WPA3_ENTERPRISE, SecurityFamily.PASSPOINT -> {
            tips += R.string.security_rec_enterprise_trust
            tips += R.string.security_rec_enterprise_cert
        }
        SecurityFamily.OWE -> {
            tips += R.string.security_rec_owe_sensitive
        }
        SecurityFamily.DPP -> {
            tips += R.string.security_rec_dpp
        }
        SecurityFamily.UNKNOWN -> {
            tips += R.string.security_rec_unknown_check
        }
    }
    if (assessment.rating == SecurityRating.INSECURE || assessment.rating == SecurityRating.LOW) {
        tips += R.string.security_rec_low_priority
    }
    return tips.toList()
}
