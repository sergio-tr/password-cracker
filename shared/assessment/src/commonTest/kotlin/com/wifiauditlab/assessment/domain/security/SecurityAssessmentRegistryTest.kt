package com.wifiauditlab.assessment.domain.security

import com.wifiauditlab.assessment.domain.classifier.WifiSecurityClassifier
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SecurityAssessmentRegistryTest {

    private val classifier = WifiSecurityClassifier()
    private val registry = SecurityAssessmentRegistry.default()

    private suspend fun rate(capabilities: String): SecurityRating =
        registry.assess(classifier.classify(capabilities)).rating

    @Test
    fun open_network_is_insecure() = runTest {
        assertEquals(SecurityRating.INSECURE, rate("[ESS]"))
    }

    @Test
    fun wep_is_insecure() = runTest {
        assertEquals(SecurityRating.INSECURE, rate("[WEP][ESS]"))
    }

    @Test
    fun wpa2_personal_is_moderate() = runTest {
        assertEquals(SecurityRating.MODERATE, rate("[WPA2-PSK-CCMP][ESS]"))
    }

    @Test
    fun wpa3_personal_is_high() = runTest {
        assertEquals(SecurityRating.HIGH, rate("[RSN-SAE-CCMP][MFPR][ESS]"))
    }

    @Test
    fun enterprise_is_high() = runTest {
        assertEquals(SecurityRating.HIGH, rate("[RSN-EAP-CCMP][ESS]"))
    }

    @Test
    fun owe_is_moderate() = runTest {
        assertEquals(SecurityRating.MODERATE, rate("[RSN-OWE-CCMP][ESS]"))
    }

    @Test
    fun unknown_profile_falls_back_without_throwing() = runTest {
        val profile = com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile(
            family = com.wifiauditlab.assessment.domain.wifi.SecurityFamily.UNKNOWN,
            keyManagements = emptySet(),
            managementFrameProtection = com.wifiauditlab.assessment.domain.wifi.ManagementFrameProtection.UNKNOWN,
            isTransitionMode = false,
            rawCapabilities = "???",
        )
        assertEquals(SecurityRating.LOW, registry.assess(profile).rating)
    }
}
