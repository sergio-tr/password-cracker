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
`UpdateSavedNetworkAlias`, `UpdateSavedNetworkLocation`, `UpdateSavedNetworkSecret`,
`RemoveSavedNetworkSecret`, `DeleteSavedNetwork`, `MatchKnownNetwork`,
`SearchSavedNetworks`.

## Seguridad de secretos

- **Nunca** en texto plano en almacenamiento, logs, excepciones, analytics,
  `toString()`, estado persistido de ViewModel, previews ni fixtures versionados.
  `NetworkSecret.toString()` devuelve `NetworkSecret(••••••••)`.
- La **clave** criptográfica reside en el almacén seguro de plataforma (Android
  Keystore, AES-GCM hardware-backed). Sólo el *ciphertext* (IV + tag + datos) se
  persiste.
- Eliminar una red elimina/desvincula su secreto de forma consistente
  (`DeleteSavedNetwork` borra el secreto antes que la red).
- `SecretVault` es una abstracción reemplazable por Keychain en iOS.

## Visualización

Las credenciales aparecen ocultas por defecto (`••••••••••••`) y sólo se revelan
o copian mediante acción explícita. La arquitectura permite añadir autenticación
biométrica antes de revelar sin que el dominio dependa de biometría (la revelación
es una acción de UI que invoca `SecretVault.read`).
