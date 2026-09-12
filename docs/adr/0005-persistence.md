# ADR 0005 · Persistencia del Vault

- Estado: Aceptada (parcial) — persistencia duradera pendiente
- Fecha: 2026-09-12

## Contexto

El Vault requiere CRUD completo detrás del puerto `SavedNetworkRepository`, y los
secretos deben cifrarse con una clave del almacén seguro de plataforma.

## Decisión

- **Secretos**: `KeystoreSecretVault` (Android) implementa `SecretVault` con una
  clave AES-GCM generada dentro del Android Keystore (hardware-backed). Sólo se
  persiste el *ciphertext* (IV + tag + datos, Base64) en `SharedPreferences`
  privadas. Este es el diseño definitivo del canal de secretos.
- **Metadatos de red**: en esta fase se usa `InMemorySavedNetworkRepository` para
  ejecutar la app end-to-end. La implementación duradera (SQLDelight, KMP-native,
  respetando `Database -> Ports` y sin `Domain -> SQLite`) queda como tarea
  siguiente y **no** cambia el puerto.

## Consecuencias

- La clave nunca sale del keystore; el dominio no conoce la criptografía.
- Reiniciar la app pierde la lista de redes hasta que se implemente SQLDelight;
  los secretos cifrados sí persisten en `SharedPreferences`.
- Migrar a SQLDelight es un cambio de adaptador, transparente para dominio y UI.
