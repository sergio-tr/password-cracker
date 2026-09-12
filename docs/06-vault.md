# 06 · Vault

El Vault es un componente fundamental con **CRUD completo**, no una tabla auxiliar.

## Contratos

```kotlin
interface SavedNetworkRepository {
    fun observeAll(): Flow<List<SavedWifiNetwork>>
    suspend fun getById(id: SavedNetworkId): SavedWifiNetwork?
    suspend fun findByIdentity(identity: NetworkIdentity): List<SavedWifiNetwork>
    suspend fun create(network: NewSavedWifiNetwork): SavedWifiNetwork
    suspend fun update(network: SavedWifiNetwork): SavedWifiNetwork
    suspend fun delete(id: SavedNetworkId)
}

interface SecretVault {
    suspend fun create(secret: NetworkSecret): SecretId
    suspend fun read(id: SecretId): NetworkSecret?
    suspend fun update(id: SecretId, secret: NetworkSecret)
    suspend fun delete(id: SecretId)
}
```

## Casos de uso

`CreateSavedNetwork`, `GetSavedNetwork`, `ObserveSavedNetworks`,
`UpdateSavedNetworkAlias`, `UpdateSavedNetworkLocation`, `UpdateSavedNetworkNotes`,
`UpdateSavedNetworkSecret`, `RemoveSavedNetworkSecret`, `DeleteSavedNetwork`,
`MatchKnownNetwork`, `SearchSavedNetworks`, `QuerySavedNetworks`,
`RevealSavedNetworkSecret`, `RecordSavedNetworkSighting`, `RecordNearbySightings`,
`SaveNearbyNetwork`.

## Seguridad de secretos

- **Nunca** en texto plano en almacenamiento, logs, excepciones, analytics,
  `toString()`, estado persistido de ViewModel, previews ni fixtures versionados.
  `NetworkSecret.toString()` devuelve `NetworkSecret(••••••••)`.
- La **clave** criptográfica reside en el almacén seguro de plataforma (Android
  Keystore, AES-GCM hardware-backed). Sólo el *ciphertext* (IV + tag + datos) se
  persiste.
- Eliminar una red elimina/desvincula su secreto de forma consistente:
  `DeleteSavedNetwork` borra primero los metadatos y luego el secreto
  (best-effort), de modo que nunca quede una red apuntando a un secreto
  inexistente (ADR 0007).
- `SecretVault` es una abstracción reemplazable por Keychain en iOS.

## Visualización

Las credenciales aparecen ocultas por defecto (`••••••••••••`) y sólo se revelan
o copian mediante acción explícita. La arquitectura permite añadir autenticación
biométrica antes de revelar sin que el dominio dependa de biometría (la revelación
es una acción de UI que invoca `RevealSavedNetworkSecret`).

## Listado y re-detección

El listado muestra alias, SSID, ubicación, familia de seguridad, si hay secreto y
la última vista. `QuerySavedNetworks` aplica búsqueda, filtro (con/sin contraseña)
y orden (alias, última vista, seguridad).

Si una red conocida se detecta de nuevo, Nearby muestra **primero el alias** del
usuario. `RecordSavedNetworkSighting` fusiona el BSSID observado y actualiza
`lastSeenAtEpochMillis` (con intervalo mínimo para no reescribir en bucle).
`SaveNearbyNetwork` crea una red nueva o actualiza la coincidencia Exact/Probable
en lugar de duplicarla.
