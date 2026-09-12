# ADR-002 · Ports & Adapters (Arquitectura Limpia)

- Estado: Aceptada
- Fecha: 2026-09-12
- Relacionado: [0001-clean-architecture-ports-adapters](./0001-clean-architecture-ports-adapters.md)

## Contexto

Necesitamos aislar la lógica de negocio de la tecnología (Compose, Android
Keystore, persistencia) y evitar lógica de negocio en los ViewModels.

## Decisión

Adoptar Ports & Adapters con dirección de dependencias `UI -> Application -> Domain`.

- El dominio define modelos y reglas puras.
- La capa de aplicación define **puertos** (interfaces) y **casos de uso**.
- Los adaptadores concretos (Android, Keystore, SQLDelight, Compose) implementan
  los puertos y viven fuera del núcleo.
- Los ViewModels sólo orquestan casos de uso y exponen estado de presentación.

Prohibido: `Domain -> Android/Compose/SQLite`. Prohibido: lógica de negocio en
ViewModels.

## Consecuencias

- Los casos de uso se prueban con fakes de los puertos, sin emulador.
- La tecnología concreta es un detalle reemplazable detrás de un puerto.
