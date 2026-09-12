package com.wifiauditlab.assessment.application

import com.wifiauditlab.assessment.domain.vault.GeoLocation
import com.wifiauditlab.assessment.domain.vault.LocationLabel
import com.wifiauditlab.assessment.domain.vault.NetworkSecret
import com.wifiauditlab.assessment.domain.vault.NewSavedWifiNetwork
import com.wifiauditlab.assessment.domain.vault.SavedNetworkId
import com.wifiauditlab.assessment.domain.vault.SavedWifiNetwork
import com.wifiauditlab.assessment.port.SavedNetworkRepository
import com.wifiauditlab.assessment.port.SecretVault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Thrown when an operation targets a saved network that does not exist. */
class SavedNetworkNotFoundException(id: SavedNetworkId) :
    IllegalArgumentException("saved network not found: $id")

private suspend fun SavedNetworkRepository.require(id: SavedNetworkId): SavedWifiNetwork =
    getById(id) ?: throw SavedNetworkNotFoundException(id)

/** Creates a saved network, optionally attaching a secret in the secure vault. */
class CreateSavedNetwork(
    private val repository: SavedNetworkRepository,
    private val secretVault: SecretVault,
) {
    suspend operator fun invoke(
        network: NewSavedWifiNetwork,
        secret: NetworkSecret? = null,
    ): SavedWifiNetwork {
        val created = repository.create(network)
        if (secret == null) return created
        val secretId = secretVault.create(secret)
        return repository.update(created.copy(secretId = secretId))
    }
}

class GetSavedNetwork(private val repository: SavedNetworkRepository) {
    suspend operator fun invoke(id: SavedNetworkId): SavedWifiNetwork? = repository.getById(id)
}

class ObserveSavedNetworks(private val repository: SavedNetworkRepository) {
    operator fun invoke(): Flow<List<SavedWifiNetwork>> = repository.observeAll()
}

class UpdateSavedNetworkAlias(private val repository: SavedNetworkRepository) {
    suspend operator fun invoke(
        id: SavedNetworkId,
        alias: String,
    ): SavedWifiNetwork {
        require(alias.isNotBlank()) { "alias must not be blank" }
        return repository.update(repository.require(id).copy(alias = alias))
    }
}

class UpdateSavedNetworkLocation(private val repository: SavedNetworkRepository) {
    suspend operator fun invoke(
        id: SavedNetworkId,
        locationLabel: LocationLabel?,
        geoLocation: GeoLocation? = null,
    ): SavedWifiNetwork =
        repository.update(repository.require(id).copy(locationLabel = locationLabel, geoLocation = geoLocation))
}

/** Creates the secret on first set, or updates the existing ciphertext in place. */
class UpdateSavedNetworkSecret(
    private val repository: SavedNetworkRepository,
    private val secretVault: SecretVault,
) {
    suspend operator fun invoke(
        id: SavedNetworkId,
        secret: NetworkSecret,
    ): SavedWifiNetwork {
        val network = repository.require(id)
        val existing = network.secretId
        return if (existing == null) {
            repository.update(network.copy(secretId = secretVault.create(secret)))
        } else {
            secretVault.update(existing, secret)
            network
        }
    }
}

/** Removes a secret and unlinks it, keeping the network. */
class RemoveSavedNetworkSecret(
    private val repository: SavedNetworkRepository,
    private val secretVault: SecretVault,
) {
    suspend operator fun invoke(id: SavedNetworkId): SavedWifiNetwork {
        val network = repository.require(id)
        val secretId = network.secretId ?: return network
        secretVault.delete(secretId)
        return repository.update(network.copy(secretId = null))
    }
}

/** Deletes a network, consistently removing its associated secret first. */
class DeleteSavedNetwork(
    private val repository: SavedNetworkRepository,
    private val secretVault: SecretVault,
) {
    suspend operator fun invoke(id: SavedNetworkId) {
        val network = repository.getById(id) ?: return
        network.secretId?.let { secretVault.delete(it) }
        repository.delete(id)
    }
}

/** Case-insensitive search across alias, SSID and location label. */
class SearchSavedNetworks(private val repository: SavedNetworkRepository) {
    operator fun invoke(query: String): Flow<List<SavedWifiNetwork>> {
        val needle = query.trim().lowercase()
        return repository.observeAll().map { networks ->
            if (needle.isEmpty()) {
                networks
            } else {
                networks.filter { network ->
                    network.alias.lowercase().contains(needle) ||
                        network.ssid.lowercase().contains(needle) ||
                        (network.locationLabel?.value?.lowercase()?.contains(needle) == true)
                }
            }
        }
    }
}
