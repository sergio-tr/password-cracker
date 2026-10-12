package com.wifiauditlab.assessment.domain.realaudit

import com.wifiauditlab.assessment.domain.real.RealNetworkReference
import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.ManagementFrameProtection
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.Ssid
import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RealWifiAuditOrchestratorTest {
    @Test
    fun unsupported_verifier_prepare_lands_on_backend_unavailable_without_start() =
        runTest {
            val search = RecordingSearch()
            val orchestrator =
                orchestrator(search, UnsupportedExternalCandidateVerificationPort())

            orchestrator.prepare(network(), profile(SecurityFamily.WPA2_PERSONAL))

            assertIs<RealWifiAuditPhase.BackendUnavailable>(orchestrator.state.value.phase)
            assertIs<RealWifiAuditCapability.Unavailable>(orchestrator.state.value.capability)
            assertEquals(
                RealWifiAuditUnavailableReason.ExternalVerifierUnavailable,
                (orchestrator.state.value.capability as RealWifiAuditCapability.Unavailable).reason,
            )
            assertEquals("Unavailable", orchestrator.state.value.technicalDetails?.externalVerifierLabel)
            assertEquals(0, orchestrator.startInvocationCount)
            assertEquals(0, search.invokeCount)
        }

    @Test
    fun unsupported_verifier_start_is_noop() =
        runTest {
            val search = RecordingSearch()
            val orchestrator =
                orchestrator(search, UnsupportedExternalCandidateVerificationPort())

            orchestrator.prepare(network(), profile(SecurityFamily.WPA2_PERSONAL))
            orchestrator.start()
            orchestrator.start()
            orchestrator.start()

            assertIs<RealWifiAuditPhase.BackendUnavailable>(orchestrator.state.value.phase)
            assertEquals(0, orchestrator.startInvocationCount)
            assertEquals(0, search.invokeCount)
        }

    @Test
    fun available_fake_verifier_prepare_ready() =
        runTest {
            val search = RecordingSearch()
            val orchestrator = orchestrator(search, AvailableAlwaysMatchExternal())

            orchestrator.prepare(network(), profile(SecurityFamily.WPA2_PERSONAL))

            assertIs<RealWifiAuditPhase.Ready>(orchestrator.state.value.phase)
            assertIs<RealWifiAuditCapability.Available>(orchestrator.state.value.capability)
            assertEquals("Available", orchestrator.state.value.technicalDetails?.externalVerifierLabel)
        }

    @Test
    fun available_fake_verifier_start_runs_search() =
        runTest {
            val search =
                RecordingSearch(
                    RealWifiSearchExecutionResult.Found(
                        RealWifiAuditProgress(strategyLabel = "PSK Audit", candidatesProcessed = 3),
                    ),
                )
            val orchestrator = orchestrator(search, AvailableAlwaysMatchExternal())

            orchestrator.prepare(network(), profile(SecurityFamily.WPA2_PERSONAL))
            orchestrator.start()

            assertEquals(1, orchestrator.startInvocationCount)
            assertEquals(1, search.invokeCount)
            val phase = assertIs<RealWifiAuditPhase.Completed>(orchestrator.state.value.phase)
            val outcome = assertIs<RealWifiAuditOutcome.Completed>(phase.outcome)
            assertEquals(RealWifiSearchOutcome.Found, outcome.search)
        }

    @Test
    fun prepare_twice_does_not_emit_completed_unavailable_loop() =
        runTest {
            val search = RecordingSearch()
            val orchestrator =
                orchestrator(search, UnsupportedExternalCandidateVerificationPort())

            orchestrator.prepare(network(), profile(SecurityFamily.WPA2_PERSONAL))
            val first = orchestrator.state.value.phase
            orchestrator.prepare(network(), profile(SecurityFamily.WPA2_PERSONAL))
            val second = orchestrator.state.value.phase

            assertIs<RealWifiAuditPhase.BackendUnavailable>(first)
            assertIs<RealWifiAuditPhase.BackendUnavailable>(second)
            assertTrue(orchestrator.state.value.phase !is RealWifiAuditPhase.Completed)
            assertTrue(orchestrator.state.value.phase !is RealWifiAuditPhase.Preparing)
        }

    @Test
    fun open_network_ready_without_external_verifier() =
        runTest {
            val search = RecordingSearch()
            val orchestrator =
                orchestrator(search, UnsupportedExternalCandidateVerificationPort())

            orchestrator.prepare(network(SecurityFamily.OPEN), profile(SecurityFamily.OPEN))
            assertIs<RealWifiAuditPhase.Ready>(orchestrator.state.value.phase)
            assertEquals("NotRequired", orchestrator.state.value.technicalDetails?.externalVerifierLabel)

            orchestrator.start()
            assertEquals(1, orchestrator.startInvocationCount)
            assertIs<RealWifiAuditOutcome.AssessmentOnly>(
                (orchestrator.state.value.phase as RealWifiAuditPhase.Completed).outcome,
            )
        }

    @Test
    fun unsupported_family_completes_without_search() =
        runTest {
            val search = RecordingSearch()
            val orchestrator =
                orchestrator(search, UnsupportedExternalCandidateVerificationPort())

            orchestrator.prepare(network(SecurityFamily.UNKNOWN), profile(SecurityFamily.UNKNOWN))
            assertEquals(
                RealWifiAuditOutcome.Unsupported,
                (orchestrator.state.value.phase as RealWifiAuditPhase.Completed).outcome,
            )
            assertEquals(0, search.invokeCount)
        }

    private fun orchestrator(
        search: RealWifiSearchPort,
        external: ExternalCandidateVerificationPort,
    ): RealWifiAuditOrchestrator =
        RealWifiAuditOrchestrator(
            strategyResolver = DefaultRealWifiAuditStrategyResolver(),
            planner = DefaultRealWifiAuditPlanner(),
            search = search,
            externalVerifier = external,
        )

    private fun network(family: SecurityFamily = SecurityFamily.WPA2_PERSONAL): RealNetworkReference =
        RealNetworkReference(
            ssid = Ssid("MiRed"),
            bssid = Bssid.of("aa:bb:cc:dd:ee:ff"),
            securityFamily = family,
            displayName = "MiRed",
        )

    private fun profile(family: SecurityFamily): WifiSecurityProfile =
        WifiSecurityProfile(
            family = family,
            keyManagements = emptySet(),
            managementFrameProtection = ManagementFrameProtection.UNKNOWN,
            isTransitionMode = false,
            rawCapabilities = null,
        )

    private class RecordingSearch(
        private val result: RealWifiSearchExecutionResult =
            RealWifiSearchExecutionResult.Unavailable(
                RealWifiAuditUnavailableReason.ExternalVerifierUnavailable,
            ),
    ) : RealWifiSearchPort {
        var invokeCount: Int = 0

        override suspend fun search(
            plan: RealWifiAuditPlan,
            progress: RealWifiAuditProgressSink,
        ): RealWifiSearchExecutionResult {
            invokeCount += 1
            return result
        }

        override fun cancel() = Unit
    }

    private class AvailableAlwaysMatchExternal : ExternalCandidateVerificationPort {
        override fun capability(): ExternalVerifierCapability = ExternalVerifierCapability.Available

        override suspend fun verify(candidate: String): ExternalVerificationResult =
            ExternalVerificationResult.Match

        override fun cancel() = Unit
    }
}
