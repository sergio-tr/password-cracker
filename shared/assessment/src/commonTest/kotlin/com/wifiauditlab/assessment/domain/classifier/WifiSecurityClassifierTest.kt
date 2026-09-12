package com.wifiauditlab.assessment.domain.classifier

import com.wifiauditlab.assessment.domain.wifi.ManagementFrameProtection
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WifiSecurityClassifierTest {
    private val classifier = WifiSecurityClassifier()

    @Test
    fun classifies_open_network() {
        assertEquals(SecurityFamily.OPEN, classifier.classify("[ESS]").family)
    }

    @Test
    fun classifies_wpa2_personal() {
        val profile = classifier.classify("[WPA2-PSK-CCMP][ESS]")
        assertEquals(SecurityFamily.WPA2_PERSONAL, profile.family)
        assertTrue("PSK" in profile.keyManagements)
    }

    @Test
    fun classifies_wpa3_personal_with_pmf() {
        val profile = classifier.classify("[RSN-SAE-CCMP][MFPR][ESS]")
        assertEquals(SecurityFamily.WPA3_PERSONAL, profile.family)
        assertEquals(ManagementFrameProtection.REQUIRED, profile.managementFrameProtection)
    }

    @Test
    fun classifies_wpa2_wpa3_transition() {
        val profile = classifier.classify("[WPA2-PSK-CCMP][RSN-SAE-CCMP][ESS]")
        assertEquals(SecurityFamily.WPA2_WPA3_PERSONAL, profile.family)
        assertTrue(profile.isTransitionMode)
    }

    @Test
    fun classifies_wep_and_wpa1_as_legacy_families() {
        assertEquals(SecurityFamily.WEP, classifier.classify("[WEP][ESS]").family)
        assertEquals(SecurityFamily.WPA_PERSONAL, classifier.classify("[WPA-PSK-TKIP][ESS]").family)
    }

    @Test
    fun classifies_enterprise() {
        assertEquals(SecurityFamily.WPA2_ENTERPRISE, classifier.classify("[RSN-EAP-CCMP][ESS]").family)
        assertEquals(SecurityFamily.WPA3_ENTERPRISE, classifier.classify("[RSN-SUITE_B-CCMP][MFPR][ESS]").family)
    }

    @Test
    fun classifies_owe() {
        assertEquals(SecurityFamily.OWE, classifier.classify("[RSN-OWE-CCMP][ESS]").family)
    }
}
