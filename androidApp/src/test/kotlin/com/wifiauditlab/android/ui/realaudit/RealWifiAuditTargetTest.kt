package com.wifiauditlab.android.ui.realaudit

import com.wifiauditlab.android.ui.nearby.NearbyItem
import com.wifiauditlab.assessment.domain.vault.SavedNetworkId
import com.wifiauditlab.assessment.domain.vault.SavedWifiNetwork
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
import org.junit.Test

class RealWifiAuditTargetTest {
    @Test
    fun fromNearby_preserves_selected_network_and_security_profile() {
        val profile =
            WifiSecurityProfile(
                family = SecurityFamily.WPA2_PERSONAL,
                keyManagements = setOf("PSK"),
                managementFrameProtection = ManagementFrameProtection.UNKNOWN,
                isTransitionMode = false,
                rawCapabilities = "[WPA2-PSK-CCMP]",
            )
        val observation =
            WifiObservation(
                ssid = Ssid("MiRed"),
                bssid = Bssid.of("11:22:33:44:55:66"),
                signal = WifiSignal(-50),
                channel = WifiChannel(6, WifiBand.GHZ_2_4, 2437),
                standard = WifiStandard.WIFI_4,
                securityProfile = profile,
                observedAtEpochMillis = 1L,
            )
        val item =
            NearbyItem(
                observation = observation,
                alias = "Casa",
                savedNetworkId = null,
                isKnown = false,
                ambiguous = false,
            )

        val target = realWifiAuditTargetFromNearby(item)

        assertEquals(Ssid("MiRed"), target.network.ssid)
        assertEquals(Bssid.of("11:22:33:44:55:66"), target.network.bssid)
        assertEquals(SecurityFamily.WPA2_PERSONAL, target.network.securityFamily)
        assertEquals("Casa", target.network.displayName)
        assertEquals(profile, target.securityProfile)
    }

    @Test
    fun fromVault_allows_missing_bssid() {
        val profile =
            WifiSecurityProfile(
                family = SecurityFamily.WPA3_PERSONAL,
                keyManagements = emptySet(),
                managementFrameProtection = ManagementFrameProtection.REQUIRED,
                isTransitionMode = false,
                rawCapabilities = null,
            )
        val network =
            SavedWifiNetwork(
                id = SavedNetworkId("1"),
                alias = "SinBssid",
                ssid = "SinBssid",
                securityFamily = SecurityFamily.WPA3_PERSONAL,
                knownBssids = emptySet(),
                locationLabel = null,
                geoLocation = null,
                secretId = null,
                notes = null,
                createdAtEpochMillis = 0L,
                lastSeenAtEpochMillis = null,
            )

        val target = realWifiAuditTargetFromVault(network, profile)
        assertNull(target.network.bssid)
        assertEquals(Ssid("SinBssid"), target.network.ssid)
        assertEquals(profile, target.securityProfile)
    }
}
