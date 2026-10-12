package com.wifiauditlab.android.ui.realaudit

import com.wifiauditlab.android.ui.nearby.NearbyItem
import com.wifiauditlab.assessment.domain.realaudit.DefaultRealWifiAuditPlanner
import com.wifiauditlab.assessment.domain.realaudit.DefaultRealWifiAuditStrategyResolver
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditBackend
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditBackendResult
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditOrchestrator
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditOutcome
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditPhase
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditPlan
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditProgress
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditProgressSink
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditStrategy
import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.ManagementFrameProtection
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.Ssid
import com.wifiauditlab.assessment.domain.wifi.WifiBand
import com.wifiauditlab.assessment.domain.wifi.WifiChannel
import com.wifiauditlab.assessment.domain.wifi.WifiObservation
import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile
import com.wifiauditlab.assessment.domain.wifi.WifiSignal
import com.wifiauditlab.assessment.domain.wifi.WifiStandard
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Application-path E2E with [FakeRealWifiAuditBackend]:
 * Nearby selection → store → strategy → plan → orchestrator → progress → result / STOP.
 */
class RealWifiAuditEndToEndTest {
    @Test
    fun nearby_selected_network_runs_through_orchestrator_to_completed() =
        runTest {
            val backend = FakeRealWifiAuditBackend()
            val store = RealWifiAuditTargetStore()
            val orchestrator =
                RealWifiAuditOrchestrator(
                    strategyResolver = DefaultRealWifiAuditStrategyResolver(),
                    planner = DefaultRealWifiAuditPlanner(),
                    backend = backend,
                )

            val item = nearbyItem(ssid = "MiRed", family = SecurityFamily.WPA2_PERSONAL)
            store.set(realWifiAuditTargetFromNearby(item))
            val target = requireNotNull(store.target.value)

            orchestrator.prepare(target.network, target.securityProfile)
            assertTrue(orchestrator.state.value.phase is RealWifiAuditPhase.Ready)
            assertEquals(RealWifiAuditStrategy.PersonalPskAudit, orchestrator.state.value.strategy)
            assertEquals(SecurityFamily.WPA2_PERSONAL, orchestrator.state.value.securityProfile?.family)
            assertEquals(Ssid("MiRed"), orchestrator.state.value.network?.ssid)

            orchestrator.start()

            val plan = requireNotNull(backend.lastPlan)
            assertEquals(target.network, plan.network)
            assertEquals(SecurityFamily.WPA2_PERSONAL, plan.securityProfile.family)
            assertEquals(RealWifiAuditStrategy.PersonalPskAudit, plan.strategy)
            assertTrue(backend.progressEvents.isNotEmpty())
            val completed = orchestrator.state.value.phase as RealWifiAuditPhase.Completed
            assertEquals(RealWifiAuditOutcome.Completed, completed.outcome)
        }

    @Test
    fun start_then_stop_cancels_fake_backend() =
        runTest {
            val backend = FakeRealWifiAuditBackend(delayMillis = 200)
            val orchestrator =
                RealWifiAuditOrchestrator(
                    strategyResolver = DefaultRealWifiAuditStrategyResolver(),
                    planner = DefaultRealWifiAuditPlanner(),
                    backend = backend,
                )
            val item = nearbyItem(ssid = "StopMe", family = SecurityFamily.WPA3_PERSONAL)
            val target = realWifiAuditTargetFromNearby(item)
            orchestrator.prepare(target.network, target.securityProfile)
            assertEquals(RealWifiAuditStrategy.PersonalSaeAudit, orchestrator.state.value.strategy)

            val job = async { orchestrator.start() }
            backend.entered.await()
            assertTrue(orchestrator.state.value.phase is RealWifiAuditPhase.Running)

            orchestrator.stop()
            assertTrue(backend.cancelCalled)
            job.await()

            val phase = orchestrator.state.value.phase as RealWifiAuditPhase.Completed
            assertEquals(RealWifiAuditOutcome.Cancelled, phase.outcome)
        }

    private fun nearbyItem(
        ssid: String,
        family: SecurityFamily,
    ): NearbyItem {
        val profile =
            WifiSecurityProfile(
                family = family,
                keyManagements = emptySet(),
                managementFrameProtection = ManagementFrameProtection.UNKNOWN,
                isTransitionMode = false,
                rawCapabilities = null,
            )
        return NearbyItem(
            observation =
                WifiObservation(
                    ssid = Ssid(ssid),
                    bssid = Bssid.of("aa:bb:cc:dd:ee:01"),
                    signal = WifiSignal(-48),
                    channel = WifiChannel(6, WifiBand.GHZ_2_4, 2437),
                    standard = WifiStandard.WIFI_5,
                    securityProfile = profile,
                    observedAtEpochMillis = 1L,
                ),
            alias = ssid,
            savedNetworkId = null,
            isKnown = false,
            ambiguous = false,
        )
    }
}

class FakeRealWifiAuditBackend(
    private val delayMillis: Long = 0,
) : RealWifiAuditBackend {
    var lastPlan: RealWifiAuditPlan? = null
    var cancelCalled: Boolean = false
    val progressEvents = mutableListOf<RealWifiAuditProgress>()
    val entered = CompletableDeferred<Unit>()

    @Volatile
    private var cancelled: Boolean = false

    override suspend fun execute(
        plan: RealWifiAuditPlan,
        progress: RealWifiAuditProgressSink,
    ): RealWifiAuditBackendResult {
        lastPlan = plan
        cancelled = false
        progress.onProgress(
            RealWifiAuditProgress(
                strategyLabel = plan.strategyLabel,
                candidatesProcessed = 0,
                elapsedMillis = 0,
                workerCount = plan.limits.workerCountHint,
                statusMessage = "Prepared",
            ).also { progressEvents.add(it) },
        )
        entered.complete(Unit)
        if (delayMillis > 0) {
            val steps = (delayMillis / 10).coerceAtLeast(1)
            repeat(steps.toInt()) {
                if (cancelled) return RealWifiAuditBackendResult.Cancelled
                delay(10)
                progress.onProgress(
                    RealWifiAuditProgress(
                        strategyLabel = plan.strategyLabel,
                        candidatesProcessed = it.toLong() + 1,
                        elapsedMillis = (it + 1) * 10L,
                        workerCount = plan.limits.workerCountHint,
                    ).also { event -> progressEvents.add(event) },
                )
            }
        }
        if (cancelled) return RealWifiAuditBackendResult.Cancelled
        progress.onProgress(
            RealWifiAuditProgress(
                strategyLabel = plan.strategyLabel,
                candidatesProcessed = progressEvents.lastOrNull()?.candidatesProcessed ?: 1,
                elapsedMillis = progressEvents.lastOrNull()?.elapsedMillis ?: 1,
                workerCount = plan.limits.workerCountHint,
                statusMessage = "Done",
            ).also { progressEvents.add(it) },
        )
        return RealWifiAuditBackendResult.Completed
    }

    override fun cancel() {
        cancelCalled = true
        cancelled = true
    }
}
