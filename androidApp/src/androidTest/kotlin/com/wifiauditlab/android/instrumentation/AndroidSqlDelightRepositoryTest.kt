package com.wifiauditlab.android.instrumentation

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.wifiauditlab.assessment.domain.vault.NewSavedWifiNetwork
import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.persistence.SqlDelightSavedNetworkRepository
import com.wifiauditlab.persistence.db.VaultDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented CRUD against the real Android SQLite driver (file DB).
 * Synthetic lab data only — no live Wi‑Fi credentials.
 */
@RunWith(AndroidJUnit4::class)
class AndroidSqlDelightRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "instrumentation_vault_test.db"

    @Before
    fun deleteDbBefore() {
        context.deleteDatabase(dbName)
    }

    @After
    fun deleteDbAfter() {
        context.deleteDatabase(dbName)
    }

    private fun openRepo(): Pair<AndroidSqliteDriver, SqlDelightSavedNetworkRepository> {
        // AndroidSqliteDriver applies Schema on open — do not call Schema.create again.
        val driver = AndroidSqliteDriver(VaultDatabase.Schema, context, dbName)
        val repo =
            SqlDelightSavedNetworkRepository(
                database = VaultDatabase(driver),
                dispatcher = Dispatchers.IO,
            )
        return driver to repo
    }

    private fun payload(
        alias: String = "Casa-Test",
        ssid: String = "LabNet",
        bssids: Set<Bssid> = emptySet(),
    ) = NewSavedWifiNetwork(
        alias = alias,
        ssid = ssid,
        securityFamily = SecurityFamily.WPA2_PERSONAL,
        knownBssids = bssids,
    )

    @Test
    fun create_read_update_delete() =
        runBlocking {
            val (driver, repo) = openRepo()
            try {
                val created = repo.create(payload())
                assertEquals("Casa-Test", created.alias)
                assertEquals("LabNet", created.ssid)

                val loaded = repo.getById(created.id)
                assertEquals(created, loaded)

                val updated = created.copy(alias = "Casa-Test-Updated", notes = "lab note")
                repo.update(updated)
                assertEquals(updated, repo.getById(created.id))

                repo.delete(created.id)
                assertNull(repo.getById(created.id))
            } finally {
                driver.close()
            }
        }

    @Test
    fun bssid_persistence() =
        runBlocking {
            val (driver, repo) = openRepo()
            try {
                val bssids =
                    setOf(
                        Bssid.of("AA:BB:CC:DD:EE:01"),
                        Bssid.of("AA:BB:CC:DD:EE:02"),
                    )
                val created = repo.create(payload(bssids = bssids))
                val loaded = repo.getById(created.id)!!
                assertEquals(bssids, loaded.knownBssids)
            } finally {
                driver.close()
            }
        }

    @Test
    fun reopen_database_and_read_again() =
        runBlocking {
            val networkId =
                openRepo().let { (driver, repo) ->
                    try {
                        val created = repo.create(payload(alias = "Casa-Test", ssid = "LabNet"))
                        created.id
                    } finally {
                        driver.close()
                    }
                }

            val (driver2, repo2) = openRepo()
            try {
                val loaded = repo2.getById(networkId)
                assertTrue(loaded != null)
                assertEquals("Casa-Test", loaded!!.alias)
                assertEquals("LabNet", loaded.ssid)
            } finally {
                driver2.close()
            }
        }
}
