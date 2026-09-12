package com.wifiauditlab.android.data

import com.wifiauditlab.assessment.domain.vault.NewSavedWifiNetwork
import com.wifiauditlab.assessment.domain.vault.SavedNetworkId
import com.wifiauditlab.assessment.domain.vault.SavedWifiNetwork
import com.wifiauditlab.assessment.domain.wifi.NetworkIdentity
import com.wifiauditlab.assessment.port.SavedNetworkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory Vault repository used to run the app end-to-end while the SQLDelight
 * adapter is implemented (see docs/adr/0005-persistence.md). It intentionally
 * stores no secrets — only [SavedNetworkId] references handled by [SecretVault].
 */
class InMemorySavedNetworkRepository : SavedNetworkRepository {
    private val state = MutableStateFlow<List<SavedWifiNetwork>>(emptyList())

    override fun observeAll(): Flow<List<SavedWifiNetwork>> = state.asStateFlow()

    override suspend fun getById(id: SavedNetworkId): SavedWifiNetwork? =
        state.value.firstOrNull { it.id == id }

    override suspend fun findByIdentity(identity: NetworkIdentity): List<SavedWifiNetwork> =
        state.value.filter { it.identity == identity }

    override suspend fun create(network: NewSavedWifiNetwork): SavedWifiNetwork {
        val created =
            SavedWifiNetwork(
                id = SavedNetworkId.random(),
                alias = network.alias,
                ssid = network.ssid,
                securityFamily = network.securityFamily,
                knownBssids = network.knownBssids,
                locationLabel = network.locationLabel,
                geoLocation = network.geoLocation,
                secretId = null,
                notes = network.notes,
                createdAtEpochMillis = System.currentTimeMillis(),
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
