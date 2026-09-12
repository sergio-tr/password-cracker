package com.wifiauditlab.assessment.application

import com.wifiauditlab.assessment.domain.vault.GeoLocation
import com.wifiauditlab.assessment.domain.vault.LocationLabel
import com.wifiauditlab.assessment.domain.vault.NetworkSecret
import com.wifiauditlab.assessment.domain.vault.NewSavedWifiNetwork
import com.wifiauditlab.assessment.domain.vault.SavedNetworkId
import com.wifiauditlab.assessment.domain.vault.SavedWifiNetwork
import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.WifiObservation
import com.wifiauditlab.assessment.port.SavedNetworkRepository
import com.wifiauditlab.assessment.port.SecretVault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Thrown when an operation targets a saved network that does not exist. */
class SavedNetworkNotFoundException(id: SavedNetworkId) :
    IllegalArgumentException("saved network not found: $id")

private suspend fun SavedNetworkRepository.require(id: SavedNetworkId): SavedWifiNetwork =
    getById(id) ?: throw SavedNetworkNotFoundException(id)

/**
 * Creates a saved network, optionally attaching a secret in the secure vault.
 *
 * There is no cross-store transaction between the metadata repository and the
 * secret vault, so the two writes are coordinated with **explicit compensation**:
 * the secret is created first, then the network; any failure rolls back the
 * partial work so neither an orphaned secret nor an orphaned network remains.
 */
class CreateSavedNetwork(
    private val repository: SavedNetworkRepository,
    private val secretVault: SecretVault,
) {
    suspend operator fun invoke(
        network: NewSavedWifiNetwork,
        secret: NetworkSecret? = null,
    ): SavedWifiNetwork {
        if (secret == null) return repository.create(network)

        val secretId = secretVault.create(secret)
        val created =
            try {
                repository.create(network)
            } catch (t: Throwable) {
                runCatching { secretVault.delete(secretId) }
                throw t
            }
        return try {
            repository.update(created.copy(secretId = secretId))
        } catch (t: Throwable) {
            runCatching { repository.delete(created.id) }
            runCatching { secretVault.delete(secretId) }
            throw t
        }
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

/** Updates the free-form notes attached to a saved network. */
class UpdateSavedNetworkNotes(private val repository: SavedNetworkRepository) {
    suspend operator fun invoke(
        id: SavedNetworkId,
        notes: String?,
    ): SavedWifiNetwork =
        repository.update(repository.require(id).copy(notes = notes?.takeIf { it.isNotBlank() }))
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
            // First set: create the secret, then link it; roll back the secret if linking fails.
            val secretId = secretVault.create(secret)
            try {
                repository.update(network.copy(secretId = secretId))
            } catch (t: Throwable) {
                runCatching { secretVault.delete(secretId) }
                throw t
            }
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

/**
 * Deletes a network and its secret.
 *
 * The metadata is removed first so no network can ever reference a missing
 * secret (a correctness bug); the secret deletion is then best-effort. If it
 * fails, the only residue is an unreferenced, still-encrypted ciphertext, which
 * is benign and cannot be turned back into a dangling reference.
 */
class DeleteSavedNetwork(
    private val repository: SavedNetworkRepository,
    private val secretVault: SecretVault,
) {
    suspend operator fun invoke(id: SavedNetworkId) {
        val network = repository.getById(id) ?: return
        repository.delete(id)
        network.secretId?.let { secretId ->
            runCatching { secretVault.delete(secretId) }
        }
    }
}

/** Case-insensitive search across alias, SSID and location label. */
class SearchSavedNetworks(
    private val repository: SavedNetworkRepository,
    private val querySaved: QuerySavedNetworks = QuerySavedNetworks(),
) {
    operator fun invoke(query: String): Flow<List<SavedWifiNetwork>> =
        repository.observeAll().map { networks -> querySaved(networks, query) }
}

/**
 * Reads a secret only when the caller asks. Returns plaintext for a transient UI
 * reveal; never logs or stringifies the value.
 */
class RevealSavedNetworkSecret(
    private val repository: SavedNetworkRepository,
    private val secretVault: SecretVault,
) {
    suspend operator fun invoke(id: SavedNetworkId): String? {
        val secretId = repository.getById(id)?.secretId ?: return null
        return secretVault.read(secretId)?.value
    }
}

/**
 * Merges a newly observed BSSID and refreshes [SavedWifiNetwork.lastSeenAtEpochMillis].
 *
 * Writes are skipped when the BSSID is already known and the last sighting is
 * more recent than [minSightingIntervalMillis], so a scan-driven observer cannot
 * loop on its own repository updates.
 */
class RecordSavedNetworkSighting(
    private val repository: SavedNetworkRepository,
    private val minSightingIntervalMillis: Long = DEFAULT_SIGHTING_INTERVAL_MILLIS,
) {
    suspend operator fun invoke(
        id: SavedNetworkId,
        bssid: Bssid,
        seenAtEpochMillis: Long,
    ): SavedWifiNetwork {
        val network = repository.require(id)
        val needsBssid = bssid !in network.knownBssids
        val lastSeen = network.lastSeenAtEpochMillis
        val needsSeen = lastSeen == null || seenAtEpochMillis - lastSeen >= minSightingIntervalMillis
        if (!needsBssid && !needsSeen) return network
        return repository.update(
            network.copy(
                knownBssids = network.knownBssids + bssid,
                lastSeenAtEpochMillis = seenAtEpochMillis,
            ),
        )
    }

    companion object {
        const val DEFAULT_SIGHTING_INTERVAL_MILLIS: Long = 60_000L
    }
}

/** Records sightings for Exact/Probable matches in a nearby snapshot. */
class RecordNearbySightings(
    private val recordSighting: RecordSavedNetworkSighting,
) {
    suspend operator fun invoke(networks: List<NearbyNetwork>) {
        for (item in networks) {
            val id = item.knownNetworkId ?: continue
            recordSighting(id, item.observation.bssid, item.observation.observedAtEpochMillis)
        }
    }
}

/**
 * Saves a detected network, or refreshes an existing Exact/Probable match
 * (alias + last seen + known BSSID) instead of creating a duplicate.
 */
class SaveNearbyNetwork(
    private val createSavedNetwork: CreateSavedNetwork,
    private val updateAlias: UpdateSavedNetworkAlias,
    private val recordSighting: RecordSavedNetworkSighting,
) {
    suspend operator fun invoke(
        observation: WifiObservation,
        alias: String,
        existingId: SavedNetworkId? = null,
    ): SavedWifiNetwork {
        if (existingId != null) {
            val trimmed = alias.trim()
            if (trimmed.isNotEmpty()) {
                updateAlias(existingId, trimmed)
            }
            return recordSighting(existingId, observation.bssid, observation.observedAtEpochMillis)
        }
        val fallbackAlias = observation.ssid.value.ifEmpty { "Red oculta" }
        return createSavedNetwork(
            NewSavedWifiNetwork(
                alias = alias.ifBlank { fallbackAlias },
                ssid = observation.ssid.value,
                securityFamily = observation.securityProfile.family,
                knownBssids = setOf(observation.bssid),
            ),
        )
    }
}
