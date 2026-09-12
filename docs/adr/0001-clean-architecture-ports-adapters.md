# ADR 0001 · Clean Architecture con Ports & Adapters

- Estado: Aceptada
- Fecha: 2026-09-12

## Contexto

El producto debe permitir reutilizar dominio y casos de uso en una futura
implementación iOS, prohibir dependencias `Domain -> Android/Compose/SQLite` y
evitar lógica de negocio en ViewModels.

## Decisión

Adoptar Clean Architecture / Ports & Adapters con la dirección de dependencias
`UI -> Application -> Domain` y adaptadores hacia puertos. El dominio y los casos
de uso no conocen Android, Compose ni el motor de persistencia.

## Consecuencias

- El dominio y los casos de uso son testeables sin emulador (target `jvm` de KMP).
- Los ViewModels sólo orquestan casos de uso y exponen estado de presentación.
- Cualquier tecnología concreta (Compose, Keystore, persistencia) es un detalle
  reemplazable detrás de un puerto.
