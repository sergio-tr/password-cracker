package com.wifiauditlab.android.ui.lab

import com.wifiauditlab.android.ui.nearby.NearbyItem
import com.wifiauditlab.android.ui.security.familyLabel
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

fun LabNetworkContext.familyDisplayLabel(): String = familyLabel(securityFamily)

fun LabNetworkContext.metaLine(): String =
    listOfNotNull(
        wifiStandard?.let { standardLabel(it) },
        band?.let { bandDisplayLabel(it) },
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

fun bandDisplayLabel(band: WifiBand): String =
    when (band) {
        WifiBand.GHZ_2_4 -> "2,4 GHz"
        WifiBand.GHZ_5 -> "5 GHz"
        WifiBand.GHZ_6 -> "6 GHz"
        WifiBand.UNKNOWN -> "Banda desconocida"
    }
