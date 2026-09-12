package com.wifiauditlab.persistence

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wifiauditlab.assessment.domain.vault.GeoLocation
import com.wifiauditlab.assessment.domain.vault.LocationLabel
import com.wifiauditlab.assessment.domain.vault.NewSavedWifiNetwork
import com.wifiauditlab.assessment.domain.vault.SavedNetworkId
import com.wifiauditlab.assessment.domain.vault.SecretId
import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.NetworkIdentity
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.persistence.db.VaultDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightSavedNetworkRepositoryTest {
    private fun newRepo(): SqlDelightSavedNetworkRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        VaultDatabase.Schema.create(driver)
        var seq = 0
        return SqlDelightSavedNetworkRepository(
            database = VaultDatabase(driver),
            dispatcher = Dispatchers.Unconfined,
            idFactory = { SavedNetworkId("net-${seq++}") },
            nowMillis = { 1_000L },
        )
    }

    private fun payload(
        alias: String = "Casa",
        ssid: String = "Home",
        family: SecurityFamily = SecurityFamily.WPA2_PERSONAL,
        bssids: Set<Bssid> = emptySet(),
        label: LocationLabel? = null,
        geo: GeoLocation? = null,
        notes: String? = null,
    ) = NewSavedWifiNetwork(
        alias = alias,
        ssid = ssid,
        securityFamily = family,
        knownBssids = bssids,
        locationLabel = label,
        geoLocation = geo,
        notes = notes,
    )

    @Test
    fun create_persists_and_observeAll_emits_it() =
        runTest {
            val repo = newRepo()
            val created = repo.create(payload())

            assertEquals(SavedNetworkId("net-0"), created.id)
            assertEquals(1_000L, created.createdAtEpochMillis)

            val all = repo.observeAll().first()
            assertEquals(listOf(created), all)
        }

    @Test
    fun bssids_and_optional_fields_round_trip() =
        runTest {
            val repo = newRepo()
            val bssids = setOf(Bssid.of("AA:BB:CC:DD:EE:01"), Bssid.of("AA:BB:CC:DD:EE:02"))
            val created =
                repo.create(
                    payload(
                        bssids = bssids,
                        label = LocationLabel("Trabajo"),
                        geo = GeoLocation(40.4, -3.7),
                        notes = "router del salón",
                    ),
                )

            val loaded = repo.getById(created.id)!!
            assertEquals(bssids, loaded.knownBssids)
            assertEquals(LocationLabel("Trabajo"), loaded.locationLabel)
            assertEquals(GeoLocation(40.4, -3.7), loaded.geoLocation)
            assertEquals("router del salón", loaded.notes)
            assertNull(loaded.secretId)
        }

    @Test
    fun findByIdentity_matches_ssid_and_family() =
        runTest {
            val repo = newRepo()
            repo.create(payload(ssid = "Home", family = SecurityFamily.WPA2_PERSONAL))
            repo.create(payload(ssid = "Home", family = SecurityFamily.WPA3_PERSONAL))
            repo.create(payload(ssid = "Other", family = SecurityFamily.WPA2_PERSONAL))

            val matches = repo.findByIdentity(NetworkIdentity("Home", SecurityFamily.WPA2_PERSONAL))
            assertEquals(1, matches.size)
            assertEquals("Home", matches.single().ssid)
        }

    @Test
    fun update_changes_persisted_fields() =
        runTest {
            val repo = newRepo()
            val created = repo.create(payload(alias = "Casa"))

            val updated =
                created.copy(
                    alias = "Casa nueva",
                    secretId = SecretId("secret-9"),
                    lastSeenAtEpochMillis = 2_000L,
                )
            repo.update(updated)

            assertEquals(updated, repo.getById(created.id))
        }

    @Test
    fun delete_removes_the_network() =
        runTest {
            val repo = newRepo()
            val created = repo.create(payload())
            repo.delete(created.id)

            assertNull(repo.getById(created.id))
            assertTrue(repo.observeAll().first().isEmpty())
        }
}
