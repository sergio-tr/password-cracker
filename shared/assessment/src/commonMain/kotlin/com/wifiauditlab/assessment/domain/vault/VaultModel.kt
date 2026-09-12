package com.wifiauditlab.assessment.domain.vault

import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.NetworkIdentity
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@JvmInline
value class SavedNetworkId(val value: String) {
    init { require(value.isNotBlank()) { "SavedNetworkId must not be blank" } }
    override fun toString(): String = value

    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun random(): SavedNetworkId = SavedNetworkId(Uuid.random().toString())
    }
}

@JvmInline
value class SecretId(val value: String) {
    init { require(value.isNotBlank()) { "SecretId must not be blank" } }
    override fun toString(): String = value

    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun random(): SecretId = SecretId(Uuid.random().toString())
    }
}

/** User-defined, non-GPS place label such as "Casa", "Trabajo", "Hotel". */
@JvmInline
value class LocationLabel(val value: String) {
    override fun toString(): String = value
}

/** Optional, explicitly requested real coordinates. Only stored on demand. */
data class GeoLocation(val latitude: Double, val longitude: Double)

/**
 * A user-provided Wi-Fi credential.
 *
 * SECURITY: the plaintext lives here only transiently, on its way to or from the
 * [com.wifiauditlab.assessment.port.SecretVault]. It must never appear in logs,
 * exceptions, analytics or [toString]; the redacting [toString] enforces that.
 */
class NetworkSecret(val value: String) {
    init { require(value.isNotEmpty()) { "secret must not be empty" } }

    override fun equals(other: Any?): Boolean = other is NetworkSecret && other.value == value
    override fun hashCode(): Int = value.hashCode()

    /** Never reveals the secret. */
    override fun toString(): String = REDACTED

    companion object {
        const val REDACTED: String = "NetworkSecret(••••••••)"
    }
}

/**
 * A persisted, known network. Distinct from a transient
 * [com.wifiauditlab.assessment.domain.wifi.WifiObservation]. A saved network may
 * be reachable through several access points, hence [knownBssids] is a set.
 */
data class SavedWifiNetwork(
    val id: SavedNetworkId,
    val alias: String,
    val ssid: String,
    val securityFamily: SecurityFamily,
    val knownBssids: Set<Bssid>,
    val locationLabel: LocationLabel?,
    val geoLocation: GeoLocation?,
    val secretId: SecretId?,
    val notes: String?,
    val createdAtEpochMillis: Long,
    val lastSeenAtEpochMillis: Long?,
) {
    val identity: NetworkIdentity get() = NetworkIdentity(ssid.trim(), securityFamily)
    val hasSecret: Boolean get() = secretId != null
}

/** Payload for creating a new saved network (id/secretId are assigned by the app). */
data class NewSavedWifiNetwork(
    val alias: String,
    val ssid: String,
    val securityFamily: SecurityFamily,
    val knownBssids: Set<Bssid> = emptySet(),
    val locationLabel: LocationLabel? = null,
    val geoLocation: GeoLocation? = null,
    val notes: String? = null,
)
