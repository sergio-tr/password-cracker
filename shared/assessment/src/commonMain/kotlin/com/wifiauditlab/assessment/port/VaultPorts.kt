package com.wifiauditlab.assessment.port

import com.wifiauditlab.assessment.domain.vault.NetworkSecret
import com.wifiauditlab.assessment.domain.vault.NewSavedWifiNetwork
import com.wifiauditlab.assessment.domain.vault.SavedNetworkId
import com.wifiauditlab.assessment.domain.vault.SavedWifiNetwork
import com.wifiauditlab.assessment.domain.vault.SecretId
import com.wifiauditlab.assessment.domain.wifi.NetworkIdentity
import kotlinx.coroutines.flow.Flow

/** CRUD contract for the Vault of known networks. */
interface SavedNetworkRepository {
    fun observeAll(): Flow<List<SavedWifiNetwork>>

    suspend fun getById(id: SavedNetworkId): SavedWifiNetwork?

    suspend fun findByIdentity(identity: NetworkIdentity): List<SavedWifiNetwork>

    suspend fun create(network: NewSavedWifiNetwork): SavedWifiNetwork

    suspend fun update(network: SavedWifiNetwork): SavedWifiNetwork

    suspend fun delete(id: SavedNetworkId)
}

/**
 * Secure store for credentials. The plaintext never persists: the encryption key
 * lives in the platform secure store (Android Keystore / iOS Keychain) and only
 * ciphertext may be persisted. This abstraction is what makes iOS portability
 * possible without touching the domain.
 */
interface SecretVault {
    suspend fun create(secret: NetworkSecret): SecretId

    suspend fun read(id: SecretId): NetworkSecret?

    suspend fun update(
        id: SecretId,
        secret: NetworkSecret,
    )

    suspend fun delete(id: SecretId)
}

/** Optional real geolocation. Absent when the platform/user does not grant it. */
interface LocationProvider {
    suspend fun currentLocation(): com.wifiauditlab.assessment.domain.vault.GeoLocation?
}
