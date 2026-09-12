package com.wifiauditlab.assessment.application

import com.wifiauditlab.assessment.domain.vault.NetworkSecret
import com.wifiauditlab.assessment.domain.vault.NewSavedWifiNetwork
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.testing.InMemorySavedNetworkRepository
import com.wifiauditlab.assessment.testing.InMemorySecretVault
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VaultUseCasesTest {
    private fun newNetwork(
        alias: String = "Casa",
        ssid: String = "MOVISTAR_1234",
    ) =
        NewSavedWifiNetwork(
            alias = alias,
            ssid = ssid,
            securityFamily = SecurityFamily.WPA2_PERSONAL,
        )

    @Test
    fun create_read_update_delete_round_trip() =
        runTest {
            val repo = InMemorySavedNetworkRepository()
            val vault = InMemorySecretVault()
            val created = CreateSavedNetwork(repo, vault)(newNetwork())

            assertNotNull(GetSavedNetwork(repo)(created.id))
            assertEquals(1, ObserveSavedNetworks(repo)().first().size)

            val renamed = UpdateSavedNetworkAlias(repo)(created.id, "Casa nueva")
            assertEquals("Casa nueva", renamed.alias)

            DeleteSavedNetwork(repo, vault)(created.id)
            assertNull(GetSavedNetwork(repo)(created.id))
        }

    @Test
    fun create_with_secret_links_it_and_never_exposes_plaintext() =
        runTest {
            val repo = InMemorySavedNetworkRepository()
            val vault = InMemorySecretVault()
            val created = CreateSavedNetwork(repo, vault)(newNetwork(), NetworkSecret("hunter2-super-secret"))

            assertTrue(created.hasSecret)
            assertEquals(1, vault.storedCount)
            // The entity carries only an id, never the plaintext.
            assertFalse(created.toString().contains("hunter2"))
        }

    @Test
    fun update_secret_creates_then_updates_in_place() =
        runTest {
            val repo = InMemorySavedNetworkRepository()
            val vault = InMemorySecretVault()
            val created = CreateSavedNetwork(repo, vault)(newNetwork())

            val withSecret = UpdateSavedNetworkSecret(repo, vault)(created.id, NetworkSecret("first-pass"))
            assertTrue(withSecret.hasSecret)
            assertEquals(1, vault.storedCount)

            // Updating again must not create a second secret entry.
            UpdateSavedNetworkSecret(repo, vault)(created.id, NetworkSecret("second-pass"))
            assertEquals(1, vault.storedCount)
            assertEquals("second-pass", vault.read(withSecret.secretId!!)?.value)
        }

    @Test
    fun remove_secret_unlinks_and_deletes_from_vault() =
        runTest {
            val repo = InMemorySavedNetworkRepository()
            val vault = InMemorySecretVault()
            val created = CreateSavedNetwork(repo, vault)(newNetwork(), NetworkSecret("to-remove"))

            val cleared = RemoveSavedNetworkSecret(repo, vault)(created.id)
            assertFalse(cleared.hasSecret)
            assertEquals(0, vault.storedCount)
        }

    @Test
    fun deleting_network_with_secret_removes_the_secret_too() =
        runTest {
            val repo = InMemorySavedNetworkRepository()
            val vault = InMemorySecretVault()
            val created = CreateSavedNetwork(repo, vault)(newNetwork(), NetworkSecret("cascade-me"))

            DeleteSavedNetwork(repo, vault)(created.id)
            assertEquals(0, vault.storedCount)
        }

    @Test
    fun search_filters_by_alias_ssid_and_location() =
        runTest {
            val repo = InMemorySavedNetworkRepository()
            val vault = InMemorySecretVault()
            CreateSavedNetwork(repo, vault)(newNetwork(alias = "Casa", ssid = "MOVISTAR_1234"))
            CreateSavedNetwork(repo, vault)(newNetwork(alias = "Oficina", ssid = "CORP_WIFI"))

            assertEquals(1, SearchSavedNetworks(repo)("movistar").first().size)
            assertEquals(1, SearchSavedNetworks(repo)("oficina").first().size)
            assertEquals(2, SearchSavedNetworks(repo)("").first().size)
        }
}
