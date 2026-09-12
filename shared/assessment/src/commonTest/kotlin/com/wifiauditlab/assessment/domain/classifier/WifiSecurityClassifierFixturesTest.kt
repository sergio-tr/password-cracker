package com.wifiauditlab.assessment.domain.classifier

import com.wifiauditlab.assessment.domain.wifi.ManagementFrameProtection
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Table-driven fixtures covering one representative Android capability string per
 * security family (and management-frame-protection variants), so a regression in
 * classification is caught immediately.
 */
class WifiSecurityClassifierFixturesTest {
    private val classifier = WifiSecurityClassifier()

    private data class Case(
        val capabilities: String,
        val family: SecurityFamily,
        val mfp: ManagementFrameProtection,
        val transition: Boolean,
        val keyManagements: Set<String>,
    )

    private val cases =
        listOf(
            Case("[ESS]", SecurityFamily.OPEN, ManagementFrameProtection.DISABLED, false, emptySet()),
            Case("[WEP][ESS]", SecurityFamily.WEP, ManagementFrameProtection.UNKNOWN, false, setOf("WEP")),
            Case("[WPA-PSK-TKIP][ESS]", SecurityFamily.WPA_PERSONAL, ManagementFrameProtection.UNKNOWN, false, setOf("PSK")),
            Case("[WPA2-PSK-CCMP][ESS]", SecurityFamily.WPA2_PERSONAL, ManagementFrameProtection.UNKNOWN, false, setOf("PSK")),
            Case("[WPA2-PSK-CCMP][MFPC][ESS]", SecurityFamily.WPA2_PERSONAL, ManagementFrameProtection.CAPABLE, false, setOf("PSK")),
            Case("[RSN-SAE-CCMP][ESS]", SecurityFamily.WPA3_PERSONAL, ManagementFrameProtection.REQUIRED, false, setOf("SAE")),
            Case(
                "[WPA2-PSK-CCMP][RSN-SAE-CCMP][ESS]",
                SecurityFamily.WPA2_WPA3_PERSONAL,
                ManagementFrameProtection.UNKNOWN,
                true,
                setOf("SAE", "PSK"),
            ),
            Case("[RSN-EAP-CCMP][ESS]", SecurityFamily.WPA2_ENTERPRISE, ManagementFrameProtection.UNKNOWN, false, setOf("EAP")),
            Case("[RSN-IEEE8021X-CCMP][ESS]", SecurityFamily.WPA2_ENTERPRISE, ManagementFrameProtection.UNKNOWN, false, setOf("EAP")),
            Case(
                "[RSN-SUITE_B-CCMP][MFPR][ESS]",
                SecurityFamily.WPA3_ENTERPRISE,
                ManagementFrameProtection.REQUIRED,
                false,
                setOf("EAP_SUITE_B"),
            ),
            Case("[RSN-OWE-CCMP][ESS]", SecurityFamily.OWE, ManagementFrameProtection.REQUIRED, false, setOf("OWE")),
            Case("[DPP][ESS]", SecurityFamily.DPP, ManagementFrameProtection.UNKNOWN, false, setOf("DPP")),
            Case("[PASSPOINT][ESS]", SecurityFamily.PASSPOINT, ManagementFrameProtection.UNKNOWN, false, emptySet()),
        )

    @Test
    fun classifies_every_security_family_fixture() {
        for (case in cases) {
            val profile = classifier.classify(case.capabilities)
            assertEquals(case.family, profile.family, "family for ${case.capabilities}")
            assertEquals(case.mfp, profile.managementFrameProtection, "mfp for ${case.capabilities}")
            assertEquals(case.transition, profile.isTransitionMode, "transition for ${case.capabilities}")
            assertEquals(case.keyManagements, profile.keyManagements, "keyManagements for ${case.capabilities}")
            assertEquals(case.capabilities, profile.rawCapabilities, "rawCapabilities preserved")
        }
    }

    @Test
    fun classification_is_case_insensitive() {
        assertEquals(
            SecurityFamily.WPA3_PERSONAL,
            classifier.classify("[rsn-sae-ccmp][ess]").family,
        )
    }
}
