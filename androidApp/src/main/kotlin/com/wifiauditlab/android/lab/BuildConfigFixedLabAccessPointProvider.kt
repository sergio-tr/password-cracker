package com.wifiauditlab.android.lab

import com.wifiauditlab.android.BuildConfig
import com.wifiauditlab.assessment.domain.audit.FixedLabAccessPoint
import com.wifiauditlab.assessment.domain.audit.FixedLabAccessPointProvider
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily

/**
 * Reads the single FixedLab AP from BuildConfig (lab flavor + local.properties).
 * Never embeds a password.
 */
class BuildConfigFixedLabAccessPointProvider : FixedLabAccessPointProvider {
    override fun configuredOrNull(): FixedLabAccessPoint? {
        if (!BuildConfig.REAL_WIFI_PROBE_ENABLED) return null
        val ssid = BuildConfig.LAB_WIFI_SSID.trim()
        val bssid = BuildConfig.LAB_WIFI_BSSID.trim()
        if (ssid.isEmpty() || bssid.isEmpty()) return null
        val family =
            runCatching { SecurityFamily.valueOf(BuildConfig.LAB_WIFI_SECURITY.trim()) }
                .getOrDefault(SecurityFamily.WPA2_PERSONAL)
        return runCatching { FixedLabAccessPoint(ssid, bssid, family) }.getOrNull()
    }
}

/** Whether the lab-flavor product surface for Prueba Wi‑Fi real is compiled in. */
object RealWifiProbeFeature {
    val isCompiledIn: Boolean get() = BuildConfig.REAL_WIFI_PROBE_ENABLED
}
