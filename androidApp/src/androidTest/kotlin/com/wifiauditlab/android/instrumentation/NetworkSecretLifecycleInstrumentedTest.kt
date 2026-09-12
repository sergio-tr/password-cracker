package com.wifiauditlab.android.instrumentation

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.wifiauditlab.android.platform.KeystoreSecretVault
import com.wifiauditlab.assessment.application.CreateSavedNetwork
import com.wifiauditlab.assessment.application.DeleteSavedNetwork
import com.wifiauditlab.assessment.application.RemoveSavedNetworkSecret
import com.wifiauditlab.assessment.application.RevealSavedNetworkSecret
import com.wifiauditlab.assessment.application.UpdateSavedNetworkSecret
import com.wifiauditlab.assessment.domain.vault.NetworkSecret
import com.wifiauditlab.assessment.domain.vault.NewSavedWifiNetwork
import com.wifiauditlab.assessment.domain.vault.SavedNetworkId
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.persistence.SqlDelightSavedNetworkRepository
import com.wifiauditlab.persistence.db.VaultDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end secret lifecycle: SQLDelight metadata + Keystore vault + use cases.
 * Synthetic data only. Never logs secret plaintext.
 */
@RunWith(AndroidJUnit4::class)
class NetworkSecretLifecycleInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "instrumentation_lifecycle_vault_test.db"
    private val prefsName = "secret_vault"
    private val initialPlaintext = "instrumentation-secret-01"
    private val updatedPlaintext = "instrumentation-secret-02"

    @Before
    fun cleanBefore() {
        context.deleteDatabase(dbName)
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun cleanAfter() {
        context.deleteDatabase(dbName)
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun openStack(): Triple<AndroidSqliteDriver, SqlDelightSavedNetworkRepository, KeystoreSecretVault> {
        val driver = AndroidSqliteDriver(VaultDatabase.Schema, context, dbName)
        VaultDatabase.Schema.create(driver)
        val repo =
            SqlDelightSavedNetworkRepository(
                database = VaultDatabase(driver),
                dispatcher = Dispatchers.IO,
            )
        val vault = KeystoreSecretVault(context)
        return Triple(driver, repo, vault)
    }

    private fun payload() =
        NewSavedWifiNetwork(
            alias = "Casa-Test",
            ssid = "LabNet",
            securityFamily = SecurityFamily.WPA2_PERSONAL,
        )

    @Test
    fun create_reveal_update_remove_delete_across_new_instances() =
        runBlocking {
            val networkId: SavedNetworkId =
                openStack().let { (driver, repo, vault) ->
                    try {
                        val created =
                            CreateSavedNetwork(repo, vault)(
                                payload(),
                                NetworkSecret(initialPlaintext),
                            )
                        assertTrue(created.hasSecret)
                        assertNotNull(created.secretId)
                        created.id
                    } finally {
                        driver.close()
                    }
                }

            openStack().let { (driver, repo, vault) ->
                try {
                    val network = repo.getById(networkId)
                    assertNotNull(network)
                    assertNotNull(network!!.secretId)

                    val revealed = RevealSavedNetworkSecret(repo, vault)(networkId)
                    assertTrue("reveal mismatch (values redacted)", revealed == initialPlaintext)

                    UpdateSavedNetworkSecret(repo, vault)(
                        networkId,
                        NetworkSecret(updatedPlaintext),
                    )
                    assertTrue(
                        "update reveal mismatch (values redacted)",
                        RevealSavedNetworkSecret(repo, vault)(networkId) == updatedPlaintext,
                    )

                    val cleared = RemoveSavedNetworkSecret(repo, vault)(networkId)
                    assertNull(cleared.secretId)
                    assertFalse(cleared.hasSecret)
                    assertNull(RevealSavedNetworkSecret(repo, vault)(networkId))

                    DeleteSavedNetwork(repo, vault)(networkId)
                    assertNull(repo.getById(networkId))
                } finally {
                    driver.close()
                }
            }
        }
}
