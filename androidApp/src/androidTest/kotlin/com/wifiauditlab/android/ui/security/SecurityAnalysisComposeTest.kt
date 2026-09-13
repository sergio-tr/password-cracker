package com.wifiauditlab.android.ui.security

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.wifiauditlab.android.R
import com.wifiauditlab.android.ui.theme.WifiAuditLabTheme
import com.wifiauditlab.assessment.application.AssessNetworkSecurity
import com.wifiauditlab.assessment.domain.security.SecurityAssessmentRegistry
import com.wifiauditlab.assessment.domain.security.SecurityRating
import com.wifiauditlab.assessment.domain.wifi.ManagementFrameProtection
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class SecurityAnalysisComposeTest(
    private val family: SecurityFamily,
    private val expectedRating: SecurityRating,
    private val transition: Boolean,
) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): Collection<Array<Any>> =
            listOf(
                arrayOf(SecurityFamily.OPEN, SecurityRating.INSECURE, false),
                arrayOf(SecurityFamily.WEP, SecurityRating.INSECURE, false),
                arrayOf(SecurityFamily.WPA2_PERSONAL, SecurityRating.MODERATE, false),
                arrayOf(SecurityFamily.WPA3_PERSONAL, SecurityRating.HIGH, false),
                arrayOf(SecurityFamily.WPA2_WPA3_PERSONAL, SecurityRating.HIGH, true),
                arrayOf(SecurityFamily.WPA2_ENTERPRISE, SecurityRating.HIGH, false),
                arrayOf(SecurityFamily.OWE, SecurityRating.MODERATE, false),
                arrayOf(SecurityFamily.UNKNOWN, SecurityRating.LOW, false),
            )
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val activity get() = composeTestRule.activity

    @Test
    fun assessment_showsRatingHeadlineRecommendationsAndTechnicalDetails() {
        val store = SecurityAnalysisTargetStore()
        store.set(
            SecurityAnalysisRequest(
                displayName = family.name,
                ssidLabel = "SSID-${family.name}",
                profile = profile(family, transition),
            ),
        )
        val vm =
            SecurityAnalysisViewModel(
                store,
                AssessNetworkSecurity(SecurityAssessmentRegistry.default()),
            )
        composeTestRule.setContent {
            WifiAuditLabTheme {
                SecurityAnalysisScreen(viewModel = vm)
            }
        }

        val ratingText = activity.getString(ratingLabelRes(expectedRating))
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText(ratingText).fetchSemanticsNodes().isNotEmpty()
        }

        val summary = activity.getString(R.string.security_summary)
        val familyLine =
            activity.getString(
                R.string.security_family,
                activity.getString(familyLabelRes(family)),
            )
        val transitionText = activity.getString(R.string.security_transition)
        val recommendations = activity.getString(R.string.security_recommendations)
        val showTechnical = activity.getString(R.string.security_technical_show)
        val hideTechnical = activity.getString(R.string.security_technical_hide)
        val expectedHeadline = vm.state.value.assessment!!.headline

        composeTestRule.onNodeWithText(summary).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(ratingText).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(expectedHeadline).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(familyLine).performScrollTo().assertIsDisplayed()

        if (family == SecurityFamily.WPA2_WPA3_PERSONAL || transition) {
            composeTestRule
                .onNodeWithText(transitionText)
                .performScrollTo()
                .assertIsDisplayed()
        }

        composeTestRule.onNodeWithText(recommendations).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(showTechnical).performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(hideTechnical).performScrollTo().assertIsDisplayed()
        assertTrue(vm.state.value.technicalExpanded)
        assertTrue(vm.state.value.recommendations.isNotEmpty())
    }
}

private fun profile(
    family: SecurityFamily,
    transition: Boolean = false,
): WifiSecurityProfile =
    WifiSecurityProfile(
        family = family,
        keyManagements =
            when (family) {
                SecurityFamily.WPA3_PERSONAL -> setOf("SAE")
                SecurityFamily.WPA2_PERSONAL -> setOf("WPA-PSK")
                SecurityFamily.WPA2_WPA3_PERSONAL -> setOf("WPA-PSK", "SAE")
                SecurityFamily.WPA2_ENTERPRISE -> setOf("WPA-EAP")
                else -> emptySet()
            },
        managementFrameProtection =
            when (family) {
                SecurityFamily.WPA3_PERSONAL -> ManagementFrameProtection.REQUIRED
                SecurityFamily.WPA2_PERSONAL -> ManagementFrameProtection.DISABLED
                else -> ManagementFrameProtection.UNKNOWN
            },
        isTransitionMode = transition || family == SecurityFamily.WPA2_WPA3_PERSONAL,
        rawCapabilities = "[${family.name}]",
    )
