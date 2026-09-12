package com.wifiauditlab.persistence

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.wifiauditlab.assessment.domain.vault.GeoLocation
import com.wifiauditlab.assessment.domain.vault.LocationLabel
import com.wifiauditlab.assessment.domain.vault.NewSavedWifiNetwork
import com.wifiauditlab.assessment.domain.vault.SavedNetworkId
import com.wifiauditlab.assessment.domain.vault.SavedWifiNetwork
import com.wifiauditlab.assessment.domain.vault.SecretId
import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.NetworkIdentity
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.port.SavedNetworkRepository
import com.wifiauditlab.persistence.db.Saved_network
import com.wifiauditlab.persistence.db.VaultDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * Durable [SavedNetworkRepository] backed by SQLDelight. This is an adapter: the
 * domain and use cases never see SQL. Secrets are never stored here; only the
 * [SecretId] pointer into the [com.wifiauditlab.assessment.port.SecretVault] is kept.
 */
class SqlDelightSavedNetworkRepository(
    database: VaultDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val idFactory: () -> SavedNetworkId = { SavedNetworkId.random() },
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : SavedNetworkRepository {
    private val queries = database.savedNetworkQueries

    override fun observeAll(): Flow<List<SavedWifiNetwork>> =
        queries.selectAll()
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getById(id: SavedNetworkId): SavedWifiNetwork? =
        withContext(dispatcher) {
            queries.selectById(id.value).executeAsOneOrNull()?.toDomain()
        }

    override suspend fun findByIdentity(identity: NetworkIdentity): List<SavedWifiNetwork> =
        withContext(dispatcher) {
            queries.selectByIdentity(identity.normalizedSsid, identity.securityFamily.name)
                .executeAsList()
                .map { it.toDomain() }
        }

    override suspend fun create(network: NewSavedWifiNetwork): SavedWifiNetwork =
        withContext(dispatcher) {
            val created =
                SavedWifiNetwork(
                    id = idFactory(),
                    alias = network.alias,
                    ssid = network.ssid,
                    securityFamily = network.securityFamily,
                    knownBssids = network.knownBssids,
                    locationLabel = network.locationLabel,
                    geoLocation = network.geoLocation,
                    secretId = null,
                    notes = network.notes,
                    createdAtEpochMillis = nowMillis(),
                    lastSeenAtEpochMillis = null,
                )
            queries.insert(
                id = created.id.value,
                alias = created.alias,
                ssid = created.ssid,
                security_family = created.securityFamily.name,
                known_bssids = created.knownBssids.encode(),
                location_label = created.locationLabel?.value,
                geo_lat = created.geoLocation?.latitude,
                geo_lng = created.geoLocation?.longitude,
                secret_id = created.secretId?.value,
                notes = created.notes,
                created_at = created.createdAtEpochMillis,
                last_seen_at = created.lastSeenAtEpochMillis,
            )
            created
        }

    override suspend fun update(network: SavedWifiNetwork): SavedWifiNetwork =
        withContext(dispatcher) {
            queries.update(
                alias = network.alias,
                ssid = network.ssid,
                security_family = network.securityFamily.name,
                known_bssids = network.knownBssids.encode(),
                location_label = network.locationLabel?.value,
                geo_lat = network.geoLocation?.latitude,
                geo_lng = network.geoLocation?.longitude,
                secret_id = network.secretId?.value,
                notes = network.notes,
                last_seen_at = network.lastSeenAtEpochMillis,
                id = network.id.value,
            )
            network
        }

    override suspend fun delete(id: SavedNetworkId) {
        withContext(dispatcher) {
            queries.deleteById(id.value)
        }
    }

    private fun Set<Bssid>.encode(): String = joinToString(BSSID_SEPARATOR) { it.value }

    private fun Saved_network.toDomain(): SavedWifiNetwork =
        SavedWifiNetwork(
            id = SavedNetworkId(id),
            alias = alias,
            ssid = ssid,
            securityFamily = SecurityFamily.valueOf(security_family),
            knownBssids = decodeBssids(known_bssids),
            locationLabel = location_label?.let { LocationLabel(it) },
            geoLocation = if (geo_lat != null && geo_lng != null) GeoLocation(geo_lat, geo_lng) else null,
            secretId = secret_id?.let { SecretId(it) },
            notes = notes,
            createdAtEpochMillis = created_at,
            lastSeenAtEpochMillis = last_seen_at,
        )

    private fun decodeBssids(encoded: String): Set<Bssid> =
        if (encoded.isBlank()) {
            emptySet()
        } else {
            encoded.split(BSSID_SEPARATOR).filter { it.isNotBlank() }.map { Bssid.of(it) }.toSet()
        }

    private companion object {
        const val BSSID_SEPARATOR = ","
    }
}
