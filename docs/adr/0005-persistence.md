# ADR 0005 · Persistencia del Vault

- Estado: Aceptada
- Fecha: 2026-09-12
- Actualización: persistencia duradera implementada con SQLDelight (`:shared:persistence`).

## Contexto

El Vault requiere CRUD completo detrás del puerto `SavedNetworkRepository`, y los
secretos deben cifrarse con una clave del almacén seguro de plataforma.

## Decisión

- **Secretos**: `KeystoreSecretVault` (Android) implementa `SecretVault` con una
  clave AES-GCM generada dentro del Android Keystore (hardware-backed). Sólo se
  persiste el *ciphertext* (IV + tag + datos, Base64) en `SharedPreferences`
  privadas. Este es el diseño definitivo del canal de secretos.
- **Metadatos de red**: `SqlDelightSavedNetworkRepository` (módulo KMP
  `:shared:persistence`) implementa el puerto `SavedNetworkRepository` sobre
  SQLDelight. En Android usa `AndroidSqliteDriver` (BD `vault.db`); los tests JVM
  usan un driver SQLite en memoria. El esquema está versionado vía
  `VaultDatabase.Schema` (create/migrate), de modo que futuras migraciones son
  ficheros `.sqm` incrementales. El BSSID set se serializa como lista separada por
  comas dentro del adaptador (detalle interno; el dominio no lo ve). No se guarda
  ningún secreto: sólo el puntero `SecretId`.

## Consecuencias

- La clave nunca sale del keystore; el dominio no conoce la criptografía.
- Reiniciar la app pierde la lista de redes hasta que se implemente SQLDelight;
  los secretos cifrados sí persisten en `SharedPreferences`.
- Migrar a SQLDelight es un cambio de adaptador, transparente para dominio y UI.
