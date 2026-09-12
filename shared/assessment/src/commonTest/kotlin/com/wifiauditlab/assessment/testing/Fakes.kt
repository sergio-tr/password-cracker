package com.wifiauditlab.assessment.testing

import com.wifiauditlab.assessment.domain.vault.NetworkSecret
import com.wifiauditlab.assessment.domain.vault.NewSavedWifiNetwork
import com.wifiauditlab.assessment.domain.vault.SavedNetworkId
import com.wifiauditlab.assessment.domain.vault.SavedWifiNetwork
import com.wifiauditlab.assessment.domain.vault.SecretId
import com.wifiauditlab.assessment.domain.wifi.NetworkIdentity
import com.wifiauditlab.assessment.port.SavedNetworkRepository
import com.wifiauditlab.assessment.port.SecretVault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Deterministic, in-memory repository for use-case tests. */
class InMemorySavedNetworkRepository : SavedNetworkRepository {
    private val state = MutableStateFlow<List<SavedWifiNetwork>>(emptyList())
    private var sequence = 0

    override fun observeAll(): Flow<List<SavedWifiNetwork>> = state.asStateFlow()

    override suspend fun getById(id: SavedNetworkId): SavedWifiNetwork? =
        state.value.firstOrNull { it.id == id }

    override suspend fun findByIdentity(identity: NetworkIdentity): List<SavedWifiNetwork> =
        state.value.filter { it.identity == identity }

    override suspend fun create(network: NewSavedWifiNetwork): SavedWifiNetwork {
        val created =
            SavedWifiNetwork(
                id = SavedNetworkId("net-${sequence++}"),
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
        state.update { it + created }
        return created
    }

    override suspend fun update(network: SavedWifiNetwork): SavedWifiNetwork {
        state.update { list -> list.map { if (it.id == network.id) network else it } }
        return network
    }

    override suspend fun delete(id: SavedNetworkId) {
        state.update { list -> list.filterNot { it.id == id } }
    }
}

/** In-memory secret vault. Tests assert that plaintext never leaks via the network entity. */
class InMemorySecretVault : SecretVault {
    private val secrets = mutableMapOf<SecretId, NetworkSecret>()
    private var sequence = 0

    val storedCount: Int get() = secrets.size

    override suspend fun create(secret: NetworkSecret): SecretId {
        val id = SecretId("secret-${sequence++}")
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
