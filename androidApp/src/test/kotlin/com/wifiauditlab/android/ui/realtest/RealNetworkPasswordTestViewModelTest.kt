package com.wifiauditlab.android.ui.realtest

import com.wifiauditlab.android.lab.LabModeSurfaces
import com.wifiauditlab.android.wifi.RealNetworkSpecifierPolicy
import com.wifiauditlab.assessment.domain.connection.CurrentWifiConnection
import com.wifiauditlab.assessment.domain.real.DefaultRealNetworkPasswordEligibilityChecker
import com.wifiauditlab.assessment.domain.real.RealNetworkPasswordExecutionResult
import com.wifiauditlab.assessment.domain.real.RealNetworkReference
import com.wifiauditlab.assessment.domain.real.RealNetworkUnavailableReason
import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.Ssid
import com.wifiauditlab.assessment.port.CurrentWifiConnectionProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RealNetworkPasswordTestViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val selectedBssid = Bssid.of("AA:BB:CC:DD:EE:01")
    private val network =
        RealNetworkReference(
            ssid = Ssid("Home"),
            bssid = selectedBssid,
            securityFamily = SecurityFamily.WPA2_PERSONAL,
            displayName = "Home",
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
    fun wiring_sameSsid_password_consent_deliversSelectedNetworkAndCredentialToPort() =
        runTest {
            val fake = FakeRealNetworkPasswordExecutionPort(RealNetworkPasswordExecutionResult.Accepted)
            val provider = MutableConnectionProvider(exactConnection())
            val vm = createVm(fake, provider, network)
            advanceUntilIdle()

            assertTrue(vm.state.value.networkEligible)
            assertTrue(vm.state.value.bssidConfirmed)
            assertFalse(vm.state.value.canStart)

            val password = "correct-horse-battery"
            vm.onPasswordChanged(password)
            vm.setConsentChecked(true)
            assertTrue(vm.state.value.canStart)

            vm.startRealNetworkTest()
            advanceUntilIdle()

            assertEquals(1, fake.executeCallCount)
            val request = requireNotNull(fake.lastRequest)
            assertEquals(network.ssid, request.network.ssid)
            assertEquals(network.bssid, request.network.bssid)
            assertEquals(network.securityFamily, request.network.securityFamily)
            assertEquals(password, request.credential.reveal())
            assertTrue(request.consent.granted)
            assertTrue(request.toString().contains("███"))
            assertFalse(request.toString().contains(password))

            val completed = vm.state.value.phase as RealNetworkPasswordTestPhase.Completed
            assertEquals(RealNetworkPasswordExecutionResult.Accepted, completed.result)
        }

    @Test
    fun sameSsid_differentBssid_stillExecutable_portCalled() =
        runTest {
            val fake = FakeRealNetworkPasswordExecutionPort(RealNetworkPasswordExecutionResult.Accepted)
            val provider =
                MutableConnectionProvider(
                    CurrentWifiConnection(
                        ssid = network.ssid,
                        bssid = Bssid.of("11:22:33:44:55:66"),
                        securityFamily = network.securityFamily,
                        rssi = -40,
                    ),
                )
            val vm = createVm(fake, provider, network)
            advanceUntilIdle()

            assertTrue(vm.state.value.networkEligible)
            assertFalse(vm.state.value.bssidConfirmed)
            vm.onPasswordChanged("any-password")
            vm.setConsentChecked(true)
            assertTrue(vm.state.value.canStart)

            vm.startRealNetworkTest()
            advanceUntilIdle()

            assertEquals(1, fake.executeCallCount)
            assertNotNull(fake.lastRequest)
        }

    @Test
    fun sameSsid_nullSelectedBssid_executable_portCalled() =
        runTest {
            val noBssid =
                network.copy(bssid = null)
            val fake = FakeRealNetworkPasswordExecutionPort(RealNetworkPasswordExecutionResult.Accepted)
            val provider = MutableConnectionProvider(exactConnection())
            val vm = createVm(fake, provider, noBssid)
            advanceUntilIdle()

            assertTrue(vm.state.value.networkEligible)
            assertFalse(vm.state.value.bssidConfirmed)
            vm.onPasswordChanged("pw")
            vm.setConsentChecked(true)
            vm.startRealNetworkTest()
            advanceUntilIdle()

            assertEquals(1, fake.executeCallCount)
            assertNull(fake.lastRequest?.network?.bssid)
        }

    @Test
    fun differentSsid_blocksExecution_portNotCalled() =
        runTest {
            val fake = FakeRealNetworkPasswordExecutionPort(RealNetworkPasswordExecutionResult.Accepted)
            val provider =
                MutableConnectionProvider(
                    CurrentWifiConnection(
                        ssid = Ssid("OtherNet"),
                        bssid = Bssid.of("FF:EE:DD:CC:BB:AA"),
                        securityFamily = network.securityFamily,
                        rssi = -40,
                    ),
                )
            val vm = createVm(fake, provider, network)
            advanceUntilIdle()

            assertFalse(vm.state.value.networkEligible)
            vm.onPasswordChanged("any-password")
            vm.setConsentChecked(true)
            assertFalse(vm.state.value.canStart)

            vm.startRealNetworkTest()
            advanceUntilIdle()

            assertEquals(0, fake.executeCallCount)
            assertNull(fake.lastRequest)
        }

    @Test
    fun ssidChangedBeforeExecute_recheckBlocksPort() =
        runTest {
            val fake = FakeRealNetworkPasswordExecutionPort(RealNetworkPasswordExecutionResult.Accepted)
            val provider = MutableConnectionProvider(exactConnection())
            val vm = createVm(fake, provider, network)
            advanceUntilIdle()
            assertTrue(vm.state.value.networkEligible)

            vm.onPasswordChanged("any-password")
            vm.setConsentChecked(true)
            assertTrue(vm.state.value.canStart)

            // Different SSID and BSSID — must not match via BSSID-first Exact.
            provider.connection =
                CurrentWifiConnection(
                    ssid = Ssid("RoamedAway"),
                    bssid = Bssid.of("FF:EE:DD:CC:BB:AA"),
                    securityFamily = network.securityFamily,
                    rssi = -50,
                )

            vm.startRealNetworkTest()
            advanceUntilIdle()

            assertEquals(0, fake.executeCallCount)
            val completed = vm.state.value.phase as RealNetworkPasswordTestPhase.Completed
            val unavailable = completed.result as RealNetworkPasswordExecutionResult.Unavailable
            assertEquals(RealNetworkUnavailableReason.NetworkNotCurrent, unavailable.reason)
        }

    @Test
    fun adapterPolicy_nullBssid_doesNotRequireBssid() {
        assertNull(RealNetworkSpecifierPolicy.optionalBssidValue(null))
        assertEquals(
            selectedBssid.value,
            RealNetworkSpecifierPolicy.optionalBssidValue(selectedBssid),
        )
    }

    @Test
    fun labModeSurfaces_offHidesLab_onShowsLab_realFlowIndependent() {
        assertFalse(LabModeSurfaces.showLabTab(false))
        assertFalse(LabModeSurfaces.showLabEntryCtas(false))
        assertTrue(LabModeSurfaces.showLabTab(true))
        assertTrue(LabModeSurfaces.showLabEntryCtas(true))
        assertNotNull(RealNetworkPasswordTestTargetStore())
    }

    private fun exactConnection() =
        CurrentWifiConnection(
            ssid = network.ssid,
            bssid = selectedBssid,
            securityFamily = network.securityFamily,
            rssi = -40,
        )

    private fun createVm(
        port: FakeRealNetworkPasswordExecutionPort,
        provider: MutableConnectionProvider,
        target: RealNetworkReference,
    ): RealNetworkPasswordTestViewModel {
        val store = RealNetworkPasswordTestTargetStore()
        store.set(RealNetworkPasswordTestRequest(network = target))
        return RealNetworkPasswordTestViewModel(
            targetStore = store,
            executionPort = port,
            eligibilityChecker = DefaultRealNetworkPasswordEligibilityChecker(provider),
            connectionProvider = provider,
        )
    }

    private class MutableConnectionProvider(
        var connection: CurrentWifiConnection?,
    ) : CurrentWifiConnectionProvider {
        override suspend fun currentConnection(): CurrentWifiConnection? = connection
    }
}
