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
| Modelo Wi-Fi + `WifiSecurityClassifier` | ✅ | Tests + fixtures por familia (OPEN/WEP/WPA/WPA2/WPA3/mixto/EAP/OWE/DPP/Passpoint) y MFP. |
| `KnownNetworkMatcher` (Exact/Probable/Unknown/Ambiguous) | ✅ | Tests. |
| Security Assessment (Strategy Registry, 8 estrategias) | ✅ | Tests por estrategia y tabla de rating por familia (incl. hallazgos/PMF). |
| Vault: modelo, puertos, casos de uso CRUD + secretos | ✅ | Tests; cascada de borrado de secreto. |
| Motor de laboratorio (lazy, límites, cancelación, métricas, viabilidad) | ✅ | Tests de casos críticos. |
| Dominio del laboratorio (policy, progress, transiciones) | ✅ | `LabSecretPolicy`, `LabSearchProgress`, `SearchLifecycle`, `LabSearchResult.fromTerminal`; CombinationCount exacto hasta 2^256. |
| Motor baseline (lazy, cancelable, tests críticos) | ✅ | `CandidateSpace`; first/middle/last, exactitud, determinismo, progreso agregado, Failed con métricas. |
| Planificador por estrategias (buckets + scoring) | ✅ | Uniform / Length / Tiered / SyntheticProbability / Adaptive; score = prob/cost con pesos en la estrategia. |
| UX de ejecución del laboratorio | ✅ | Preview (reto/estrategia/límites/viabilidad), métricas en vivo, STOP visible, resultado con métricas; tests de ViewModel. |
| Adaptadores Android (WifiScanner/Mapper/Permisos, KeystoreSecretVault) | ✅ | Compila; falta test instrumentado. |
| DI (Koin) + UI Compose (Redes, Guardadas, Laboratorio, Ajustes) | ✅ (parcial) | Ver pendientes de UI. |
| Documentación `docs/01`–`11` + ADRs | ✅ | — |
| Tooling: CI (compile + unit tests + lint), ktlint + `.editorconfig`, reglas `.cursor` | ✅ | GitHub Actions; ADR-001/002/003. |
| Tests de dominio (objetos de valor Wi‑Fi/Vault/Alphabet) | ✅ | Redacción de `NetworkSecret`, umbrales de señal, identidad, invariantes. |
| Casos de uso de aplicación para redes cercanas | ✅ | `ObserveNearbyNetworks` (matching fuera del ViewModel), `RefreshNearbyNetworks`; fakes + tests. |
| Persistencia duradera del Vault (SQLDelight) | ✅ | Módulo `:shared:persistence`; `AndroidSqliteDriver`; tests JVM con SQLite en memoria; esquema versionado. |
| Compensación explícita red + secreto | ✅ | Rollback en create/update-first; borrado sin referencias colgantes (ADR 0007); tests con fakes que fallan. |
| `NearbyViewModel` recableado a `ObserveNearbyNetworks` | ✅ | Matching y clasificación fuera del ViewModel; `stateIn(viewModelScope)`; tests JUnit con `runTest`. |
| Pantalla Nearby + detalle (bottom sheet) + guardar en Vault | ✅ | Assessment por red, alias editable, permisos UX (acciones a Ajustes). |
| Vault UI: listado + detalle + CRUD + secretos | ✅ | Búsqueda/orden/filtro; create manual y desde red detectada; alias/ubicación/notas/secreto; confirmación de borrado; Reveal/Hide/Copy/Replace/Remove sin auto-revelar. |
| Re-detección de red conocida | ✅ | Alias primero en Nearby; `RecordSavedNetworkSighting` fusiona BSSID y actualiza last seen. |

## Pendiente

| Área | Prioridad | Notas |
| --- | --- | --- |
| Persistencia duradera del Vault (SQLDelight) | Hecho | Ver fila en Implementado. |
| Algoritmo de búsqueda más potente | Alta | Nuevas estrategias/optimizadores de priorización; pool de workers con benchmarks (ADR 0006). |
| Pantallas restantes | Alta | Onboarding, Permissions dedicada, Security Analysis detallada, Lab Result como pantalla propia. |
| Revelar secreto con biometría | Media | Arquitectura lista (`RevealSavedNetworkSecret`); no bloquea el CRUD. |
| Tests de Compose UI + instrumentación | Media | ViewModel cubierto; falta UI/androidTest (requiere emulador). |
| Módulo de benchmarks del laboratorio | Media | Sección 29 del enunciado. |
| Targets iOS reales | Baja | Requiere macOS/Xcode. |
