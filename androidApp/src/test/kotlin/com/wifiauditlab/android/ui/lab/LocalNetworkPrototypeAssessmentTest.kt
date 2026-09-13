package com.wifiauditlab.android.ui.lab

import com.wifiauditlab.assessment.application.AssessNetworkSecurity
import com.wifiauditlab.assessment.domain.security.SecurityAssessmentRegistry
import com.wifiauditlab.assessment.domain.security.SecurityRating
import com.wifiauditlab.assessment.domain.security.Severity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkPrototypeAssessmentTest {
    private val assess = AssessNetworkSecurity(SecurityAssessmentRegistry.default())

    @Test
    fun wpa2_vs_wpa3_prototypes_yieldDifferentRatingsAndFindings() =
        runTest {
            val wpa2 =
                LocalNetworkPrototype(
                    ssid = "Lab-WPA2",
                    securityProfile = PrototypeSecurityPreset.WPA2_PERSONAL.toProfile(),
                )
            val wpa3 =
                LocalNetworkPrototype(
                    ssid = "Lab-WPA3",
                    securityProfile = PrototypeSecurityPreset.WPA3_PERSONAL.toProfile(),
                )

            val wpa2Assessment = assess(wpa2.securityProfile)
            val wpa3Assessment = assess(wpa3.securityProfile)

            assertEquals(SecurityRating.MODERATE, wpa2Assessment.rating)
            assertEquals(SecurityRating.HIGH, wpa3Assessment.rating)
            assertTrue(wpa2Assessment.findings.any { it.severity == Severity.MEDIUM })
            assertFalse(wpa3Assessment.findings.any { it.title == "Vulnerable a ataques offline" })
        }

    @Test
    fun openPreset_isInsecure_withoutPskAuditPath() =
        runTest {
            val open =
                LocalNetworkPrototype(
                    ssid = "OpenLab",
                    securityProfile = PrototypeSecurityPreset.OPEN.toProfile(),
                )
            val assessment = assess(open.securityProfile)
            assertEquals(SecurityRating.INSECURE, assessment.rating)
            assertFalse(open.securityFamily.supportsSharedPasswordDemo())
        }

    @Test
    fun enterprisePreset_hasNoSharedPasswordDemo() =
        runTest {
            val enterprise =
                LocalNetworkPrototype(
                    ssid = "Corp",
                    securityProfile = PrototypeSecurityPreset.ENTERPRISE_WPA2.toProfile(),
                )
            assertFalse(enterprise.securityFamily.supportsSharedPasswordDemo())
            assertTrue(assess(enterprise.securityProfile).headline.contains("Enterprise"))
        }
}
