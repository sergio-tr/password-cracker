package com.wifiauditlab.android.ui.lab

import com.wifiauditlab.android.ui.nearby.NearbyItem
import com.wifiauditlab.android.ui.security.familyLabelRes
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.WifiBand
import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile
import com.wifiauditlab.assessment.domain.wifi.WifiStandard

/**
 * Presentation snapshot that contextualizes a Lab experiment from a real network.
 *
 * Lives in androidApp so `:shared:lab` never depends on `:shared:assessment`.
 * The engine still verifies a synthetic local secret — this never drives AP auth.
 */
data class LabNetworkContext(
    val displayName: String,
    val ssidLabel: String,
    val securityFamily: SecurityFamily,
    val securityProfile: WifiSecurityProfile,
    val wifiStandard: WifiStandard?,
    val band: WifiBand?,
    val assessmentSummary: String?,
)

/** Process-scoped holder — same pattern as [com.wifiauditlab.android.ui.security.SecurityAnalysisTargetStore]. */
class LabNetworkContextStore {
    @Volatile
    var current: LabNetworkContext? = null
        private set

    fun set(context: LabNetworkContext) {
        current = context
    }

    fun clear() {
        current = null
    }
}

fun LabNetworkContext.familyDisplayLabel(resolveString: (Int) -> String): String =
    resolveString(familyLabelRes(securityFamily))

fun LabNetworkContext.metaLine(resolveString: (Int) -> String): String =
    listOfNotNull(
        wifiStandard?.let { standardLabel(it) },
        band?.let { resolveString(bandDisplayLabelRes(it)) },
    ).joinToString(" · ").ifEmpty { "—" }

fun labNetworkContextFromNearby(
    item: NearbyItem,
    assessmentSummary: String? = null,
): LabNetworkContext {
    val observation = item.observation
    return LabNetworkContext(
        displayName = item.alias ?: observation.ssid.toString(),
        ssidLabel = observation.ssid.toString(),
        securityFamily = observation.securityProfile.family,
        securityProfile = observation.securityProfile,
        wifiStandard = observation.standard.takeUnless { it == WifiStandard.UNKNOWN },
        band = observation.channel.band.takeUnless { it == WifiBand.UNKNOWN },
        assessmentSummary = assessmentSummary,
    )
}

/** Builds a Lab [LocalNetworkPrototype] from Nearby context (identity + security only). */
fun localNetworkPrototypeFromContext(context: LabNetworkContext): LocalNetworkPrototype {
    val matched = PrototypeSecurityPreset.matching(context.securityProfile)
    return LocalNetworkPrototype(
        displayName = context.displayName,
        ssid = context.ssidLabel.trim().removeSurrounding("\""),
        securityProfile =
            matched?.toProfile(context.securityProfile.managementFrameProtection)
                ?: context.securityProfile,
        band = context.band,
        standard = context.wifiStandard,
    )
}

/** Stable key so Lab only re-seeds when the Nearby selection changes. */
fun LabNetworkContext.seedKey(): String =
    listOf(ssidLabel, securityFamily.name, displayName, securityProfile.rawCapabilities.orEmpty())
        .joinToString("|")

fun standardLabel(standard: WifiStandard): String =
    when (standard) {
        WifiStandard.LEGACY -> "Legacy"
        WifiStandard.WIFI_4 -> "Wi-Fi 4"
        WifiStandard.WIFI_5 -> "Wi-Fi 5"
        WifiStandard.WIFI_6 -> "Wi-Fi 6"
        WifiStandard.WIFI_6E -> "Wi-Fi 6E"
        WifiStandard.WIFI_7 -> "Wi-Fi 7"
        WifiStandard.UNKNOWN -> "Wi-Fi"
    }

@androidx.annotation.StringRes
fun bandDisplayLabelRes(band: WifiBand): Int =
    when (band) {
        WifiBand.GHZ_2_4 -> com.wifiauditlab.android.R.string.nearby_band_2_4
        WifiBand.GHZ_5 -> com.wifiauditlab.android.R.string.nearby_band_5
        WifiBand.GHZ_6 -> com.wifiauditlab.android.R.string.nearby_band_6
        WifiBand.UNKNOWN -> com.wifiauditlab.android.R.string.lab_band_unknown
    }
