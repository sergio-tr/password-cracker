package com.wifiauditlab.android.ui.realaudit

import com.wifiauditlab.assessment.domain.real.RealNetworkReference
import com.wifiauditlab.assessment.domain.realaudit.DefaultRealWifiAuditPlanner
import com.wifiauditlab.assessment.domain.realaudit.DefaultRealWifiAuditStrategyResolver
import com.wifiauditlab.assessment.domain.realaudit.ExternalCandidateVerificationPort
import com.wifiauditlab.assessment.domain.realaudit.ExternalVerificationResult
import com.wifiauditlab.assessment.domain.realaudit.ExternalVerifierCapability
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditCapability
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditOrchestrator
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditOutcome
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditPhase
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditPlan
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditProgress
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditProgressSink
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditUnavailableReason
import com.wifiauditlab.assessment.domain.realaudit.RealWifiSearchExecutionResult
import com.wifiauditlab.assessment.domain.realaudit.RealWifiSearchOutcome
import com.wifiauditlab.assessment.domain.realaudit.RealWifiSearchPort
import com.wifiauditlab.assessment.domain.realaudit.UnsupportedExternalCandidateVerificationPort
import com.wifiauditlab.assessment.domain.wifi.ManagementFrameProtection
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.Ssid
import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RealWifiAuditEndToEndTest {
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
    fun unsupported_verifier_backend_unavailable_start_disabled() =
        runTest(dispatcher) {
            val search = FakeSearch()
            val store = RealWifiAuditTargetStore()
            store.set(
                RealWifiAuditTarget(
                    network = network(),
                    securityProfile = profile(),
                ),
            )
            val orchestrator =
                RealWifiAuditOrchestrator(
                    strategyResolver = DefaultRealWifiAuditStrategyResolver(),
                    planner = DefaultRealWifiAuditPlanner(),
                    search = search,
                    externalVerifier = UnsupportedExternalCandidateVerificationPort(),
                )
            val vm = RealWifiAuditViewModel(store, orchestrator)

            assertTrue(vm.state.value.phase is RealWifiAuditPhase.BackendUnavailable)
            assertFalse(vm.state.value.canStart)
            assertTrue(vm.state.value.showBackendUnavailableCard)

            vm.start()
            vm.start()

            assertEquals(0, orchestrator.startInvocationCount)
            assertEquals(0, search.invokeCount)
            assertTrue(vm.state.value.phase is RealWifiAuditPhase.BackendUnavailable)
        }

    @Test
    fun available_fake_verifier_ready_start_enabled() =
        runTest(dispatcher) {
            val search =
                FakeSearch(
                    RealWifiSearchExecutionResult.Found(
                        RealWifiAuditProgress(strategyLabel = "PSK Audit", candidatesProcessed = 7),
                    ),
                )
            val store = RealWifiAuditTargetStore()
            store.set(RealWifiAuditTarget(network(), profile()))
            val orchestrator =
                RealWifiAuditOrchestrator(
                    strategyResolver = DefaultRealWifiAuditStrategyResolver(),
                    planner = DefaultRealWifiAuditPlanner(),
                    search = search,
                    externalVerifier = AvailableExternal(),
                )
            val vm = RealWifiAuditViewModel(store, orchestrator)

            assertTrue(vm.state.value.phase is RealWifiAuditPhase.Ready)
            assertTrue(vm.state.value.canStart)
            assertTrue(vm.state.value.capability is RealWifiAuditCapability.Available)
            assertFalse(vm.state.value.showBackendUnavailableCard)

            vm.start()
            assertEquals(1, orchestrator.startInvocationCount)
            assertEquals(1, search.invokeCount)
        }

    @Test
    fun recomposition_equivalent_prepare_does_not_create_completed_error() =
        runTest(dispatcher) {
            val search = FakeSearch()
            val orchestrator =
                RealWifiAuditOrchestrator(
                    strategyResolver = DefaultRealWifiAuditStrategyResolver(),
                    planner = DefaultRealWifiAuditPlanner(),
                    search = search,
                    externalVerifier = UnsupportedExternalCandidateVerificationPort(),
                )
            orchestrator.prepare(network(), profile())
            orchestrator.prepare(network(), profile())
            assertTrue(orchestrator.state.value.phase is RealWifiAuditPhase.BackendUnavailable)
            assertFalse(orchestrator.state.value.phase is RealWifiAuditPhase.Completed)
        }

    @Test
    fun exhausted_with_available_verifier() =
        runTest(dispatcher) {
            val search =
                FakeSearch(
                    RealWifiSearchExecutionResult.Exhausted(
                        RealWifiAuditProgress(strategyLabel = "PSK Audit"),
                    ),
                )
            val orchestrator =
                RealWifiAuditOrchestrator(
                    strategyResolver = DefaultRealWifiAuditStrategyResolver(),
                    planner = DefaultRealWifiAuditPlanner(),
                    search = search,
                    externalVerifier = AvailableExternal(),
                )
            orchestrator.prepare(network(), profile())
            orchestrator.start()
            val outcome =
                (orchestrator.state.value.phase as RealWifiAuditPhase.Completed).outcome
                    as RealWifiAuditOutcome.Completed
            assertEquals(RealWifiSearchOutcome.Exhausted, outcome.search)
        }

    private fun network() =
        RealNetworkReference(
            ssid = Ssid("Home"),
            bssid = null,
            securityFamily = SecurityFamily.WPA2_PERSONAL,
            displayName = "Home",
        )

    private fun profile() =
        WifiSecurityProfile(
            family = SecurityFamily.WPA2_PERSONAL,
            keyManagements = emptySet(),
            managementFrameProtection = ManagementFrameProtection.UNKNOWN,
            isTransitionMode = false,
            rawCapabilities = null,
        )

    private class FakeSearch(
        private val result: RealWifiSearchExecutionResult =
            RealWifiSearchExecutionResult.Unavailable(
                RealWifiAuditUnavailableReason.ExternalVerifierUnavailable,
            ),
    ) : RealWifiSearchPort {
        var invokeCount = 0

        override suspend fun search(
            plan: RealWifiAuditPlan,
            progress: RealWifiAuditProgressSink,
        ): RealWifiSearchExecutionResult {
            invokeCount += 1
            return result
        }

        override fun cancel() = Unit
    }

    private class AvailableExternal : ExternalCandidateVerificationPort {
        override fun capability(): ExternalVerifierCapability = ExternalVerifierCapability.Available

        override suspend fun verify(candidate: String): ExternalVerificationResult =
            ExternalVerificationResult.Match

        override fun cancel() = Unit
    }
}
