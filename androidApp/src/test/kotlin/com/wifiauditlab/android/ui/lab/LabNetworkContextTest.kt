package com.wifiauditlab.android.ui.lab

import com.wifiauditlab.android.R
import com.wifiauditlab.android.ui.nearby.NearbyItem
import com.wifiauditlab.android.ui.security.familyLabelRes
import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.ManagementFrameProtection
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.Ssid
import com.wifiauditlab.assessment.domain.wifi.WifiBand
import com.wifiauditlab.assessment.domain.wifi.WifiChannel
import com.wifiauditlab.assessment.domain.wifi.WifiObservation
import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile
import com.wifiauditlab.assessment.domain.wifi.WifiSignal
import com.wifiauditlab.assessment.domain.wifi.WifiStandard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabNetworkContextTest {
    private fun observation(
        ssid: String = "Home",
        family: SecurityFamily = SecurityFamily.WPA2_PERSONAL,
    ): WifiObservation =
        WifiObservation(
            ssid = Ssid(ssid),
            bssid = Bssid.of("AA:BB:CC:DD:EE:01"),
            signal = WifiSignal(-50),
            channel = WifiChannel(6, WifiBand.GHZ_2_4, 2437),
            standard = WifiStandard.WIFI_5,
            securityProfile =
                WifiSecurityProfile(
                    family = family,
                    keyManagements = setOf("PSK"),
                    managementFrameProtection = ManagementFrameProtection.UNKNOWN,
                    isTransitionMode = false,
                    rawCapabilities = "[WPA2-PSK-CCMP][ESS]",
                ),
            observedAtEpochMillis = 0L,
        )

    @Test
    fun fromNearby_mapsIdentityAndDoesNotTouchLabModule() {
        val item =
            NearbyItem(
                observation = observation(ssid = "MOVISTAR_XXXX", family = SecurityFamily.WPA2_WPA3_PERSONAL),
                alias = "Casa",
                savedNetworkId = null,
                isKnown = true,
                ambiguous = false,
            )
        val context = labNetworkContextFromNearby(item, assessmentSummary = "Resumen de prueba")
        assertEquals("Casa", context.displayName)
        assertEquals("MOVISTAR_XXXX", context.ssidLabel)
        assertEquals(SecurityFamily.WPA2_WPA3_PERSONAL, context.securityFamily)
        assertEquals(WifiStandard.WIFI_5, context.wifiStandard)
        assertEquals(WifiBand.GHZ_2_4, context.band)
        assertEquals("Resumen de prueba", context.assessmentSummary)
        assertEquals(R.string.security_family_wpa2_wpa3, familyLabelRes(context.securityFamily))
        assertTrue(context.metaLine { "band-$it" }.contains("Wi-Fi 5"))
    }

    @Test
    fun prototypeFromContext_copiesSsidFamilyAndBand() {
        val context =
            labNetworkContextFromNearby(
                NearbyItem(
                    observation = observation(ssid = "LabNet", family = SecurityFamily.WPA2_PERSONAL),
                    alias = "Casa",
                    savedNetworkId = null,
                    isKnown = false,
                    ambiguous = false,
                ),
            )
        val prototype = localNetworkPrototypeFromContext(context)
        assertEquals("Casa", prototype.displayName)
        assertEquals("LabNet", prototype.ssid)
        assertEquals(SecurityFamily.WPA2_PERSONAL, prototype.securityFamily)
        assertEquals(WifiBand.GHZ_2_4, prototype.band)
        assertEquals(WifiStandard.WIFI_5, prototype.standard)
        assertTrue(context.seedKey().contains("LabNet"))
    }

    @Test
    fun store_setAndClear() {
        val store = LabNetworkContextStore()
        assertNull(store.current)
        val context =
            labNetworkContextFromNearby(
                NearbyItem(
                    observation = observation(),
                    alias = null,
                    savedNetworkId = null,
                    isKnown = false,
                    ambiguous = false,
                ),
            )
        store.set(context)
        assertEquals("Home", store.current?.displayName)
        store.clear()
        assertNull(store.current)
    }
}
