package com.wifiauditlab.android.ui.vault

import com.wifiauditlab.assessment.application.CreateSavedNetwork
import com.wifiauditlab.assessment.application.DeleteSavedNetwork
import com.wifiauditlab.assessment.application.ObserveSavedNetworks
import com.wifiauditlab.assessment.application.QuerySavedNetworks
import com.wifiauditlab.assessment.application.RemoveSavedNetworkSecret
import com.wifiauditlab.assessment.application.RevealSavedNetworkSecret
import com.wifiauditlab.assessment.application.SavedNetworkSecretFilter
import com.wifiauditlab.assessment.application.UpdateSavedNetworkAlias
import com.wifiauditlab.assessment.application.UpdateSavedNetworkLocation
import com.wifiauditlab.assessment.application.UpdateSavedNetworkNotes
import com.wifiauditlab.assessment.application.UpdateSavedNetworkSecret
import com.wifiauditlab.assessment.domain.vault.NetworkSecret
import com.wifiauditlab.assessment.domain.vault.NewSavedWifiNetwork
import com.wifiauditlab.assessment.domain.vault.SavedNetworkId
import com.wifiauditlab.assessment.domain.vault.SavedWifiNetwork
import com.wifiauditlab.assessment.domain.vault.SecretId
import com.wifiauditlab.assessment.domain.wifi.NetworkIdentity
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.port.SavedNetworkRepository
import com.wifiauditlab.assessment.port.SecretVault
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VaultViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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
        private val secrets = mutableMapOf<SecretId, NetworkSecret>()
        private var seq = 0

        override suspend fun create(secret: NetworkSecret): SecretId {
            val id = SecretId("secret-${seq++}")
            secrets[id] = secret
            return id
        }

        override suspend fun read(id: SecretId): NetworkSecret? = secrets[id]

        override suspend fun update(
            id: SecretId,
            secret: NetworkSecret,
        ) {
            secrets[id] = secret
        }

        override suspend fun delete(id: SecretId) {
            secrets.remove(id)
        }
    }

    private fun viewModel(
        repo: FakeRepo,
        vault: FakeVault = FakeVault(),
    ): VaultViewModel =
        VaultViewModel(
            ObserveSavedNetworks(repo),
            QuerySavedNetworks(),
            CreateSavedNetwork(repo, vault),
            DeleteSavedNetwork(repo, vault),
            UpdateSavedNetworkAlias(repo),
            UpdateSavedNetworkLocation(repo),
            UpdateSavedNetworkNotes(repo),
            UpdateSavedNetworkSecret(repo, vault),
            RemoveSavedNetworkSecret(repo, vault),
            RevealSavedNetworkSecret(repo, vault),
        )

    @Test
    fun create_and_list_shows_alias_ssid_and_secret_flag() =
        runTest(dispatcher) {
            val vm = viewModel(FakeRepo())
            val job = launch { vm.state.collect {} }
            vm.create("Casa", "MOVISTAR_XXXX", SecurityFamily.WPA2_PERSONAL, "Salón", "hunter2")
            advanceUntilIdle()

            val network = vm.state.value.networks.single()
            assertEquals("Casa", network.alias)
            assertEquals("MOVISTAR_XXXX", network.ssid)
            assertEquals("Salón", network.locationLabel?.value)
            assertTrue(network.hasSecret)
            job.cancel()
        }

    @Test
    fun search_and_filter_hide_non_matching_rows() =
        runTest(dispatcher) {
            val vm = viewModel(FakeRepo())
            val job = launch { vm.state.collect {} }
            vm.create("Casa", "MOVISTAR_XXXX", SecurityFamily.WPA2_PERSONAL, null, "secret")
            vm.create("Oficina", "CORP_WIFI", SecurityFamily.WPA2_ENTERPRISE, null, null)
            advanceUntilIdle()

            vm.setQuery("casa")
            advanceUntilIdle()
            assertEquals(1, vm.state.value.networks.size)

            vm.setQuery("")
            vm.setFilter(SavedNetworkSecretFilter.WithoutSecret)
            advanceUntilIdle()
            assertEquals("Oficina", vm.state.value.networks.single().alias)
            job.cancel()
        }

    @Test
    fun select_does_not_reveal_secret_until_explicit_action() =
        runTest(dispatcher) {
            val vm = viewModel(FakeRepo())
            val stateJob = launch { vm.state.collect {} }
            val detailJob = launch { vm.detail.collect {} }
            vm.create("Casa", "MOVISTAR_XXXX", SecurityFamily.WPA2_PERSONAL, null, "hunter2")
            advanceUntilIdle()

            vm.select(vm.state.value.networks.single())
            advanceUntilIdle()
            assertNull(vm.detail.value?.revealedSecret)

            vm.revealSecret(vm.state.value.networks.single())
            advanceUntilIdle()
            assertEquals("hunter2", vm.detail.value?.revealedSecret)

            vm.hideSecret()
            advanceUntilIdle()
            assertNull(vm.detail.value?.revealedSecret)
            stateJob.cancel()
            detailJob.cancel()
        }

    @Test
    fun replace_and_remove_secret_update_detail() =
        runTest(dispatcher) {
            val vm = viewModel(FakeRepo())
            val stateJob = launch { vm.state.collect {} }
            val detailJob = launch { vm.detail.collect {} }
            vm.create("Casa", "MOVISTAR_XXXX", SecurityFamily.WPA2_PERSONAL, null, "old-pass")
            advanceUntilIdle()
            val id = vm.state.value.networks.single().id
            vm.select(vm.state.value.networks.single())
            vm.revealSecret(vm.state.value.networks.single())
            advanceUntilIdle()

            vm.setOrReplaceSecret(id, "new-pass")
            advanceUntilIdle()
            assertEquals("new-pass", vm.detail.value?.revealedSecret)

            vm.removeSecret(id)
            advanceUntilIdle()
            assertFalse(vm.detail.value?.network?.hasSecret == true)
            assertNull(vm.detail.value?.revealedSecret)
            stateJob.cancel()
            detailJob.cancel()
        }

    @Test
    fun edit_alias_location_notes_and_delete() =
        runTest(dispatcher) {
            val repo = FakeRepo()
            val vm = viewModel(repo)
            val stateJob = launch { vm.state.collect {} }
            val detailJob = launch { vm.detail.collect {} }
            vm.create("Casa", "MOVISTAR_XXXX", SecurityFamily.WPA2_PERSONAL, null, "secret")
            advanceUntilIdle()
            val id = vm.state.value.networks.single().id

            vm.editAlias(id, "Hogar")
            vm.editLocation(id, "Salón")
            vm.editNotes(id, "router del pasillo")
            advanceUntilIdle()

            val updated = repo.networks.value.single()
            assertEquals("Hogar", updated.alias)
            assertEquals("Salón", updated.locationLabel?.value)
            assertEquals("router del pasillo", updated.notes)

            vm.select(updated)
            vm.delete(id)
            advanceUntilIdle()
            assertTrue(repo.networks.value.isEmpty())
            assertNull(vm.detail.value)
            stateJob.cancel()
            detailJob.cancel()
        }
}
