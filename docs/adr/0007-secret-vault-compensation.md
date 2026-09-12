# ADR 0007 · Compensación en el ciclo red + secreto

- Estado: Aceptada
- Fecha: 2026-09-12
- Relacionado: [0005-persistence](./0005-persistence.md)

## Contexto

Los metadatos de red (`SavedNetworkRepository`, SQLDelight) y los secretos
(`SecretVault`, Android Keystore + SharedPreferences) viven en almacenes
distintos, sin una transacción común. Una operación que toca ambos puede fallar a
mitad y dejar estado inconsistente: un secreto huérfano o una red que referencia
un secreto inexistente.

## Decisión

Coordinar ambas escrituras con **compensación explícita** en los casos de uso:

- **Crear red con secreto** (`CreateSavedNetwork`): crear primero el secreto,
  luego la red y su enlace. Si falla la creación de la red, se borra el secreto;
  si falla el enlace, se borran red y secreto. No quedan huérfanos.
- **Fijar secreto por primera vez** (`UpdateSavedNetworkSecret`): crear el secreto
  y, si falla el enlace, borrarlo.
- **Actualizar secreto existente**: se sobrescribe el ciphertext in situ (una sola
  escritura, sin coordinación).
- **Borrar red** (`DeleteSavedNetwork`): borrar primero los metadatos (para que
  nunca exista una referencia colgante), y luego borrar el secreto en modo
  *best-effort*. Si el borrado del secreto falla, el único residuo es un ciphertext
  sin referencias: benigno, no revela nada y no puede convertirse en referencia
  colgante.

## Consecuencias

- El invariante fuerte es "ninguna red referencia un secreto inexistente".
- No se implementa criptografía propia: el canal seguro es AES-GCM (AEAD) con clave
  en el Android Keystore (ADR-0005).
- Cada rama de compensación está cubierta por tests con fakes que fallan.
