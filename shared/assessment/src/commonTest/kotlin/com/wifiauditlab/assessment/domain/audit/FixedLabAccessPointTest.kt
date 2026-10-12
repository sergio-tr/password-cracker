package com.wifiauditlab.assessment.domain.audit

import com.wifiauditlab.assessment.domain.connection.CurrentWifiConnection
import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.Ssid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FixedLabAccessPointTest {
    private val fixed =
        FixedLabAccessPoint(
            ssid = "WIFI_LAB_SERGIO",
            bssid = "AA:BB:CC:DD:EE:FF",
            securityFamily = SecurityFamily.WPA2_PERSONAL,
        )

    @Test
    fun matchesIdentity_requiresExactSsidAndBssid() {
        assertTrue(fixed.matchesIdentity("WIFI_LAB_SERGIO", "aa:bb:cc:dd:ee:ff"))
        assertFalse(fixed.matchesIdentity("WIFI_LAB_SERGIO", "11:22:33:44:55:66"))
        assertFalse(fixed.matchesIdentity("OTHER", "AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun matchesConnection_exactEligible() {
        val connection =
            CurrentWifiConnection(
                ssid = Ssid("WIFI_LAB_SERGIO"),
                bssid = Bssid.of("AA:BB:CC:DD:EE:FF"),
                securityFamily = SecurityFamily.WPA2_PERSONAL,
                rssi = -40,
            )
        assertEquals(FixedLabConnectionMatch.Matched, fixed.matchesConnection(connection))
    }

    @Test
    fun matchesConnection_sameSsidDifferentBssid_denied() {
        val connection =
            CurrentWifiConnection(
                ssid = Ssid("WIFI_LAB_SERGIO"),
                bssid = Bssid.of("11:22:33:44:55:66"),
                securityFamily = SecurityFamily.WPA2_PERSONAL,
                rssi = -40,
            )
        assertEquals(FixedLabConnectionMatch.TargetMismatch, fixed.matchesConnection(connection))
    }

    @Test
    fun matchesConnection_null_notConnected() {
        assertEquals(FixedLabConnectionMatch.NotConnected, fixed.matchesConnection(null))
    }
}
