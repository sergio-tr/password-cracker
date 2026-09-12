package com.wifiauditlab.android.ui.security

import com.wifiauditlab.assessment.application.AssessNetworkSecurity
import com.wifiauditlab.assessment.domain.security.SecurityAssessmentRegistry
import com.wifiauditlab.assessment.domain.security.SecurityRating
import com.wifiauditlab.assessment.domain.wifi.ManagementFrameProtection
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@OptIn(ExperimentalCoroutinesApi::class)
class SecurityAnalysisViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val assess = AssessNetworkSecurity(SecurityAssessmentRegistry.default())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun missingTargetShowsEmptyState() =
        runTest {
            val store = SecurityAnalysisTargetStore()
            val vm = SecurityAnalysisViewModel(store, assess)
            assertTrue(vm.state.value.missingTarget)
            assertFalse(vm.state.value.loading)
        }

    @Test
    fun loadsAssessmentSummaryAndRecommendations() =
        runTest {
            val store = SecurityAnalysisTargetStore()
            store.set(
                SecurityAnalysisRequest(
                    displayName = "Casa",
                    ssidLabel = "WIFI_HOME",
                    profile = profile(SecurityFamily.WPA3_PERSONAL),
                ),
            )
            val vm = SecurityAnalysisViewModel(store, assess)
            val state = vm.state.value
            assertFalse(state.loading)
            assertEquals("Casa", state.displayName)
            assertNotNull(state.assessment)
            assertEquals(SecurityRating.HIGH, state.assessment!!.rating)
            assertNotNull(state.authentication)
            assertTrue(state.recommendations.isNotEmpty())
            assertFalse(state.technicalExpanded)
            vm.toggleTechnicalDetails()
            assertTrue(vm.state.value.technicalExpanded)
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class SecurityAnalysisFamiliesTest(
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

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun assessmentAndAuthSummaryMatchFamily() =
        runTest {
            val store = SecurityAnalysisTargetStore()
            store.set(
                SecurityAnalysisRequest(
                    displayName = family.name,
                    ssidLabel = family.name,
                    profile = profile(family, transition),
                ),
            )
            val vm = SecurityAnalysisViewModel(store, AssessNetworkSecurity(SecurityAssessmentRegistry.default()))
            val state = vm.state.value
            assertEquals(expectedRating, state.assessment!!.rating)
            assertEquals(familyLabel(family), state.authentication!!.familyLabel)
            if (family == SecurityFamily.WPA2_WPA3_PERSONAL || transition) {
                assertNotNull(state.authentication!!.transitionLabel)
            }
            assertTrue(state.recommendations.isNotEmpty())
            assertFalse(
                state.recommendations.any {
                    it.contains("genera", ignoreCase = true) ||
                        it.contains("candidate", ignoreCase = true) ||
                        it.contains("laboratorio", ignoreCase = true)
                },
            )
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
