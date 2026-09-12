package com.wifiauditlab.assessment.domain.classifier

import com.wifiauditlab.assessment.domain.wifi.ManagementFrameProtection
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile

/**
 * Normalizes raw Android-style capability strings (e.g.
 * `"[WPA2-PSK-CCMP][RSN-SAE-CCMP][ESS]"`) into a platform-independent
 * [WifiSecurityProfile].
 *
 * This classifier is part of the business core: it contains no Android types so
 * it can be unit-tested without an emulator and reused on iOS.
 */
class WifiSecurityClassifier {

    fun classify(capabilities: String): WifiSecurityProfile {
        val caps = capabilities.uppercase()

        val hasSae = caps.contains("SAE")
        val hasPsk = caps.contains("PSK")
        val hasOwe = caps.contains("OWE")
        val hasDpp = caps.contains("DPP")
        val hasWep = caps.contains("WEP")
        val hasSuiteB = caps.contains("SUITE_B") || caps.contains("SUITE-B") || caps.contains("EAP_SUITE_B")
        val hasEap = caps.contains("EAP") || caps.contains("IEEE8021X") || hasSuiteB
        val hasPasspoint = caps.contains("PASSPOINT")
        val isWpa2Gen = caps.contains("WPA2") || caps.contains("RSN")
        val isWpa1Only = caps.contains("[WPA-") && !isWpa2Gen

        val keyManagements = buildSet {
            if (hasSae) add("SAE")
            if (hasPsk) add("PSK")
            if (hasEap) add(if (hasSuiteB) "EAP_SUITE_B" else "EAP")
            if (hasOwe) add("OWE")
            if (hasDpp) add("DPP")
            if (hasWep) add("WEP")
        }

        val family = when {
            hasOwe -> SecurityFamily.OWE
            hasEap -> if (hasSae || hasSuiteB) SecurityFamily.WPA3_ENTERPRISE else SecurityFamily.WPA2_ENTERPRISE
            hasSae && hasPsk -> SecurityFamily.WPA2_WPA3_PERSONAL
            hasSae -> SecurityFamily.WPA3_PERSONAL
            hasPsk -> if (isWpa1Only) SecurityFamily.WPA_PERSONAL else SecurityFamily.WPA2_PERSONAL
            hasDpp -> SecurityFamily.DPP
            hasWep -> SecurityFamily.WEP
            hasPasspoint -> SecurityFamily.PASSPOINT
            isOpen(caps) -> SecurityFamily.OPEN
            else -> SecurityFamily.UNKNOWN
        }

        val mfp = when {
            caps.contains("MFPR") -> ManagementFrameProtection.REQUIRED
            caps.contains("MFPC") -> ManagementFrameProtection.CAPABLE
            family == SecurityFamily.WPA3_PERSONAL ||
                family == SecurityFamily.WPA3_ENTERPRISE ||
                family == SecurityFamily.OWE -> ManagementFrameProtection.REQUIRED
            family == SecurityFamily.OPEN -> ManagementFrameProtection.DISABLED
            else -> ManagementFrameProtection.UNKNOWN
        }

        return WifiSecurityProfile(
            family = family,
            keyManagements = keyManagements,
            managementFrameProtection = mfp,
            isTransitionMode = family == SecurityFamily.WPA2_WPA3_PERSONAL,
            rawCapabilities = capabilities,
        )
    }

    private fun isOpen(caps: String): Boolean {
        // No key-management token present; only ESS/IBSS/WPS markers (or empty).
        val meaningful = listOf("PSK", "SAE", "EAP", "OWE", "DPP", "WEP", "IEEE8021X")
        return meaningful.none { caps.contains(it) }
    }
}
