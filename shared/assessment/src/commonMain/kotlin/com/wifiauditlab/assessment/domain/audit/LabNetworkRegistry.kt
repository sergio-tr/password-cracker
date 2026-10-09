package com.wifiauditlab.assessment.domain.audit

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Explicit user marking that a network identity may be used for
 * [VerificationMode.LAB_NETWORK_VALIDATION] (lab / own / expressly authorized).
 *
 * Identity is SSID (trimmed) + [SecurityFamily] — matches Vault/Nearby persistence
 * without inventing BSSID-only targets (target always comes from current connection).
 */
data class AuthorizedLabNetworkKey(
    val ssid: String,
    val securityFamily: com.wifiauditlab.assessment.domain.wifi.SecurityFamily,
) {
    init {
        require(ssid.isNotBlank()) { "ssid must not be blank" }
    }

    companion object {
        fun of(
            ssid: String,
            family: com.wifiauditlab.assessment.domain.wifi.SecurityFamily,
        ): AuthorizedLabNetworkKey = AuthorizedLabNetworkKey(ssid.trim(), family)
    }
}

/**
 * Lab network registry (product name from plan).
 * ADR-004 name: AuthorizedLabNetworkStore.
 */
interface AuthorizedLabNetworkStore {
    suspend fun isAuthorized(key: AuthorizedLabNetworkKey): Boolean

    suspend fun setAuthorized(
        key: AuthorizedLabNetworkKey,
        authorized: Boolean,
    )

    suspend fun authorizedKeys(): Set<AuthorizedLabNetworkKey>
}

/** Plan name retained as alias of [AuthorizedLabNetworkStore]. */
typealias LabNetworkRegistry = AuthorizedLabNetworkStore

/** In-memory store for unit tests and JVM. */
class InMemoryAuthorizedLabNetworkStore : AuthorizedLabNetworkStore {
    private val mutex = Mutex()
    private val keys = mutableSetOf<AuthorizedLabNetworkKey>()

    override suspend fun isAuthorized(key: AuthorizedLabNetworkKey): Boolean =
        mutex.withLock { key in keys }

    override suspend fun setAuthorized(
        key: AuthorizedLabNetworkKey,
        authorized: Boolean,
    ) {
        mutex.withLock {
            if (authorized) keys += key else keys -= key
        }
    }

    override suspend fun authorizedKeys(): Set<AuthorizedLabNetworkKey> =
        mutex.withLock { keys.toSet() }
}
