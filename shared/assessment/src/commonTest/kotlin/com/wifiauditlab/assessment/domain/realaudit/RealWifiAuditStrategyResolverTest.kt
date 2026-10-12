package com.wifiauditlab.assessment.domain.realaudit

import com.wifiauditlab.assessment.domain.wifi.ManagementFrameProtection
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile
import kotlin.test.Test
import kotlin.test.assertEquals

class RealWifiAuditStrategyResolverTest {
    private val resolver = DefaultRealWifiAuditStrategyResolver()

    @Test
    fun maps_every_security_family() {
        val expected =
            mapOf(
                SecurityFamily.OPEN to RealWifiAuditStrategy.OpenNetworkAssessment,
                SecurityFamily.WEP to RealWifiAuditStrategy.LegacyPersonalKeyAudit,
                SecurityFamily.WPA_PERSONAL to RealWifiAuditStrategy.LegacyPersonalKeyAudit,
                SecurityFamily.WPA2_PERSONAL to RealWifiAuditStrategy.PersonalPskAudit,
                SecurityFamily.WPA3_PERSONAL to RealWifiAuditStrategy.PersonalSaeAudit,
                SecurityFamily.WPA2_WPA3_PERSONAL to RealWifiAuditStrategy.TransitionPersonalAudit,
                SecurityFamily.WPA2_ENTERPRISE to RealWifiAuditStrategy.EnterpriseAssessment,
                SecurityFamily.WPA3_ENTERPRISE to RealWifiAuditStrategy.EnterpriseAssessment,
                SecurityFamily.OWE to RealWifiAuditStrategy.EnhancedOpenAssessment,
                SecurityFamily.PASSPOINT to RealWifiAuditStrategy.PasspointAssessment,
                SecurityFamily.DPP to RealWifiAuditStrategy.DppAssessment,
                SecurityFamily.UNKNOWN to RealWifiAuditStrategy.Unsupported,
            )
        assertEquals(SecurityFamily.entries.toSet(), expected.keys)
        for ((family, strategy) in expected) {
            assertEquals(strategy, resolver.resolve(profile(family)), "family=$family")
        }
    }

    private fun profile(family: SecurityFamily): WifiSecurityProfile =
        WifiSecurityProfile(
            family = family,
            keyManagements = emptySet(),
            managementFrameProtection = ManagementFrameProtection.UNKNOWN,
            isTransitionMode = family == SecurityFamily.WPA2_WPA3_PERSONAL,
            rawCapabilities = null,
        )
}
