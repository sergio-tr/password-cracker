package com.wifiauditlab.assessment.domain.audit

import com.wifiauditlab.assessment.domain.connection.CurrentWifiConnection
import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.Ssid
import com.wifiauditlab.assessment.port.CurrentWifiConnectionProvider
import com.wifiauditlab.assessment.port.InMemoryLabModePreferences
import com.wifiauditlab.assessment.port.WifiConnectionMonitor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LabValidationSessionOrchestratorTest {
    @Test
    fun gateDenied_doesNotInvokeAdapter() =
        runTest {
            val adapter = SimulatedValidationAdapter(capabilityValue = ApAuthCapability.Available)
            val harness = harness(adapter = adapter, labMode = false)
            val outcome =
                harness.orchestrator.requestStart(
                    this,
                    harness.startRequest(consent = true),
                )
            assertIs<LabValidationStartOutcome.Denied>(outcome)
            assertTrue(AuthorizedApTestDenial.LabModeDisabled in outcome.reasons)
            assertEquals(0, adapter.invokeCount)
        }

    @Test
    fun gateAllowed_createsSession_andCompletesLifecycle() =
        runTest {
            val adapter =
                SimulatedValidationAdapter(
                    capabilityValue = ApAuthCapability.Available,
                    behavior =
                        SimulatedValidationBehavior.Immediate(NetworkValidationResult.Validated),
                )
            val harness = harness(adapter = adapter)
            val outcome = harness.orchestrator.requestStart(this, harness.startRequest())
            assertIs<LabValidationStartOutcome.Started>(outcome)
            assertEquals(LabValidationSessionState.Admitted, outcome.session.state)

            val terminal = awaitTerminal(harness)
            assertEquals(LabValidationSessionState.Completed, terminal.state)
            assertEquals(LabSessionTerminationReason.Completed, terminal.terminationReason)
            assertEquals(1, adapter.invokeCount)
            val states =
                harness.orchestrator
                    .evidence()
                    .recordsFor(terminal.sessionId)
                    .mapNotNull { it.state }
            assertTrue(LabValidationSessionState.Admitted in states)
            assertTrue(LabValidationSessionState.Running in states)
            assertTrue(LabValidationSessionState.Completed in states)
        }

    @Test
    fun targetAlwaysCurrentNetwork_snapshotMatchesConnection() =
        runTest {
            val adapter =
                SimulatedValidationAdapter(
                    capabilityValue = ApAuthCapability.Available,
                    behavior =
                        SimulatedValidationBehavior.Immediate(NetworkValidationResult.Validated),
                )
            val harness = harness(adapter = adapter)
            val outcome = harness.orchestrator.requestStart(this, harness.startRequest())
            val started = assertIs<LabValidationStartOutcome.Started>(outcome)
            assertEquals("LabNet", started.session.networkSnapshot.ssid)
            assertTrue(started.session.networkSnapshot.matches(harness.connection.value))
            awaitTerminal(harness)
        }

    @Test
    fun userCancel_cancelsAdapter_andSessionCancelled() =
        runTest {
            val adapter =
                SimulatedValidationAdapter(
                    capabilityValue = ApAuthCapability.Available,
                    behavior = SimulatedValidationBehavior.HangUntilCancelled,
                )
            val harness = harness(adapter = adapter)
            assertIs<LabValidationStartOutcome.Started>(
                harness.orchestrator.requestStart(this, harness.startRequest()),
            )
            awaitRunning(harness)
            harness.orchestrator.userCancel()
            val terminal = awaitTerminal(harness)
            assertEquals(LabValidationSessionState.Cancelled, terminal.state)
            assertEquals(LabSessionTerminationReason.UserCancelled, terminal.terminationReason)
        }

    @Test
    fun adapterError_failsSession() =
        runTest {
            val adapter =
                SimulatedValidationAdapter(
                    capabilityValue = ApAuthCapability.Available,
                    behavior =
                        SimulatedValidationBehavior.Immediate(
                            NetworkValidationResult.PlatformError("Boom"),
                        ),
                )
            val harness = harness(adapter = adapter)
            harness.orchestrator.requestStart(this, harness.startRequest())
            val terminal = awaitTerminal(harness)
            assertEquals(LabValidationSessionState.Failed, terminal.state)
            assertEquals(LabSessionTerminationReason.AdapterError, terminal.terminationReason)
        }

    @Test
    fun networkChanged_whileRunning_cancelsAdapter_withNetworkChanged() =
        runTest {
            val adapter =
                SimulatedValidationAdapter(
                    capabilityValue = ApAuthCapability.Available,
                    behavior = SimulatedValidationBehavior.HangUntilCancelled,
                )
            val harness = harness(adapter = adapter)
            assertIs<LabValidationStartOutcome.Started>(
                harness.orchestrator.requestStart(this, harness.startRequest()),
            )
            awaitRunning(harness)

            harness.connection.value =
                CurrentWifiConnection(
                    ssid = Ssid("OtherNet"),
                    bssid = Bssid.of("AA:BB:CC:DD:EE:99"),
                    securityFamily = SecurityFamily.WPA2_PERSONAL,
                    rssi = -50,
                )
            runCurrent()
            val terminal = awaitTerminal(harness)
            assertEquals(LabSessionTerminationReason.NetworkChanged, terminal.terminationReason)
            assertTrue(terminal.state.isTerminal)
        }

    @Test
    fun networkLost_whileRunning_stopsWithNetworkLost() =
        runTest {
            val adapter =
                SimulatedValidationAdapter(
                    capabilityValue = ApAuthCapability.Available,
                    behavior = SimulatedValidationBehavior.HangUntilCancelled,
                )
            val harness = harness(adapter = adapter)
            harness.orchestrator.requestStart(this, harness.startRequest())
            awaitRunning(harness)
            harness.connection.value = null
            runCurrent()
            val terminal = awaitTerminal(harness)
            assertEquals(LabSessionTerminationReason.NetworkLost, terminal.terminationReason)
        }

    @Test
    fun labModeDisabled_whileRunning_cancelsWithLabModeDisabled() =
        runTest {
            val adapter =
                SimulatedValidationAdapter(
                    capabilityValue = ApAuthCapability.Available,
                    behavior = SimulatedValidationBehavior.HangUntilCancelled,
                )
            val harness = harness(adapter = adapter)
            harness.orchestrator.requestStart(this, harness.startRequest())
            awaitRunning(harness)
            harness.labMode.setLabModeEnabled(false)
            advanceTimeBy(LabValidationSessionOrchestrator.INVARIANT_POLL_MS)
            runCurrent()
            val terminal = awaitTerminal(harness)
            assertEquals(LabSessionTerminationReason.LabModeDisabled, terminal.terminationReason)
            assertTrue(terminal.state.isTerminal)
        }

    @Test
    fun registryRevoked_whileRunning_stops() =
        runTest {
            val adapter =
                SimulatedValidationAdapter(
                    capabilityValue = ApAuthCapability.Available,
                    behavior = SimulatedValidationBehavior.HangUntilCancelled,
                )
            val harness = harness(adapter = adapter)
            harness.orchestrator.requestStart(this, harness.startRequest())
            awaitRunning(harness)
            harness.registry.setAuthorized(
                AuthorizedLabNetworkKey.of("LabNet", SecurityFamily.WPA2_PERSONAL),
                false,
            )
            advanceTimeBy(LabValidationSessionOrchestrator.INVARIANT_POLL_MS)
            runCurrent()
            val terminal = awaitTerminal(harness)
            assertEquals(
                LabSessionTerminationReason.NetworkAuthorizationRevoked,
                terminal.terminationReason,
            )
        }

    @Test
    fun concurrentStart_onlyOneSession_adapterInvokedOnce() =
        runTest {
            val adapter =
                SimulatedValidationAdapter(
                    capabilityValue = ApAuthCapability.Available,
                    behavior = SimulatedValidationBehavior.HangUntilCancelled,
                )
            val harness = harness(adapter = adapter)
            val first =
                async {
                    harness.orchestrator.requestStart(this@runTest, harness.startRequest())
                }
            val second =
                async {
                    harness.orchestrator.requestStart(this@runTest, harness.startRequest())
                }
            val outcomes = listOf(first.await(), second.await())
            val started = outcomes.filterIsInstance<LabValidationStartOutcome.Started>()
            val rejected = outcomes.filterIsInstance<LabValidationStartOutcome.RejectedConcurrent>()
            assertEquals(1, started.size)
            assertEquals(1, rejected.size)
            awaitRunning(harness)
            assertEquals(1, adapter.invokeCount)
            harness.orchestrator.userCancel()
            awaitTerminal(harness)
        }

    @Test
    fun operationTimeout_cancelsExecution_withTimeout() =
        runTest {
            val adapter =
                SimulatedValidationAdapter(
                    capabilityValue = ApAuthCapability.Available,
                    behavior =
                        SimulatedValidationBehavior.DelayThen(
                            delayMs = 60_000,
                            result = NetworkValidationResult.Validated,
                        ),
                )
            val budget =
                LabSessionBudget(
                    maxDurationMs = 120_000,
                    maxOperations = 1,
                    operationTimeoutMs = 50,
                )
            val harness = harness(adapter = adapter, budget = budget)
            harness.orchestrator.requestStart(this, harness.startRequest(budget = budget))
            awaitRunning(harness)
            advanceTimeBy(50)
            runCurrent()
            val terminal = awaitTerminal(harness)
            assertEquals(LabSessionTerminationReason.Timeout, terminal.terminationReason)
        }

    @Test
    fun budgetExhausted_whenMaxDurationElapses() =
        runTest {
            val adapter =
                SimulatedValidationAdapter(
                    capabilityValue = ApAuthCapability.Available,
                    behavior = SimulatedValidationBehavior.HangUntilCancelled,
                )
            val budget =
                LabSessionBudget(
                    maxDurationMs = 40,
                    maxOperations = 1,
                    operationTimeoutMs = 10_000,
                )
            val harness = harness(adapter = adapter, budget = budget)
            harness.orchestrator.requestStart(this, harness.startRequest(budget = budget))
            awaitRunning(harness)
            advanceTimeBy(40)
            runCurrent()
            val terminal = awaitTerminal(harness)
            assertEquals(LabSessionTerminationReason.BudgetExhausted, terminal.terminationReason)
        }

    @Test
    fun secondSession_requiresConsentAgain() =
        runTest {
            val adapter =
                SimulatedValidationAdapter(
                    capabilityValue = ApAuthCapability.Available,
                    behavior =
                        SimulatedValidationBehavior.Immediate(NetworkValidationResult.Validated),
                )
            val harness = harness(adapter = adapter)
            harness.orchestrator.requestStart(this, harness.startRequest(consent = true))
            awaitTerminal(harness)

            val denied =
                harness.orchestrator.requestStart(
                    this,
                    harness.startRequest(consent = false),
                )
            assertIs<LabValidationStartOutcome.Denied>(denied)
            assertTrue(AuthorizedApTestDenial.ConsentRequired in denied.reasons)
            assertEquals(1, adapter.invokeCount)

            val started =
                harness.orchestrator.requestStart(
                    this,
                    harness.startRequest(consent = true),
                )
            assertIs<LabValidationStartOutcome.Started>(started)
            awaitTerminal(harness)
            assertEquals(2, adapter.invokeCount)
        }

    @Test
    fun evidence_andModels_doNotContainSecrets() =
        runTest {
            val secret = "super-secret-lab-password"
            val adapter =
                SimulatedValidationAdapter(
                    capabilityValue = ApAuthCapability.Available,
                    behavior =
                        SimulatedValidationBehavior.Immediate(NetworkValidationResult.Validated),
                )
            val harness = harness(adapter = adapter)
            harness.orchestrator.requestStart(this, harness.startRequest())
            val terminal = awaitTerminal(harness)
            val blob =
                buildString {
                    append(terminal)
                    append(terminal.networkSnapshot)
                    harness.orchestrator.evidence().all().forEach { append(it) }
                    append(
                        AuthorizedValidationContext(
                            sessionId = terminal.sessionId,
                            networkSnapshot = terminal.networkSnapshot,
                            budget = terminal.budget,
                        ),
                    )
                    append(
                        ApAuthProbeRequest(
                            ssid = "LabNet",
                            bssidHint = null,
                            family = SecurityFamily.WPA2_PERSONAL,
                            passphrase = secret,
                        ),
                    )
                }
            assertTrue(!blob.contains(secret))
            assertTrue(blob.contains("••••••••"))
        }

    @Test
    fun androidAdapter_remainsFailClosed_viaOrchestrator() =
        runTest {
            val adapter = AndroidValidationAdapter()
            val harness = harness(adapter = adapter)
            val outcome = harness.orchestrator.requestStart(this, harness.startRequest())
            assertIs<LabValidationStartOutcome.Denied>(outcome)
            assertTrue(AuthorizedApTestDenial.PlatformCapabilityUnavailable in outcome.reasons)
        }

    private class Harness(
        val connection: MutableStateFlow<CurrentWifiConnection?>,
        val labMode: InMemoryLabModePreferences,
        val registry: InMemoryAuthorizedLabNetworkStore,
        val orchestrator: LabValidationSessionOrchestrator,
        val budget: LabSessionBudget,
    ) {
        fun startRequest(
            consent: Boolean = true,
            budget: LabSessionBudget = this.budget,
            passphrase: String = "lab-test-passphrase",
        ) = LabValidationStartRequest(
            requestedSsid = Ssid("LabNet"),
            requestedBssid = Bssid.of("AA:BB:CC:DD:EE:01"),
            securityFamily = SecurityFamily.WPA2_PERSONAL,
            userConsentGranted = consent,
            budget = budget,
            credential = ValidationCredential.fromPlaintext(passphrase),
        )
    }

    private suspend fun awaitRunning(harness: Harness): LabValidationSession =
        harness.orchestrator.session
            .mapNotNull { it }
            .first {
                it.state == LabValidationSessionState.Running || it.state.isTerminal
            }.also {
                assertEquals(LabValidationSessionState.Running, it.state)
            }

    private suspend fun awaitTerminal(harness: Harness): LabValidationSession =
        harness.orchestrator.session
            .mapNotNull { it?.takeIf { s -> s.state.isTerminal } }
            .first()

    private suspend fun TestScope.harness(
        adapter: NetworkValidationAdapter,
        labMode: Boolean = true,
        budget: LabSessionBudget = LabSessionBudget.standard(),
    ): Harness {
        val connection =
            MutableStateFlow<CurrentWifiConnection?>(
                CurrentWifiConnection(
                    ssid = Ssid("LabNet"),
                    bssid = Bssid.of("AA:BB:CC:DD:EE:01"),
                    securityFamily = SecurityFamily.WPA2_PERSONAL,
                    rssi = -40,
                ),
            )
        val prefs = InMemoryLabModePreferences().also { it.setLabModeEnabled(labMode) }
        val registry = InMemoryAuthorizedLabNetworkStore()
        registry.setAuthorized(
            AuthorizedLabNetworkKey.of("LabNet", SecurityFamily.WPA2_PERSONAL),
            true,
        )
        val provider =
            object : CurrentWifiConnectionProvider {
                override suspend fun currentConnection(): CurrentWifiConnection? = connection.value
            }
        val monitor = WifiConnectionMonitor { connection }
        val orchestrator =
            LabValidationSessionOrchestrator(
                connectionProvider = provider,
                connectionMonitor = monitor,
                labModePreferences = prefs,
                registry = registry,
                adapter = adapter,
                evidenceLog = InMemoryLabSessionEvidenceLog(),
                clockMs = { testScheduler.currentTime },
                idFactory = { "lab-test-${testScheduler.currentTime}" },
            )
        return Harness(connection, prefs, registry, orchestrator, budget)
    }
}
