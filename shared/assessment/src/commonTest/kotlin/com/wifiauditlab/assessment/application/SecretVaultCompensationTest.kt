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
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SecretVaultCompensationTest {
    private fun payload() =
        NewSavedWifiNetwork(
            alias = "Casa",
            ssid = "Home",
            securityFamily = SecurityFamily.WPA2_PERSONAL,
        )

    @Test
    fun create_with_secret_rolls_back_secret_when_network_create_fails() =
        runTest {
            val repo = InMemorySavedNetworkRepository().apply { failOnCreate = true }
            val vault = InMemorySecretVault()

            assertFailsWith<IllegalStateException> {
                CreateSavedNetwork(repo, vault)(payload(), NetworkSecret("pw"))
            }

            assertEquals(0, vault.storedCount, "orphaned secret must be compensated")
            assertTrue(repo.observeAll().first().isEmpty())
        }

    @Test
    fun create_with_secret_rolls_back_everything_when_linking_update_fails() =
        runTest {
            val repo = InMemorySavedNetworkRepository().apply { failOnUpdate = true }
            val vault = InMemorySecretVault()

            assertFailsWith<IllegalStateException> {
                CreateSavedNetwork(repo, vault)(payload(), NetworkSecret("pw"))
            }

            assertEquals(0, vault.storedCount, "orphaned secret must be compensated")
            assertTrue(repo.observeAll().first().isEmpty(), "orphaned network must be compensated")
        }

    @Test
    fun create_without_secret_never_touches_the_vault() =
        runTest {
            val repo = InMemorySavedNetworkRepository()
            val vault = InMemorySecretVault()

            val created = CreateSavedNetwork(repo, vault)(payload())

            assertEquals(0, vault.storedCount)
            assertEquals(listOf(created), repo.observeAll().first())
        }

    @Test
    fun update_first_secret_rolls_back_when_linking_update_fails() =
        runTest {
            val repo = InMemorySavedNetworkRepository()
            val vault = InMemorySecretVault()
            val created = CreateSavedNetwork(repo, vault)(payload())

            repo.failOnUpdate = true
            assertFailsWith<IllegalStateException> {
                UpdateSavedNetworkSecret(repo, vault)(created.id, NetworkSecret("pw"))
            }

            assertEquals(0, vault.storedCount, "orphaned secret must be compensated")
        }

    @Test
    fun delete_removes_network_and_secret_on_success() =
        runTest {
            val repo = InMemorySavedNetworkRepository()
            val vault = InMemorySecretVault()
            val created = CreateSavedNetwork(repo, vault)(payload(), NetworkSecret("pw"))
            assertNotNull(created.secretId)
            assertEquals(1, vault.storedCount)

            DeleteSavedNetwork(repo, vault)(created.id)

            assertTrue(repo.observeAll().first().isEmpty())
            assertEquals(0, vault.storedCount)
        }

    @Test
    fun delete_removes_network_even_if_secret_cleanup_fails() =
        runTest {
            val repo = InMemorySavedNetworkRepository()
            val vault = InMemorySecretVault()
            val created = CreateSavedNetwork(repo, vault)(payload(), NetworkSecret("pw"))

            vault.failOnDelete = true
            // Best-effort secret cleanup: delete must not throw and must remove the network.
            DeleteSavedNetwork(repo, vault)(created.id)

            assertTrue(repo.observeAll().first().isEmpty(), "network must be removed (no dangling reference)")
        }
}
