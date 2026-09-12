package com.wifiauditlab.assessment.domain.vault

import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.NetworkIdentity
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VaultModelTest {
    @Test
    fun network_secret_toString_never_reveals_the_value() {
        val secret = NetworkSecret("super-secret-pass")
        assertEquals(NetworkSecret.REDACTED, secret.toString())
        assertFalse(secret.toString().contains("super-secret-pass"))
    }

    @Test
    fun network_secret_equality_is_by_value() {
        assertEquals(NetworkSecret("abc123"), NetworkSecret("abc123"))
        assertEquals(NetworkSecret("abc123").hashCode(), NetworkSecret("abc123").hashCode())
    }

    @Test
    fun network_secret_rejects_empty() {
        assertFailsWith<IllegalArgumentException> { NetworkSecret("") }
    }

    @Test
    fun ids_reject_blank_and_generate_distinct_randoms() {
        assertFailsWith<IllegalArgumentException> { SavedNetworkId("  ") }
        assertFailsWith<IllegalArgumentException> { SecretId("") }
        assertTrue(SavedNetworkId.random() != SavedNetworkId.random())
        assertTrue(SecretId.random() != SecretId.random())
    }

    @Test
    fun saved_network_identity_and_hasSecret_reflect_fields() {
        val base =
            SavedWifiNetwork(
                id = SavedNetworkId("id-1"),
                alias = "Casa",
                ssid = "  MyHome  ",
                securityFamily = SecurityFamily.WPA3_PERSONAL,
                knownBssids = setOf(Bssid.of("AA:BB:CC:DD:EE:FF")),
                locationLabel = LocationLabel("Casa"),
                geoLocation = null,
                secretId = null,
                notes = null,
                createdAtEpochMillis = 1L,
                lastSeenAtEpochMillis = null,
            )
        assertEquals(NetworkIdentity("MyHome", SecurityFamily.WPA3_PERSONAL), base.identity)
        assertFalse(base.hasSecret)
        assertTrue(base.copy(secretId = SecretId("s-1")).hasSecret)
    }

    @Test
    fun new_saved_network_has_safe_defaults() {
        val payload = NewSavedWifiNetwork(alias = "Trabajo", ssid = "Office", securityFamily = SecurityFamily.WPA2_ENTERPRISE)
        assertTrue(payload.knownBssids.isEmpty())
        assertEquals(null, payload.locationLabel)
        assertEquals(null, payload.geoLocation)
        assertEquals(null, payload.notes)
    }
}
