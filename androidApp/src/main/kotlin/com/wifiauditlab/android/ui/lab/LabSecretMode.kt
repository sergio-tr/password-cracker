package com.wifiauditlab.android.ui.lab

import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.WifiBand
import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile
import com.wifiauditlab.assessment.domain.wifi.WifiStandard

/** How the Lab challenge secret is sourced — always verified locally, never against an AP. */
enum class LabSecretMode {
    /** Random hidden secret drawn from the configured alphabet and length. */
    RandomHidden,

    /** Synthetic local network profile for security assessment (password audit = FIX-03B). */
    LocalPrototype,
}

/**
 * Editable local network prototype for Lab experiments.
 * Context only — assessment uses [securityProfile]; search against a custom password is FIX-03B.
 */
data class LocalNetworkPrototype(
    val id: String? = null,
    val displayName: String = "",
    val ssid: String = "",
    val securityProfile: WifiSecurityProfile = PrototypeSecurityPreset.WPA2_PERSONAL.toProfile(),
    val band: WifiBand? = null,
    val standard: WifiStandard? = null,
) {
    val securityFamily: SecurityFamily get() = securityProfile.family
}
