package com.wifiauditlab.android.ui.security

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
    private val expectedHeadline: String,
) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): Collection<Array<Any>> =
            listOf(
                arrayOf(SecurityFamily.OPEN, SecurityRating.INSECURE, false, "Red abierta"),
                arrayOf(SecurityFamily.WEP, SecurityRating.INSECURE, false, "Cifrado WEP obsoleto"),
                arrayOf(SecurityFamily.WPA2_PERSONAL, SecurityRating.MODERATE, false, "WPA2-Personal"),
                arrayOf(SecurityFamily.WPA3_PERSONAL, SecurityRating.HIGH, false, "WPA3-Personal"),
                arrayOf(SecurityFamily.WPA2_WPA3_PERSONAL, SecurityRating.HIGH, true, "WPA2/WPA3-Personal (transición)"),
                arrayOf(SecurityFamily.WPA2_ENTERPRISE, SecurityRating.HIGH, false, "WPA2-Enterprise"),
                arrayOf(SecurityFamily.OWE, SecurityRating.MODERATE, false, "Open enhanced (OWE)"),
                arrayOf(SecurityFamily.UNKNOWN, SecurityRating.LOW, false, "Configuración no reconocida"),
            )
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

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

        val ratingText = ratingLabel(expectedRating)
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText(ratingText).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Resumen").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(ratingText).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(expectedHeadline).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Familia: ${familyLabel(family)}").performScrollTo().assertIsDisplayed()

        if (family == SecurityFamily.WPA2_WPA3_PERSONAL || transition) {
            composeTestRule
                .onNodeWithText("Transición WPA2/WPA3: la protección real depende del cliente que se conecte.")
                .performScrollTo()
                .assertIsDisplayed()
        }

        composeTestRule.onNodeWithText("Recomendaciones").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Mostrar detalles técnicos").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Ocultar detalles técnicos").performScrollTo().assertIsDisplayed()
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
