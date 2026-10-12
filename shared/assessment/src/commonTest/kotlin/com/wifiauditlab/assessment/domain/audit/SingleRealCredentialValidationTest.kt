package com.wifiauditlab.assessment.domain.audit

import com.wifiauditlab.assessment.domain.connection.CurrentWifiConnection
import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.Ssid
import com.wifiauditlab.assessment.port.CurrentWifiConnectionProvider
import com.wifiauditlab.assessment.port.InMemoryLabModePreferences
import com.wifiauditlab.assessment.port.WifiConnectionMonitor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Gate + orchestrator invariants for [VerificationMode.SINGLE_REAL_CREDENTIAL_VALIDATION].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SingleRealCredentialValidationTest {
    private val gate = AuthorizedApTestGate()
    private val fixed =
        FixedLabAccessPoint(
            ssid = "WIFI_LAB_SERGIO",
            bssid = "AA:BB:CC:DD:EE:FF",
            securityFamily = SecurityFamily.WPA2_PERSONAL,
        )

    private fun singleRealRequest(
        fixedLab: FixedLabAccessPoint? = fixed,
        connectionSsid: String = "WIFI_LAB_SERGIO",
        connectionBssid: String = "AA:BB:CC:DD:EE:FF",
        requestSsid: String = "WIFI_LAB_SERGIO",
        requestBssid: String = "AA:BB:CC:DD:EE:FF",
        labMode: Boolean = true,
        consent: Boolean = true,
        capability: ApAuthCapability = ApAuthCapability.Available,
        connection: CurrentWifiConnection? =
            CurrentWifiConnection(
                ssid = Ssid(connectionSsid),
                bssid = Bssid.of(connectionBssid),
                securityFamily = SecurityFamily.WPA2_PERSONAL,
                rssi = -40,
            ),
    ) = AuthorizedApTestGateRequest(
        mode = VerificationMode.SINGLE_REAL_CREDENTIAL_VALIDATION,
        labModeEnabled = labMode,
        requestedSsid = Ssid(requestSsid),
        requestedBssid = Bssid.of(requestBssid),
        securityFamily = SecurityFamily.WPA2_PERSONAL,
        connection = connection,
        labAuthorized = fixedLab != null,
        userConsentGranted = consent,
        capability = capability,
        fixedLabAccessPoint = fixedLab,
    )

    @Test
    fun exactSsidAndBssid_eligible() {
        assertIs<AuthorizedApTestGateResult.Admitted>(gate.evaluate(singleRealRequest()))
    }

    @Test
    fun sameSsidDifferentBssid_denied() {
        val denied =
            assertIs<AuthorizedApTestGateResult.Denied>(
                gate.evaluate(singleRealRequest(connectionBssid = "11:22:33:44:55:66")),
            )
        assertTrue(AuthorizedApTestDenial.FixedLabTargetMismatch in denied.reasons)
    }

    @Test
    fun differentSsid_denied() {
        val denied =
            assertIs<AuthorizedApTestGateResult.Denied>(
                gate.evaluate(
                    singleRealRequest(
                        connectionSsid = "OTHER",
                        requestSsid = "OTHER",
                    ),
                ),
            )
        assertTrue(AuthorizedApTestDenial.FixedLabTargetMismatch in denied.reasons)
    }

    @Test
    fun noFixedLabConfig_denied() {
        val denied =
            assertIs<AuthorizedApTestGateResult.Denied>(
                gate.evaluate(singleRealRequest(fixedLab = null)),
            )
        assertEquals(listOf(AuthorizedApTestDenial.FixedLabNotConfigured), denied.reasons)
    }

    @Test
    fun oneShot_adapterInvokedOnce() =
        runTest {
            val adapter =
                SimulatedValidationAdapter(
                    capabilityValue = ApAuthCapability.Available,
                    behavior =
                        SimulatedValidationBehavior.Immediate(NetworkValidationResult.Validated),
                )
            val harness = harness(adapter)
            val outcome =
                harness.orchestrator.requestStart(
                    this,
                    harness.startRequest(),
                )
            assertIs<LabValidationStartOutcome.Started>(outcome)
            val terminal = awaitTerminal(harness)
            assertEquals(LabValidationSessionState.Completed, terminal.state)
            assertEquals(1, adapter.invokeCount)
            assertEquals("Validated", terminal.resultSummary)
        }

    @Test
    fun secondStartWhileRunning_rejectedConcurrent() =
        runTest {
            val adapter =
                SimulatedValidationAdapter(
                    capabilityValue = ApAuthCapability.Available,
                    behavior = SimulatedValidationBehavior.HangUntilCancelled,
                )
            val harness = harness(adapter)
            assertIs<LabValidationStartOutcome.Started>(
                harness.orchestrator.requestStart(this, harness.startRequest()),
            )
            awaitRunning(harness)
            val second =
                harness.orchestrator.requestStart(
                    this,
                    harness.startRequest(passphrase = "other-passphrase"),
                )
            assertIs<LabValidationStartOutcome.RejectedConcurrent>(second)
            assertEquals(1, adapter.invokeCount)
            harness.orchestrator.userCancel()
            awaitTerminal(harness)
        }

    @Test
    fun stop_cancelsAndCleansUp() =
        runTest {
            val adapter =
                SimulatedValidationAdapter(
                    capabilityValue = ApAuthCapability.Available,
                    behavior = SimulatedValidationBehavior.HangUntilCancelled,
                )
            val harness = harness(adapter)
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
    fun requestUnavailable_isNotWrongPassword() =
        runTest {
            val adapter =
                SimulatedValidationAdapter(
                    capabilityValue = ApAuthCapability.Available,
                    behavior =
                        SimulatedValidationBehavior.Immediate(
                            NetworkValidationResult.RequestUnavailable,
                        ),
                )
            val harness = harness(adapter)
            harness.orchestrator.requestStart(this, harness.startRequest())
            val terminal = awaitTerminal(harness)
            assertEquals("RequestUnavailable", terminal.resultSummary)
            assertTrue(terminal.resultSummary != "AuthenticationRejected")
            assertTrue(terminal.resultSummary?.contains("WrongPassword") != true)
        }

    @Test
    fun secrets_doNotAppearInEvidenceOrToString() =
        runTest {
            val secret = "super-secret-lab-password"
            val adapter =
                SimulatedValidationAdapter(
                    capabilityValue = ApAuthCapability.Available,
                    behavior =
                        SimulatedValidationBehavior.Immediate(NetworkValidationResult.Validated),
                )
            val harness = harness(adapter)
            harness.orchestrator.requestStart(this, harness.startRequest(passphrase = secret))
            val terminal = awaitTerminal(harness)
            val blob =
                buildString {
                    append(terminal)
                    append(terminal.resultSummary)
                    harness.orchestrator.evidence().all().forEach { append(it) }
                    append(ValidationCredential.fromPlaintext(secret))
                }
            assertTrue(!blob.contains(secret))
            assertTrue(blob.contains("••••••••"))
        }

    private class Harness(
        val connection: MutableStateFlow<CurrentWifiConnection?>,
        val orchestrator: LabValidationSessionOrchestrator,
        private val fixed: FixedLabAccessPoint,
    ) {
        fun startRequest(passphrase: String = "lab-test-passphrase") =
            LabValidationStartRequest(
                requestedSsid = Ssid(fixed.ssidNormalized),
                requestedBssid = fixed.bssidNormalized,
                securityFamily = fixed.securityFamily,
                userConsentGranted = true,
                budget = LabSessionBudget.standard(),
                credential = ValidationCredential.fromPlaintext(passphrase),
                verificationMode = VerificationMode.SINGLE_REAL_CREDENTIAL_VALIDATION,
                fixedLabAccessPoint = fixed,
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

    private fun TestScope.harness(adapter: NetworkValidationAdapter): Harness {
        val connection =
            MutableStateFlow<CurrentWifiConnection?>(
                CurrentWifiConnection(
                    ssid = Ssid(fixed.ssidNormalized),
                    bssid = fixed.bssidNormalized,
                    securityFamily = fixed.securityFamily,
                    rssi = -40,
                ),
            )
        val prefs = InMemoryLabModePreferences().also { it.setLabModeEnabled(true) }
        val registry = InMemoryAuthorizedLabNetworkStore()
        val provider =
            object : CurrentWifiConnectionProvider {
                override suspend fun currentConnection(): CurrentWifiConnection? = connection.value
            }
        val orchestrator =
            LabValidationSessionOrchestrator(
                connectionProvider = provider,
                connectionMonitor = WifiConnectionMonitor { connection },
                labModePreferences = prefs,
                registry = registry,
                adapter = adapter,
                evidenceLog = InMemoryLabSessionEvidenceLog(),
                clockMs = { testScheduler.currentTime },
                idFactory = { "single-real-${testScheduler.currentTime}" },
            )
        return Harness(connection, orchestrator, fixed)
    }
}
