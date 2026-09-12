package com.wifiauditlab.assessment.domain.wifi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WifiModelTest {
    @Test
    fun signal_quality_buckets_are_derived_from_rssi_boundaries() {
        assertEquals(SignalQuality.EXCELLENT, WifiSignal(-55).quality)
        assertEquals(SignalQuality.EXCELLENT, WifiSignal(-40).quality)
        assertEquals(SignalQuality.GOOD, WifiSignal(-56).quality)
        assertEquals(SignalQuality.GOOD, WifiSignal(-67).quality)
        assertEquals(SignalQuality.FAIR, WifiSignal(-68).quality)
        assertEquals(SignalQuality.FAIR, WifiSignal(-78).quality)
        assertEquals(SignalQuality.WEAK, WifiSignal(-79).quality)
        assertEquals(SignalQuality.WEAK, WifiSignal(-95).quality)
    }

    @Test
    fun ssid_treats_empty_as_hidden_and_reports_it() {
        assertTrue(Ssid("").isHidden)
        assertEquals("<hidden>", Ssid("").toString())
        assertEquals(Ssid.HIDDEN, Ssid(""))
        assertFalse(Ssid("Home").isHidden)
        assertEquals("Home", Ssid("Home").toString())
    }

    @Test
    fun ssid_normalized_trims_surrounding_whitespace() {
        assertEquals("MyNet", Ssid("  MyNet  ").normalized)
    }

    @Test
    fun bssid_is_normalized_to_lowercase_colon_form() {
        assertEquals("aa:bb:cc:11:22:33", Bssid.of("  AA:BB:CC:11:22:33 ").value)
        assertEquals(Bssid.of("AA:BB:CC:11:22:33"), Bssid.of("aa:bb:cc:11:22:33"))
    }

    @Test
    fun network_identity_is_ssid_plus_family_not_bssid() {
        val observation =
            WifiObservation(
                ssid = Ssid(" Café "),
                bssid = Bssid.of("AA:BB:CC:DD:EE:FF"),
                signal = WifiSignal(-60),
                channel = WifiChannel(36, WifiBand.GHZ_5, 5180),
                standard = WifiStandard.WIFI_6,
                securityProfile = WifiSecurityProfile.open(),
                observedAtEpochMillis = 0L,
            )
        assertEquals(NetworkIdentity("Café", SecurityFamily.OPEN), observation.identity)
    }
}
