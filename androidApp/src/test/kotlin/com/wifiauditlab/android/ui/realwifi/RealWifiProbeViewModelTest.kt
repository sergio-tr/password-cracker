package com.wifiauditlab.android.ui.realwifi

import com.wifiauditlab.assessment.domain.audit.ApAuthCapability
import com.wifiauditlab.assessment.domain.audit.AuthorizedApTestGate
import com.wifiauditlab.assessment.domain.audit.FixedLabAccessPoint
import com.wifiauditlab.assessment.domain.audit.FixedLabAccessPointProvider
import com.wifiauditlab.assessment.domain.audit.InMemoryAuthorizedLabNetworkStore
import com.wifiauditlab.assessment.domain.audit.InMemoryLabSessionEvidenceLog
import com.wifiauditlab.assessment.domain.audit.LabValidationSessionOrchestrator
import com.wifiauditlab.assessment.domain.audit.LabValidationSessionState
import com.wifiauditlab.assessment.domain.audit.NetworkValidationResult
import com.wifiauditlab.assessment.domain.audit.PlatformApAuthCapabilityProvider
import com.wifiauditlab.assessment.domain.audit.SimulatedValidationAdapter
import com.wifiauditlab.assessment.domain.audit.SimulatedValidationBehavior
import com.wifiauditlab.assessment.domain.connection.CurrentWifiConnection
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.Ssid
import com.wifiauditlab.assessment.port.CurrentWifiConnectionProvider
import com.wifiauditlab.assessment.port.LabModePreferences
import com.wifiauditlab.assessment.port.WifiConnectionMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RealWifiProbeViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val fixed =
        FixedLabAccessPoint(
            ssid = "WIFI_LAB_SERGIO",
            bssid = "AA:BB:CC:DD:EE:FF",
            securityFamily = SecurityFamily.WPA2_PERSONAL,
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun featureOff_unavailable() =
        runTest {
            val vm = viewModel(featureCompiledIn = false, fixedLab = fixed)
            advanceUntilIdle()
            assertFalse(vm.state.value.featureCompiledIn)
            assertFalse(vm.state.value.canRequestStart)
        }

    @Test
    fun noConfig_cannotStart() =
        runTest {
            val vm = viewModel(featureCompiledIn = true, fixedLab = null)
            advanceUntilIdle()
            assertEquals(null, vm.state.value.fixedLab)
            assertFalse(vm.state.value.canRequestStart)
        }

    @Test
    fun readyState_canRequestStart() =
        runTest {
            val vm = viewModel()
            advanceUntilIdle()
            vm.onPasswordChanged("correct-password")
            assertTrue(vm.state.value.canRequestStart)
        }

    @Test
    fun consentFlow_oneShot_validated() =
        runTest {
            val adapter =
                SimulatedValidationAdapter(
                    capabilityValue = ApAuthCapability.Available,
                    behavior =
                        SimulatedValidationBehavior.Immediate(NetworkValidationResult.Validated),
                )
            val vm = viewModel(adapter = adapter)
            advanceUntilIdle()
            vm.onPasswordChanged("correct-password")
            vm.requestStart()
            assertTrue(vm.state.value.showConsentDialog)
            vm.setConsentChecked(true)
            vm.confirmConsentAndStart()
            advanceUntilIdle()
            assertEquals(1, adapter.invokeCount)
            assertEquals(RealWifiProbePhase.Completed, vm.state.value.phase)
            assertEquals(RealWifiProbeUiOutcome.Validated, vm.state.value.outcome)
            assertFalse(vm.state.value.showConsentDialog)
        }

    @Test
    fun requestUnavailable_mapsToInconclusive_notWrongPassword() =
        runTest {
            val adapter =
                SimulatedValidationAdapter(
                    capabilityValue = ApAuthCapability.Available,
                    behavior =
                        SimulatedValidationBehavior.Immediate(
                            NetworkValidationResult.RequestUnavailable,
                        ),
                )
            val vm = viewModel(adapter = adapter)
            advanceUntilIdle()
            vm.onPasswordChanged("wrong-password")
            vm.requestStart()
            vm.setConsentChecked(true)
            vm.confirmConsentAndStart()
            advanceUntilIdle()
            assertEquals(RealWifiProbeUiOutcome.Inconclusive, vm.state.value.outcome)
            assertEquals("RequestUnavailable", vm.state.value.resultDetail)
        }

    @Test
    fun stopWhileRunning_cancelled() =
        runTest {
            val adapter =
                SimulatedValidationAdapter(
                    capabilityValue = ApAuthCapability.Available,
                    behavior = SimulatedValidationBehavior.HangUntilCancelled,
                )
            val harness = harness(adapter)
            val vm = harness.vm
            advanceUntilIdle()
            vm.onPasswordChanged("correct-password")
            vm.requestStart()
            vm.setConsentChecked(true)
            vm.confirmConsentAndStart()
            // Do not advanceUntilIdle: budget timeouts would fire under virtual time.
            runCurrent()
            assertEquals(RealWifiProbePhase.Running, vm.state.value.phase)
            vm.stop()
            runCurrent()
            assertEquals(RealWifiProbePhase.Cancelled, vm.state.value.phase)
            val session = harness.orchestrator.session.value
            assertEquals(LabValidationSessionState.Cancelled, session?.state)
        }

    @Test
    fun doubleStartWhileRunning_secondRejected() =
        runTest {
            val adapter =
                SimulatedValidationAdapter(
                    capabilityValue = ApAuthCapability.Available,
                    behavior = SimulatedValidationBehavior.HangUntilCancelled,
                )
            val vm = viewModel(adapter = adapter)
            advanceUntilIdle()
            vm.onPasswordChanged("correct-password")
            vm.requestStart()
            vm.setConsentChecked(true)
            vm.confirmConsentAndStart()
            runCurrent()
            assertEquals(RealWifiProbePhase.Running, vm.state.value.phase)
            assertFalse(vm.state.value.canRequestStart)
            vm.requestStart()
            assertFalse(vm.state.value.showConsentDialog)
            assertEquals(1, adapter.invokeCount)
            vm.stop()
            runCurrent()
        }

    private fun viewModel(
        featureCompiledIn: Boolean = true,
        fixedLab: FixedLabAccessPoint? = fixed,
        adapter: SimulatedValidationAdapter =
            SimulatedValidationAdapter(
                capabilityValue = ApAuthCapability.Available,
                behavior =
                    SimulatedValidationBehavior.Immediate(NetworkValidationResult.Validated),
            ),
        connected: Boolean = true,
        labMode: Boolean = true,
    ): RealWifiProbeViewModel = harness(adapter, featureCompiledIn, fixedLab, connected, labMode).vm

    private fun harness(
        adapter: SimulatedValidationAdapter,
        featureCompiledIn: Boolean = true,
        fixedLab: FixedLabAccessPoint? = fixed,
        connected: Boolean = true,
        labMode: Boolean = true,
    ): Harness {
        val connection =
            MutableStateFlow(
                if (connected && fixedLab != null) {
                    CurrentWifiConnection(
                        ssid = Ssid(fixedLab.ssidNormalized),
                        bssid = fixedLab.bssidNormalized,
                        securityFamily = fixedLab.securityFamily,
                        rssi = -40,
                    )
                } else {
                    null
                },
            )
        val prefs = FakeLabModePrefs(labMode)
        val orchestrator =
            LabValidationSessionOrchestrator(
                gate = AuthorizedApTestGate(),
                connectionProvider =
                    object : CurrentWifiConnectionProvider {
                        override suspend fun currentConnection(): CurrentWifiConnection? = connection.value
                    },
                connectionMonitor = WifiConnectionMonitor { connection },
                labModePreferences = prefs,
                registry = InMemoryAuthorizedLabNetworkStore(),
                adapter = adapter,
                evidenceLog = InMemoryLabSessionEvidenceLog(),
            )
        val vm =
            RealWifiProbeViewModel(
                featureCompiledIn = featureCompiledIn,
                fixedLabProvider = FixedLabAccessPointProvider { fixedLab },
                labModePreferences = prefs,
                connectionProvider =
                    object : CurrentWifiConnectionProvider {
                        override suspend fun currentConnection(): CurrentWifiConnection? = connection.value
                    },
                capabilityProvider = PlatformApAuthCapabilityProvider { adapter.capability() },
                gate = AuthorizedApTestGate(),
                orchestrator = orchestrator,
            )
        return Harness(vm, orchestrator, adapter)
    }

    private class Harness(
        val vm: RealWifiProbeViewModel,
        val orchestrator: LabValidationSessionOrchestrator,
        val adapter: SimulatedValidationAdapter,
    )

    private class FakeLabModePrefs(
        initiallyEnabled: Boolean,
    ) : LabModePreferences {
        private val _enabled = MutableStateFlow(initiallyEnabled)
        override val enabled = _enabled

        override fun isLabModeEnabled(): Boolean = _enabled.value

        override fun setLabModeEnabled(enabled: Boolean) {
            _enabled.value = enabled
        }
    }
}
