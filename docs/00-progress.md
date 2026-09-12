# 00 · Estado del proyecto

Documento vivo para controlar qué está implementado y qué queda pendiente. Se
actualiza en cada iteración (misma PR que el cambio, ver política de docs).

Prioridades acordadas: **(1) Vault**, **(2) búsqueda de contraseñas con un
algoritmo potente**, **(3) cobertura completa de pantallas y acciones**.

## Implementado

| Área | Estado | Notas |
| --- | --- | --- |
| Build KMP multi-módulo (Gradle 8.11.1, Kotlin 2.1, AGP 8.7.3) | ✅ | `jvm` + `androidTarget`. |
| Frontera Real/Lab por grafo de módulos | ✅ | `:shared:lab` sólo depende de `:shared:core`. |
| `CombinationCount` (> 64 bits, formato) | ✅ | Tests incluidos. |
| `PlatformCapabilities` | ✅ | Android; iOS previsto. |
| Modelo Wi-Fi + `WifiSecurityClassifier` | ✅ | Tests. |
| `KnownNetworkMatcher` (Exact/Probable/Unknown/Ambiguous) | ✅ | Tests. |
| Security Assessment (Strategy Registry, 8 estrategias) | ✅ | Tests. |
| Vault: modelo, puertos, casos de uso CRUD + secretos | ✅ | Tests; cascada de borrado de secreto. |
| Motor de laboratorio (lazy, límites, cancelación, métricas, viabilidad) | ✅ | Tests de casos críticos. |
| Adaptadores Android (WifiScanner/Mapper/Permisos, KeystoreSecretVault) | ✅ | Compila; falta test instrumentado. |
| DI (Koin) + UI Compose (Redes, Guardadas, Laboratorio, Ajustes) | ✅ (parcial) | Ver pendientes de UI. |
| Documentación `docs/01`–`11` + ADRs | ✅ | — |
| Tooling: CI (compile + unit tests + lint), ktlint + `.editorconfig`, reglas `.cursor` | ✅ | GitHub Actions; ADR-001/002/003. |
| Tests de dominio (objetos de valor Wi‑Fi/Vault/Alphabet) | ✅ | Redacción de `NetworkSecret`, umbrales de señal, identidad, invariantes. |
| Casos de uso de aplicación para redes cercanas | ✅ | `ObserveNearbyNetworks` (matching fuera del ViewModel), `RefreshNearbyNetworks`; fakes + tests. |

## Pendiente

| Área | Prioridad | Notas |
| --- | --- | --- |
| Persistencia duradera del Vault (SQLDelight) | Alta | Hoy repo en memoria; secretos cifrados sí persisten (ADR 0005). |
| Algoritmo de búsqueda más potente | Alta | Nuevas estrategias/optimizadores de priorización; pool de workers con benchmarks (ADR 0006). |
| Pantallas restantes | Alta | Onboarding, Permissions dedicada, Network Detail, Security Analysis detallada, Saved Network Detail/Edit, Lab Result como pantalla propia. |
| Recablear `NearbyViewModel` a `ObserveNearbyNetworks` | Alta | Quitar el matching del ViewModel (FASE 07). |
| Acciones de Vault en UI | Alta | Editar alias/ubicación/secreto, copiar al portapapeles, revelar con biometría. |
| Tests de ViewModel + Compose UI + instrumentación | Media | — |
| Módulo de benchmarks del laboratorio | Media | Sección 29 del enunciado. |
| Targets iOS reales | Baja | Requiere macOS/Xcode. |
