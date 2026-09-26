package com.wifiauditlab.android.ui.audit

import com.wifiauditlab.assessment.domain.vault.SavedNetworkId
import com.wifiauditlab.assessment.domain.vault.SavedWifiNetwork
import com.wifiauditlab.assessment.domain.vault.SecretId
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PasswordAuditRequestFromVaultTest {
    @Test
    fun fromVault_mapsIdentityAndSavedId_withoutObservation() {
        val network =
            SavedWifiNetwork(
                id = SavedNetworkId("id-1"),
                alias = "Casa",
                ssid = "HOME",
                securityFamily = SecurityFamily.WPA2_PERSONAL,
                knownBssids = emptySet(),
                locationLabel = null,
                geoLocation = null,
                secretId = SecretId("sec-1"),
                notes = null,
                createdAtEpochMillis = 1L,
                lastSeenAtEpochMillis = null,
            )
        val request = passwordAuditRequestFromVault(network)
        assertEquals("Casa", request.network.displayName)
        assertEquals("HOME", request.network.ssid.value)
        assertEquals(SecurityFamily.WPA2_PERSONAL, request.network.securityProfile.family)
        assertEquals(network.id, request.savedNetworkId)
        assertNull(request.observation)
    }
}
