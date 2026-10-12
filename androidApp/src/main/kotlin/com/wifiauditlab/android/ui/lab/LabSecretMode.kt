package com.wifiauditlab.android.ui.lab

import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.WifiBand
import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile
import com.wifiauditlab.assessment.domain.wifi.WifiStandard

/** How the Lab challenge secret is sourced — always verified locally, never against an AP. */
enum class LabSecretMode {
    /**
     * Didactic black-box: random secret encapsulated in [com.wifiauditlab.lab.domain.BlackBoxChallengeVerifier].
     * ViewModel / planner / Search Engine / UI never hold the plaintext.
     */
    RandomHidden,

    /**
     * Local Prototype: user-supplied password via [com.wifiauditlab.lab.domain.EncapsulatedPasswordVerifier]
     * (Synthetic / Guided / Advanced known-password demos only — not RealWifiAudit).
     */
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
