package com.wifiauditlab.assessment.domain.connection

import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.Ssid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DefaultNetworkConnectionMatcherTest {
    private val matcher = DefaultNetworkConnectionMatcher()
    private val homeSsid = Ssid("Casa")
    private val homeBssid = Bssid.of("AA:BB:CC:DD:EE:01")
    private val otherBssid = Bssid.of("AA:BB:CC:DD:EE:99")

    @Test
    fun nullConnection_isNotConnected() {
        assertEquals(
            NetworkConnectionMatch.NotConnected,
            matcher.match(homeSsid, homeBssid, connection = null),
        )
    }

    @Test
    fun exactBssid_winsEvenIfSsidMissing() {
        val connection =
            CurrentWifiConnection(
                ssid = null,
                bssid = homeBssid,
                securityFamily = SecurityFamily.WPA2_PERSONAL,
                rssi = -50,
            )
        assertEquals(
            NetworkConnectionMatch.Exact,
            matcher.match(homeSsid, homeBssid, connection),
        )
    }

    @Test
    fun sameSsidDifferentBssid_isProbable() {
        val connection =
            CurrentWifiConnection(
                ssid = homeSsid,
                bssid = otherBssid,
                securityFamily = SecurityFamily.WPA2_PERSONAL,
                rssi = -55,
            )
        assertEquals(
            NetworkConnectionMatch.Probable,
            matcher.match(homeSsid, homeBssid, connection),
        )
    }

    @Test
    fun sameSsidOnly_isProbable() {
        val connection =
            CurrentWifiConnection(
                ssid = Ssid("Casa"),
                bssid = null,
                securityFamily = null,
                rssi = null,
            )
        assertEquals(
            NetworkConnectionMatch.Probable,
            matcher.match(homeSsid, homeBssid, connection),
        )
    }

    @Test
    fun differentSsid_isDifferentNetwork() {
        val connection =
            CurrentWifiConnection(
                ssid = Ssid("Otro"),
                bssid = otherBssid,
                securityFamily = SecurityFamily.WPA2_PERSONAL,
                rssi = -60,
            )
        assertEquals(
            NetworkConnectionMatch.DifferentNetwork,
            matcher.match(homeSsid, homeBssid, connection),
        )
    }

    @Test
    fun redactedIdentifiers_areInsufficient() {
        val connection =
            CurrentWifiConnection(
                ssid = null,
                bssid = Bssid.of("02:00:00:00:00:00"),
                securityFamily = null,
                rssi = -40,
            )
        assertIs<NetworkConnectionMatch.InsufficientInformation>(
            matcher.match(homeSsid, homeBssid, connection),
        )
    }
}
