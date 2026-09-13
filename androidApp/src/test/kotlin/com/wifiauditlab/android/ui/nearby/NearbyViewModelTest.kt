package com.wifiauditlab.android.ui.nearby

import com.wifiauditlab.assessment.application.AssessNetworkSecurity
import com.wifiauditlab.assessment.application.CreateSavedNetwork
import com.wifiauditlab.assessment.application.ObserveNearbyNetworks
import com.wifiauditlab.assessment.application.RecordNearbySightings
import com.wifiauditlab.assessment.application.RecordSavedNetworkSighting
import com.wifiauditlab.assessment.application.RefreshNearbyNetworks
import com.wifiauditlab.assessment.application.SaveNearbyNetwork
import com.wifiauditlab.assessment.application.UpdateSavedNetworkAlias
import com.wifiauditlab.assessment.domain.audit.DefaultPasswordAuditEligibilityChecker
import com.wifiauditlab.assessment.domain.audit.PasswordAuditEligibility
import com.wifiauditlab.assessment.domain.connection.CurrentWifiConnection
import com.wifiauditlab.assessment.domain.connection.NetworkConnectionMatch
import com.wifiauditlab.assessment.domain.match.DefaultKnownNetworkMatcher
import com.wifiauditlab.assessment.domain.security.SecurityAssessmentRegistry
import com.wifiauditlab.assessment.domain.vault.NetworkSecret
import com.wifiauditlab.assessment.domain.vault.NewSavedWifiNetwork
import com.wifiauditlab.assessment.domain.vault.SavedNetworkId
import com.wifiauditlab.assessment.domain.vault.SavedWifiNetwork
import com.wifiauditlab.assessment.domain.vault.SecretId
import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.ManagementFrameProtection
import com.wifiauditlab.assessment.domain.wifi.NetworkIdentity
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.Ssid
import com.wifiauditlab.assessment.domain.wifi.WifiBand
import com.wifiauditlab.assessment.domain.wifi.WifiChannel
import com.wifiauditlab.assessment.domain.wifi.WifiObservation
import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile
import com.wifiauditlab.assessment.domain.wifi.WifiSignal
import com.wifiauditlab.assessment.domain.wifi.WifiStandard
import com.wifiauditlab.assessment.port.CurrentWifiConnectionProvider
import com.wifiauditlab.assessment.port.SavedNetworkRepository
import com.wifiauditlab.assessment.port.SecretVault
import com.wifiauditlab.assessment.port.WifiScanRequestResult
import com.wifiauditlab.assessment.port.WifiScanState
import com.wifiauditlab.assessment.port.WifiScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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

@OptIn(ExperimentalCoroutinesApi::class)
class NearbyViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeScanner(initial: WifiScanState) : WifiScanner {
        val state = MutableStateFlow(initial)
        var refreshCount = 0
            private set

        override fun observeState(): Flow<WifiScanState> = state

