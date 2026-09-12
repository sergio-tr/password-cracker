package com.wifiauditlab.assessment.domain.match

import com.wifiauditlab.assessment.domain.vault.LocationLabel
import com.wifiauditlab.assessment.domain.vault.SavedNetworkId
import com.wifiauditlab.assessment.domain.vault.SavedWifiNetwork
import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.Ssid
import com.wifiauditlab.assessment.domain.wifi.WifiBand
import com.wifiauditlab.assessment.domain.wifi.WifiChannel
import com.wifiauditlab.assessment.domain.wifi.WifiObservation
import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile
import com.wifiauditlab.assessment.domain.wifi.WifiSignal
import com.wifiauditlab.assessment.domain.wifi.WifiStandard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DefaultKnownNetworkMatcherTest {

    private val matcher = DefaultKnownNetworkMatcher()

    private fun observation(
        ssid: String,
        bssid: String,
        family: SecurityFamily = SecurityFamily.WPA2_PERSONAL,
    ) = WifiObservation(
        ssid = Ssid(ssid),
        bssid = Bssid.of(bssid),
        signal = WifiSignal(-50),
        channel = WifiChannel(36, WifiBand.GHZ_5, 5180),
        standard = WifiStandard.WIFI_6,
        securityProfile = WifiSecurityProfile(family, setOf("PSK"), managementFrameProtection = com.wifiauditlab.assessment.domain.wifi.ManagementFrameProtection.UNKNOWN, isTransitionMode = false, rawCapabilities = null),
        observedAtEpochMillis = 0,
    )

    private fun saved(
        id: String,
        ssid: String,
        family: SecurityFamily = SecurityFamily.WPA2_PERSONAL,
        bssids: Set<String> = emptySet(),
    ) = SavedWifiNetwork(
        id = SavedNetworkId(id),
        alias = "Alias $id",
        ssid = ssid,
        securityFamily = family,
        knownBssids = bssids.map { Bssid.of(it) }.toSet(),
        locationLabel = LocationLabel("Casa"),
        geoLocation = null,
        secretId = null,
        notes = null,
        createdAtEpochMillis = 0,
        lastSeenAtEpochMillis = null,
    )

    @Test
    fun unknown_when_no_ssid_matches() {
        val result = matcher.match(observation("Cafe", "aa:bb:cc:dd:ee:ff"), listOf(saved("1", "Casa")))
        assertEquals(NetworkMatchResult.Unknown, result)
    }

    @Test
    fun exact_when_bssid_is_known() {
        val net = saved("1", "Casa", bssids = setOf("AA:BB:CC:DD:EE:01"))
        val result = matcher.match(observation("Casa", "aa:bb:cc:dd:ee:01"), listOf(net))
        val exact = assertIs<NetworkMatchResult.Exact>(result)
        assertEquals(net.id, exact.network.id)
    }

    @Test
    fun probable_when_ssid_and_family_match_but_bssid_is_new() {
        val net = saved("1", "Casa", bssids = setOf("AA:BB:CC:DD:EE:01"))
        val result = matcher.match(observation("Casa", "11:22:33:44:55:66"), listOf(net))
        val probable = assertIs<NetworkMatchResult.Probable>(result)
        assertEquals(MatchConfidence.SSID_AND_FAMILY, probable.confidence)
    }

    @Test
    fun ambiguous_when_multiple_same_ssid_and_family() {
        val a = saved("1", "Casa")
        val b = saved("2", "Casa")
        val result = matcher.match(observation("Casa", "11:22:33:44:55:66"), listOf(a, b))
        val ambiguous = assertIs<NetworkMatchResult.Ambiguous>(result)
        assertEquals(2, ambiguous.candidates.size)
    }

    @Test
    fun probable_ssid_only_when_family_differs() {
        val net = saved("1", "Casa", family = SecurityFamily.WPA3_PERSONAL)
        val result = matcher.match(
            observation("Casa", "11:22:33:44:55:66", family = SecurityFamily.WPA2_PERSONAL),
            listOf(net),
        )
        val probable = assertIs<NetworkMatchResult.Probable>(result)
        assertEquals(MatchConfidence.SSID_ONLY, probable.confidence)
    }
}
