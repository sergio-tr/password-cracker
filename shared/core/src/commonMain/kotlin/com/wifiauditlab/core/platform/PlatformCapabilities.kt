package com.wifiauditlab.core.platform

/**
 * Declares which platform-dependent capabilities are available at runtime.
 *
 * Cross-platform code must branch on capabilities rather than on the platform,
 * so that iOS limitations (e.g. no arbitrary nearby-Wi-Fi discovery) are handled
 * in one place instead of through scattered platform conditionals.
 */
interface PlatformCapabilities {
    val nearbyWifiDiscovery: Boolean
    val currentWifiInspection: Boolean
    val secureSecretStorage: Boolean
    val geolocation: Boolean
}

/** Immutable snapshot of [PlatformCapabilities], convenient for tests and DI. */
data class StaticPlatformCapabilities(
    override val nearbyWifiDiscovery: Boolean,
    override val currentWifiInspection: Boolean,
    override val secureSecretStorage: Boolean,
    override val geolocation: Boolean,
) : PlatformCapabilities
