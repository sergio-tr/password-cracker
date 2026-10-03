package com.wifiauditlab.android.ui.lab

import androidx.annotation.StringRes
import com.wifiauditlab.android.R
import com.wifiauditlab.assessment.domain.wifi.ManagementFrameProtection
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile

/** Lab-only presets that map to normalized [WifiSecurityProfile] values for assessment. */
enum class PrototypeSecurityPreset(
    @StringRes val labelRes: Int,
    private val family: SecurityFamily,
    private val keyManagements: Set<String>,
    private val defaultPmf: ManagementFrameProtection,
    private val transition: Boolean = false,
) {
    OPEN(R.string.lab_preset_open, SecurityFamily.OPEN, emptySet(), ManagementFrameProtection.UNKNOWN),
    WEP_LEGACY(R.string.lab_preset_wep, SecurityFamily.WEP, emptySet(), ManagementFrameProtection.UNKNOWN),
    WPA2_PERSONAL(
        R.string.lab_preset_wpa2,
        SecurityFamily.WPA2_PERSONAL,
        setOf("WPA-PSK"),
        ManagementFrameProtection.UNKNOWN,
    ),
    WPA3_PERSONAL(
        R.string.lab_preset_wpa3,
        SecurityFamily.WPA3_PERSONAL,
        setOf("SAE"),
        ManagementFrameProtection.REQUIRED,
    ),
    WPA2_WPA3_TRANSITION(
        R.string.lab_preset_transition,
        SecurityFamily.WPA2_WPA3_PERSONAL,
        setOf("WPA-PSK", "SAE"),
        ManagementFrameProtection.REQUIRED,
        transition = true,
    ),
    ENTERPRISE_WPA2(
        R.string.lab_preset_enterprise_wpa2,
        SecurityFamily.WPA2_ENTERPRISE,
        setOf("802.1X"),
        ManagementFrameProtection.CAPABLE,
    ),
    ENTERPRISE_WPA3(
        R.string.lab_preset_enterprise_wpa3,
        SecurityFamily.WPA3_ENTERPRISE,
        setOf("802.1X"),
        ManagementFrameProtection.REQUIRED,
    ),
    ;

    fun toProfile(pmf: ManagementFrameProtection? = null): WifiSecurityProfile =
        when (this) {
            OPEN -> WifiSecurityProfile.open(rawCapabilities = "[ESS]")
            else ->
                WifiSecurityProfile(
                    family = family,
                    keyManagements = keyManagements,
                    managementFrameProtection = pmf ?: defaultPmf,
                    isTransitionMode = transition,
                    rawCapabilities = rawCapabilitiesLabel(),
                )
        }

    private fun rawCapabilitiesLabel(): String? =
        when (this) {
            WEP_LEGACY -> "[WEP][ESS]"
            WPA2_PERSONAL -> "[WPA2-PSK-CCMP][ESS]"
            WPA3_PERSONAL -> "[WPA3-SAE-CCMP][ESS]"
            WPA2_WPA3_TRANSITION -> "[WPA2-PSK-CCMP][SAE-CCMP][ESS]"
            ENTERPRISE_WPA2 -> "[WPA2-EAP-CCMP][ESS]"
            ENTERPRISE_WPA3 -> "[WPA3-EAP-CCMP][ESS]"
            OPEN -> null
        }

    companion object {
        val PRIMARY: List<PrototypeSecurityPreset> =
            listOf(
                OPEN,
                WEP_LEGACY,
                WPA2_PERSONAL,
                WPA3_PERSONAL,
                WPA2_WPA3_TRANSITION,
                ENTERPRISE_WPA2,
            )

        val ENTERPRISE_ALTERNATIVES: List<PrototypeSecurityPreset> =
            listOf(
                ENTERPRISE_WPA2,
                ENTERPRISE_WPA3,
            )

        fun matching(profile: WifiSecurityProfile): PrototypeSecurityPreset? =
            entries.firstOrNull { preset ->
                preset.family == profile.family &&
                    preset.keyManagements == profile.keyManagements &&
                    preset.transition == profile.isTransitionMode
            }

        /** Best-effort preset from a persisted Vault [SecurityFamily] (no full profile). */
        fun forFamily(family: SecurityFamily): PrototypeSecurityPreset? =
            when (family) {
                SecurityFamily.OPEN -> OPEN
                SecurityFamily.WEP -> WEP_LEGACY
                SecurityFamily.WPA_PERSONAL,
                SecurityFamily.WPA2_PERSONAL,
                -> WPA2_PERSONAL
                SecurityFamily.WPA3_PERSONAL -> WPA3_PERSONAL
                SecurityFamily.WPA2_WPA3_PERSONAL -> WPA2_WPA3_TRANSITION
                SecurityFamily.WPA2_ENTERPRISE -> ENTERPRISE_WPA2
                SecurityFamily.WPA3_ENTERPRISE -> ENTERPRISE_WPA3
                else -> null
            }
    }
}

fun LocalNetworkPrototype.withPreset(
    preset: PrototypeSecurityPreset,
    preservePmf: Boolean = false,
): LocalNetworkPrototype {
    val pmf =
        if (preservePmf) {
            securityProfile.managementFrameProtection
        } else {
            null
        }
    return copy(securityProfile = preset.toProfile(pmf))
}

fun LocalNetworkPrototype.withPmf(pmf: ManagementFrameProtection): LocalNetworkPrototype =
    copy(securityProfile = securityProfile.copy(managementFrameProtection = pmf))