        override suspend fun refresh(): WifiScanRequestResult {
            refreshCount++
            return WifiScanRequestResult.STARTED
        }
    }

    private class FakeRepo : SavedNetworkRepository {
        val networks = MutableStateFlow<List<SavedWifiNetwork>>(emptyList())
        private var seq = 0

        override fun observeAll(): Flow<List<SavedWifiNetwork>> = networks

        override suspend fun getById(id: SavedNetworkId): SavedWifiNetwork? = networks.value.firstOrNull { it.id == id }

        override suspend fun findByIdentity(identity: NetworkIdentity): List<SavedWifiNetwork> =
            networks.value.filter { it.identity == identity }

        override suspend fun create(network: NewSavedWifiNetwork): SavedWifiNetwork {
            val created =
                SavedWifiNetwork(
                    id = SavedNetworkId("net-${seq++}"),
                    alias = network.alias,
                    ssid = network.ssid,
                    securityFamily = network.securityFamily,
                    knownBssids = network.knownBssids,
                    locationLabel = network.locationLabel,
                    geoLocation = network.geoLocation,
                    secretId = null,
                    notes = network.notes,
                    createdAtEpochMillis = 0L,
                    lastSeenAtEpochMillis = null,
                )
            networks.update { it + created }
            return created
        }

        override suspend fun update(network: SavedWifiNetwork): SavedWifiNetwork {
            networks.update { list -> list.map { if (it.id == network.id) network else it } }
            return network
        }

        override suspend fun delete(id: SavedNetworkId) {
            networks.update { list -> list.filterNot { it.id == id } }
        }
    }

    private class FakeVault : SecretVault {
        override suspend fun create(secret: NetworkSecret): SecretId = SecretId("secret")

        override suspend fun read(id: SecretId): NetworkSecret? = null

        override suspend fun update(
            id: SecretId,
            secret: NetworkSecret,
        ) = Unit

        override suspend fun delete(id: SecretId) = Unit
    }

    private fun observation(
        ssid: String = "Home",
        bssid: String = "AA:BB:CC:DD:EE:01",
    ) =
        WifiObservation(
            ssid = Ssid(ssid),
            bssid = Bssid.of(bssid),
            signal = WifiSignal(-50),
            channel = WifiChannel(6, WifiBand.GHZ_2_4, 2437),
            standard = WifiStandard.WIFI_5,
            securityProfile =
                WifiSecurityProfile(
                    family = SecurityFamily.WPA2_PERSONAL,
                    keyManagements = setOf("PSK"),
                    managementFrameProtection = ManagementFrameProtection.UNKNOWN,
                    isTransitionMode = false,
                    rawCapabilities = "[WPA2-PSK-CCMP][ESS]",
                ),
            observedAtEpochMillis = 0L,
        )

    private fun viewModel(
        scanner: FakeScanner,
        repo: FakeRepo,
        connection: CurrentWifiConnection? = null,
    ): NearbyViewModel {
        val provider =
            object : CurrentWifiConnectionProvider {
                override suspend fun currentConnection(): CurrentWifiConnection? = connection
            }
        return NearbyViewModel(
            ObserveNearbyNetworks(scanner, repo, DefaultKnownNetworkMatcher()),
            RefreshNearbyNetworks(scanner),
            AssessNetworkSecurity(SecurityAssessmentRegistry.default()),
            SaveNearbyNetwork(
                CreateSavedNetwork(repo, FakeVault()),
                UpdateSavedNetworkAlias(repo),
                RecordSavedNetworkSighting(repo),
            ),
            RecordNearbySightings(RecordSavedNetworkSighting(repo)),
            provider,
            DefaultPasswordAuditEligibilityChecker(provider),
        )
    }

    @Test
    fun state_maps_scan_results_to_unknown_items() =
        runTest(dispatcher) {
            val scanner = FakeScanner(WifiScanState.Results(listOf(observation())))
            val vm = viewModel(scanner, FakeRepo())
            val job = launch { vm.state.collect {} }
            advanceUntilIdle()

            assertEquals(1, vm.state.value.items.size)
            assertFalse(vm.state.value.items.first().isKnown)
            assertFalse(vm.state.value.items.first().isCurrentlyConnected)
            job.cancel()
        }

    @Test
    fun connected_network_is_flagged_and_eligible_for_audit() =
        runTest(dispatcher) {
            val obs = observation()
            val scanner = FakeScanner(WifiScanState.Results(listOf(obs)))
            val connection =
                CurrentWifiConnection(
                    ssid = obs.ssid,
                    bssid = obs.bssid,
                    securityFamily = SecurityFamily.WPA2_PERSONAL,
                    rssi = -45,
                )
            val vm = viewModel(scanner, FakeRepo(), connection)
            val job = launch { vm.state.collect {} }
            advanceUntilIdle()

            val item = vm.state.value.items.single()
            assertTrue(item.isCurrentlyConnected)
            assertEquals(NetworkConnectionMatch.Exact, item.connectionMatch)

            vm.select(item)
            advanceUntilIdle()
            val eligibility = vm.detail.value?.auditEligibility
            assertTrue(eligibility is PasswordAuditEligibility.EligibleConnectedNetwork)
            job.cancel()
        }

    @Test
    fun refresh_delegates_to_the_scanner() =
        runTest(dispatcher) {
            val scanner = FakeScanner(WifiScanState.Idle)
            val vm = viewModel(scanner, FakeRepo())

            vm.refresh()
            advanceUntilIdle()

            assertEquals(1, scanner.refreshCount)
        }

    @Test
    fun select_computes_a_security_assessment() =
        runTest(dispatcher) {
            val scanner = FakeScanner(WifiScanState.Results(listOf(observation())))
            val vm = viewModel(scanner, FakeRepo())
            val job = launch { vm.state.collect {} }
            advanceUntilIdle()

            vm.select(vm.state.value.items.first())
            advanceUntilIdle()

            assertNotNull(vm.detail.value?.assessment)
            job.cancel()
        }

    @Test
    fun save_selected_persists_a_network_and_marks_saved() =
        runTest(dispatcher) {
            val scanner = FakeScanner(WifiScanState.Results(listOf(observation())))
            val repo = FakeRepo()
            val vm = viewModel(scanner, repo)
            val job = launch { vm.state.collect {} }
            advanceUntilIdle()

            vm.select(vm.state.value.items.first())
            advanceUntilIdle()
            vm.saveSelectedToVault("Casa")
            advanceUntilIdle()

            assertTrue(vm.detail.value?.saved == true)
            assertEquals(1, repo.networks.value.size)
            assertEquals("Casa", repo.networks.value.first().alias)
            job.cancel()
        }

    @Test
    fun known_network_records_last_seen_and_new_bssid() =
        runTest(dispatcher) {
            val repo = FakeRepo()
            repo.create(
                NewSavedWifiNetwork(
                    alias = "Casa",
                    ssid = "Home",
                    securityFamily = SecurityFamily.WPA2_PERSONAL,
                    knownBssids = setOf(Bssid.of("AA:BB:CC:DD:EE:99")),
                ),
            )
            val scanner =
                FakeScanner(
                    WifiScanState.Results(listOf(observation(ssid = "Home", bssid = "AA:BB:CC:DD:EE:01"))),
                )
            val vm = viewModel(scanner, repo)
            val job = launch { vm.state.collect {} }
            advanceUntilIdle()

            val saved = repo.networks.value.single()
            assertEquals("Casa", vm.state.value.items.single().alias)
            assertTrue(vm.state.value.items.single().isKnown)
            assertNotNull(saved.lastSeenAtEpochMillis)
            assertTrue(saved.knownBssids.any { it.value == "aa:bb:cc:dd:ee:01" })
            job.cancel()
        }
}
