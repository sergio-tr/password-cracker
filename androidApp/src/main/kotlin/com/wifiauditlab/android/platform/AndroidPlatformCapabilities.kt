package com.wifiauditlab.android.platform

import com.wifiauditlab.core.platform.PlatformCapabilities

/**
 * Android's capability profile. On Android all four capabilities are available
 * (subject to runtime permissions). iOS will provide a different profile without
 * the domain needing platform conditionals.
 */
class AndroidPlatformCapabilities : PlatformCapabilities {
    override val nearbyWifiDiscovery: Boolean = true
    override val currentWifiInspection: Boolean = true
    override val secureSecretStorage: Boolean = true
    override val geolocation: Boolean = true
}
