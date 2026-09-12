package com.wifiauditlab.assessment.domain.security

import com.wifiauditlab.assessment.domain.wifi.ManagementFrameProtection
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssessmentStrategiesTest {
    private fun profile(
        family: SecurityFamily,
        mfp: ManagementFrameProtection = ManagementFrameProtection.UNKNOWN,
        keyManagements: Set<String> = emptySet(),
        transition: Boolean = false,
        raw: String? = null,
    ) = WifiSecurityProfile(
        family = family,
        keyManagements = keyManagements,
        managementFrameProtection = mfp,
        isTransitionMode = transition,
        rawCapabilities = raw,
    )

    private val registry = SecurityAssessmentRegistry.default()

    @Test
    fun open_network_is_insecure_with_critical_cleartext_finding() =
        runTest {
            val result = OpenNetworkAssessmentStrategy().assess(profile(SecurityFamily.OPEN))
            assertEquals(SecurityRating.INSECURE, result.rating)
            assertTrue(result.findings.any { it.severity == Severity.CRITICAL && it.title == "Tráfico sin cifrar" })
        }

    @Test
    fun legacy_strategy_supports_wep_and_wpa1_with_distinct_headlines() =
        runTest {
            val legacy = LegacyNetworkAssessmentStrategy()
            assertTrue(legacy.supports(profile(SecurityFamily.WEP)))
            assertTrue(legacy.supports(profile(SecurityFamily.WPA_PERSONAL)))

            val wep = legacy.assess(profile(SecurityFamily.WEP))
            val wpa1 = legacy.assess(profile(SecurityFamily.WPA_PERSONAL))
            assertEquals(SecurityRating.INSECURE, wep.rating)
            assertEquals(SecurityRating.INSECURE, wpa1.rating)
            assertTrue(wep.headline.contains("WEP"))
            assertTrue(wpa1.headline.contains("WPA"))
            assertFalse(wep.headline == wpa1.headline)
        }

    @Test
    fun personal_wpa2_is_moderate_and_flags_offline_attacks() =
        runTest {
            val result =
                PersonalNetworkAssessmentStrategy()
                    .assess(profile(SecurityFamily.WPA2_PERSONAL, keyManagements = setOf("PSK")))
            assertEquals(SecurityRating.MODERATE, result.rating)
            assertTrue(result.findings.any { it.severity == Severity.MEDIUM && it.title == "Vulnerable a ataques offline" })
        }

    @Test
    fun personal_wpa3_is_high_and_reports_active_pmf() =
        runTest {
            val result =
                PersonalNetworkAssessmentStrategy()
                    .assess(profile(SecurityFamily.WPA3_PERSONAL, mfp = ManagementFrameProtection.REQUIRED))
            assertEquals(SecurityRating.HIGH, result.rating)
            assertTrue(result.findings.any { it.title == "Protección de tramas activa" })
            assertFalse(result.findings.any { it.title == "Vulnerable a ataques offline" })
        }

    @Test
    fun personal_transition_is_high() =
        runTest {
            val result = PersonalNetworkAssessmentStrategy().assess(profile(SecurityFamily.WPA2_WPA3_PERSONAL, transition = true))
            assertEquals(SecurityRating.HIGH, result.rating)
            assertTrue(result.headline.contains("transición"))
        }

    @Test
    fun enterprise_wpa3_reports_suite_b_in_technical_summary() =
        runTest {
            val result = EnterpriseNetworkAssessmentStrategy().assess(profile(SecurityFamily.WPA3_ENTERPRISE))
            assertEquals(SecurityRating.HIGH, result.rating)
            assertTrue(result.technicalSummary.contains("Suite-B"))
        }

    @Test
    fun owe_is_moderate_and_warns_about_missing_ap_authentication() =
        runTest {
            val result = OweAssessmentStrategy().assess(profile(SecurityFamily.OWE))
            assertEquals(SecurityRating.MODERATE, result.rating)
            assertTrue(result.findings.any { it.title == "Sin autenticación del punto de acceso" })
        }

    @Test
    fun passpoint_and_dpp_are_high() =
        runTest {
            assertEquals(SecurityRating.HIGH, PasspointAssessmentStrategy().assess(profile(SecurityFamily.PASSPOINT)).rating)
            assertEquals(SecurityRating.HIGH, DppAssessmentStrategy().assess(profile(SecurityFamily.DPP)).rating)
        }

    @Test
    fun unsupported_fallback_is_low_and_non_conclusive() =
        runTest {
            val result = UnsupportedAssessmentStrategy().assess(profile(SecurityFamily.UNKNOWN, raw = "???"))
            assertEquals(SecurityRating.LOW, result.rating)
            assertTrue(result.findings.any { it.title == "Evaluación no concluyente" })
        }

    @Test
    fun registry_resolves_expected_rating_for_every_family() =
        runTest {
            val expected =
                mapOf(
                    SecurityFamily.OPEN to SecurityRating.INSECURE,
                    SecurityFamily.WEP to SecurityRating.INSECURE,
                    SecurityFamily.WPA_PERSONAL to SecurityRating.INSECURE,
                    SecurityFamily.WPA2_PERSONAL to SecurityRating.MODERATE,
                    SecurityFamily.WPA3_PERSONAL to SecurityRating.HIGH,
                    SecurityFamily.WPA2_WPA3_PERSONAL to SecurityRating.HIGH,
                    SecurityFamily.WPA2_ENTERPRISE to SecurityRating.HIGH,
                    SecurityFamily.WPA3_ENTERPRISE to SecurityRating.HIGH,
                    SecurityFamily.OWE to SecurityRating.MODERATE,
                    SecurityFamily.PASSPOINT to SecurityRating.HIGH,
                    SecurityFamily.DPP to SecurityRating.HIGH,
                    SecurityFamily.UNKNOWN to SecurityRating.LOW,
                )
            for ((family, rating) in expected) {
                assertEquals(rating, registry.assess(profile(family)).rating, "rating for $family")
            }
        }
}
