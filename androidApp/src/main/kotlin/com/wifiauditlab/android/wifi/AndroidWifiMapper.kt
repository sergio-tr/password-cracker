package com.wifiauditlab.android.wifi

import android.net.wifi.ScanResult
import android.os.Build
import com.wifiauditlab.assessment.domain.classifier.WifiSecurityClassifier
import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.Ssid
import com.wifiauditlab.assessment.domain.wifi.WifiBand
import com.wifiauditlab.assessment.domain.wifi.WifiChannel
import com.wifiauditlab.assessment.domain.wifi.WifiObservation
import com.wifiauditlab.assessment.domain.wifi.WifiSignal
import com.wifiauditlab.assessment.domain.wifi.WifiStandard

/**
 * Translates Android [ScanResult]s into domain [WifiObservation]s. All security
 * interpretation is delegated to the platform-independent [WifiSecurityClassifier],
 * keeping business logic out of the adapter.
 */
class AndroidWifiMapper(
    private val classifier: WifiSecurityClassifier = WifiSecurityClassifier(),
) {
    @Suppress("DEPRECATION") // ScanResult.SSID is deprecated on API 33 but still the portable field
    fun toObservation(result: ScanResult, nowEpochMillis: Long): WifiObservation {
        val band = bandOf(result.frequency)
        return WifiObservation(
            ssid = Ssid(result.SSID ?: ""),
            bssid = Bssid.of(result.BSSID ?: ""),
            signal = WifiSignal(result.level),
            channel = WifiChannel(channelOf(result.frequency, band), band, result.frequency),
            standard = standardOf(result, band),
            securityProfile = classifier.classify(result.capabilities ?: ""),
            observedAtEpochMillis = nowEpochMillis,
        )
    }

    private fun bandOf(frequencyMhz: Int): WifiBand = when (frequencyMhz) {
        in 2400..2500 -> WifiBand.GHZ_2_4
        in 4900..5895 -> WifiBand.GHZ_5
        in 5925..7125 -> WifiBand.GHZ_6
        else -> WifiBand.UNKNOWN
    }

    private fun channelOf(frequencyMhz: Int, band: WifiBand): Int = when (band) {
        WifiBand.GHZ_2_4 -> (frequencyMhz - 2407) / 5
        WifiBand.GHZ_5 -> (frequencyMhz - 5000) / 5
        WifiBand.GHZ_6 -> (frequencyMhz - 5950) / 5
        WifiBand.UNKNOWN -> 0
    }

    private fun standardOf(result: ScanResult, band: WifiBand): WifiStandard {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return WifiStandard.UNKNOWN
        return when (result.wifiStandard) {
            ScanResult.WIFI_STANDARD_LEGACY -> WifiStandard.LEGACY
            ScanResult.WIFI_STANDARD_11N -> WifiStandard.WIFI_4
            ScanResult.WIFI_STANDARD_11AC -> WifiStandard.WIFI_5
            ScanResult.WIFI_STANDARD_11AX -> if (band == WifiBand.GHZ_6) WifiStandard.WIFI_6E else WifiStandard.WIFI_6
            else -> mapNewerStandards(result.wifiStandard)
        }
    }

    private fun mapNewerStandards(standard: Int): WifiStandard =
        if (standard == WIFI_STANDARD_11BE) WifiStandard.WIFI_7 else WifiStandard.UNKNOWN

    private companion object {
        const val WIFI_STANDARD_11BE = 8 // ScanResult.WIFI_STANDARD_11BE, available from API 33
    }
}
