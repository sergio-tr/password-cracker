package com.wifiauditlab.assessment.domain.realaudit

import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile

/**
 * Deterministic mapping from [WifiSecurityProfile] / [SecurityFamily] to a
 * [RealWifiAuditStrategy]. Pure and unit-testable.
 */
fun interface RealWifiAuditStrategyResolver {
    fun resolve(profile: WifiSecurityProfile): RealWifiAuditStrategy
}

class DefaultRealWifiAuditStrategyResolver : RealWifiAuditStrategyResolver {
    override fun resolve(profile: WifiSecurityProfile): RealWifiAuditStrategy =
        when (profile.family) {
            SecurityFamily.OPEN -> RealWifiAuditStrategy.OpenNetworkAssessment
            SecurityFamily.WEP -> RealWifiAuditStrategy.LegacyPersonalKeyAudit
            SecurityFamily.WPA_PERSONAL -> RealWifiAuditStrategy.LegacyPersonalKeyAudit
            SecurityFamily.WPA2_PERSONAL -> RealWifiAuditStrategy.PersonalPskAudit
            SecurityFamily.WPA3_PERSONAL -> RealWifiAuditStrategy.PersonalSaeAudit
            SecurityFamily.WPA2_WPA3_PERSONAL -> RealWifiAuditStrategy.TransitionPersonalAudit
            SecurityFamily.WPA2_ENTERPRISE,
            SecurityFamily.WPA3_ENTERPRISE,
            -> RealWifiAuditStrategy.EnterpriseAssessment
            SecurityFamily.OWE -> RealWifiAuditStrategy.EnhancedOpenAssessment
            SecurityFamily.PASSPOINT -> RealWifiAuditStrategy.PasspointAssessment
            SecurityFamily.DPP -> RealWifiAuditStrategy.DppAssessment
            SecurityFamily.UNKNOWN -> RealWifiAuditStrategy.Unsupported
        }
}

fun RealWifiAuditStrategy.displayLabel(): String =
    when (this) {
        RealWifiAuditStrategy.OpenNetworkAssessment -> "Open assessment"
        RealWifiAuditStrategy.LegacyPersonalKeyAudit -> "Legacy key audit"
        RealWifiAuditStrategy.PersonalPskAudit -> "PSK Audit"
        RealWifiAuditStrategy.PersonalSaeAudit -> "SAE Audit"
        RealWifiAuditStrategy.TransitionPersonalAudit -> "PSK/SAE transition audit"
        RealWifiAuditStrategy.EnterpriseAssessment -> "Enterprise assessment"
        RealWifiAuditStrategy.EnhancedOpenAssessment -> "OWE assessment"
        RealWifiAuditStrategy.PasspointAssessment -> "Passpoint assessment"
        RealWifiAuditStrategy.DppAssessment -> "DPP assessment"
        RealWifiAuditStrategy.Unsupported -> "Unsupported"
    }
