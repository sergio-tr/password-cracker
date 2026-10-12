package com.wifiauditlab.assessment.domain.realaudit

import com.wifiauditlab.assessment.domain.real.RealNetworkReference
import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.ManagementFrameProtection
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.Ssid
import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RealWifiAuditOrchestratorTest {
    @Test
    fun start_passes_selected_network_profile_strategy_and_plan_to_backend() =
        runTest {
            val backend = RecordingBackend()
            val orchestrator =
                RealWifiAuditOrchestrator(
                    strategyResolver = DefaultRealWifiAuditStrategyResolver(),
                    planner = DefaultRealWifiAuditPlanner(),
                    backend = backend,
                )
            val network =
                RealNetworkReference(
                    ssid = Ssid("MiRed"),
                    bssid = Bssid.of("aa:bb:cc:dd:ee:ff"),
                    securityFamily = SecurityFamily.WPA2_PERSONAL,
                    displayName = "MiRed",
                )
            val profile = profile(SecurityFamily.WPA2_PERSONAL)

            orchestrator.prepare(network, profile)
            assertIs<RealWifiAuditPhase.Ready>(orchestrator.state.value.phase)
            assertEquals(RealWifiAuditStrategy.PersonalPskAudit, orchestrator.state.value.strategy)

            orchestrator.start()

            val received = assertNotNull(backend.lastPlan)
            assertEquals(network, received.network)
            assertEquals(profile, received.securityProfile)
            assertEquals(RealWifiAuditStrategy.PersonalPskAudit, received.strategy)
            assertEquals("PSK Audit", received.strategyLabel)
            assertIs<RealWifiAuditPhase.Completed>(orchestrator.state.value.phase)
            assertEquals(
                RealWifiAuditOutcome.Completed,
                (orchestrator.state.value.phase as RealWifiAuditPhase.Completed).outcome,
            )
        }

    @Test
    fun stop_cancels_backend_and_ends_cancelled() =
        runTest {
            val backend = SlowCancellableBackend()
            val orchestrator =
                RealWifiAuditOrchestrator(
                    strategyResolver = DefaultRealWifiAuditStrategyResolver(),
                    planner = DefaultRealWifiAuditPlanner(),
                    backend = backend,
                )
            orchestrator.prepare(
                RealNetworkReference(
                    ssid = Ssid("Lab"),
                    bssid = null,
                    securityFamily = SecurityFamily.WPA3_PERSONAL,
                    displayName = "Lab",
                ),
                profile(SecurityFamily.WPA3_PERSONAL),
            )

            val started = async { orchestrator.start() }
            backend.entered.await()
            assertIs<RealWifiAuditPhase.Running>(orchestrator.state.value.phase)

            orchestrator.stop()
            assertIs<RealWifiAuditPhase.Stopping>(orchestrator.state.value.phase)
            assertTrue(backend.cancelCalled)

            started.await()
            val phase = orchestrator.state.value.phase
            assertIs<RealWifiAuditPhase.Completed>(phase)
            assertEquals(RealWifiAuditOutcome.Cancelled, phase.outcome)
        }

    @Test
    fun unsupported_family_completes_without_calling_backend() =
        runTest {
            val backend = RecordingBackend()
            val orchestrator =
                RealWifiAuditOrchestrator(
                    strategyResolver = DefaultRealWifiAuditStrategyResolver(),
                    planner = DefaultRealWifiAuditPlanner(),
                    backend = backend,
                )
            orchestrator.prepare(
                RealNetworkReference(
                    ssid = Ssid("X"),
                    bssid = null,
                    securityFamily = SecurityFamily.UNKNOWN,
                    displayName = "X",
                ),
                profile(SecurityFamily.UNKNOWN),
            )
            assertEquals(
                RealWifiAuditOutcome.Unsupported,
                (orchestrator.state.value.phase as RealWifiAuditPhase.Completed).outcome,
            )
            orchestrator.start()
            assertEquals(null, backend.lastPlan)
        }

    private fun profile(family: SecurityFamily): WifiSecurityProfile =
        WifiSecurityProfile(
            family = family,
            keyManagements = emptySet(),
            managementFrameProtection = ManagementFrameProtection.UNKNOWN,
            isTransitionMode = false,
            rawCapabilities = null,
        )

    private class RecordingBackend(
        private val result: RealWifiAuditBackendResult = RealWifiAuditBackendResult.Completed,
    ) : RealWifiAuditBackend {
        var lastPlan: RealWifiAuditPlan? = null
        var cancelCalled: Boolean = false

        override suspend fun execute(
            plan: RealWifiAuditPlan,
            progress: RealWifiAuditProgressSink,
        ): RealWifiAuditBackendResult {
            lastPlan = plan
            return result
        }

        override fun cancel() {
            cancelCalled = true
        }
    }

    private class SlowCancellableBackend : RealWifiAuditBackend {
        val entered = CompletableDeferred<Unit>()
        var cancelCalled: Boolean = false

        @Volatile
        private var cancelled: Boolean = false

        override suspend fun execute(
            plan: RealWifiAuditPlan,
            progress: RealWifiAuditProgressSink,
        ): RealWifiAuditBackendResult {
            entered.complete(Unit)
            repeat(50) {
                if (cancelled) return RealWifiAuditBackendResult.Cancelled
                delay(10)
            }
            return RealWifiAuditBackendResult.Completed
        }

        override fun cancel() {
            cancelCalled = true
            cancelled = true
        }
    }
}
