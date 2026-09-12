package com.wifiauditlab.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StaticPlatformCapabilitiesTest {
    @Test
    fun exposes_each_capability_flag_independently() {
        val capabilities =
            StaticPlatformCapabilities(
                nearbyWifiDiscovery = true,
                currentWifiInspection = true,
                secureSecretStorage = true,
                geolocation = false,
            )

        assertTrue(capabilities.nearbyWifiDiscovery)
        assertTrue(capabilities.currentWifiInspection)
        assertTrue(capabilities.secureSecretStorage)
        assertFalse(capabilities.geolocation)
    }

    @Test
    fun models_a_restricted_platform_like_ios() {
        // A platform without arbitrary nearby-Wi-Fi discovery still stores secrets securely.
        val ios =
            StaticPlatformCapabilities(
                nearbyWifiDiscovery = false,
                currentWifiInspection = true,
                secureSecretStorage = true,
                geolocation = true,
            )
        val android =
            StaticPlatformCapabilities(
                nearbyWifiDiscovery = true,
                currentWifiInspection = true,
                secureSecretStorage = true,
                geolocation = true,
            )

        assertFalse(ios.nearbyWifiDiscovery)
        assertTrue(android.nearbyWifiDiscovery)
        assertEquals(ios.secureSecretStorage, android.secureSecretStorage)
    }
}
