package com.wifiauditlab.android.ui.lab

import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.WifiBand
import com.wifiauditlab.assessment.domain.wifi.WifiStandard

/** How the Lab challenge secret is sourced — always verified locally, never against an AP. */
enum class LabSecretMode {
    /** Random hidden secret drawn from the configured alphabet and length. */
    RandomHidden,

    /** Synthetic local network profile with a user-supplied target password (in-memory only). */
    LocalPrototype,
}

/**
 * Editable local network prototype for Lab experiments.
 * Context only — the search engine never authenticates against a real access point.
 */
data class LocalNetworkPrototype(
    val ssidLabel: String = "",
    val securityFamily: SecurityFamily = SecurityFamily.WPA2_PERSONAL,
    val wifiStandard: WifiStandard? = null,
    val band: WifiBand? = null,
)

/** Primary PSK choices shown first in guided prototype flow. */
val LAB_PRIMARY_PSK_FAMILIES: List<SecurityFamily> =
    listOf(
        SecurityFamily.WPA2_PERSONAL,
        SecurityFamily.WPA3_PERSONAL,
    )

/** Legacy / transition PSK choices — secondary in guided mode. */
val LAB_SECONDARY_PSK_FAMILIES: List<SecurityFamily> =
    listOf(
        SecurityFamily.WPA_PERSONAL,
        SecurityFamily.WPA2_WPA3_PERSONAL,
    )

/** PSK personal families selectable as the prototype auth / security type. */
val LAB_PERSONAL_PSK_FAMILIES: List<SecurityFamily> =
    LAB_PRIMARY_PSK_FAMILIES + LAB_SECONDARY_PSK_FAMILIES
