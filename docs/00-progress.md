# 00 · Estado del proyecto

Documento vivo para controlar qué está implementado y qué queda pendiente. Se
actualiza en cada iteración (misma PR que el cambio, ver política de docs).

Prioridades del producto (contrato): **(1) Fiabilidad**, **(2) Vault seguro y CRUD**,
**(3) UX clara**, **(4) Laboratorio eficiente/cancelable**, **(5) Testing**,
**(6) Performance medida**, **(7) Observabilidad**, **(8) Multiplataforma**.

Leyenda: **Implemented** · **Partial** · **Deferred**.

## Implemented

| Área | Notas |
| --- | --- |
| Build KMP multi-módulo (Gradle 8.11.1, Kotlin 2.1, AGP 8.7.3) | Targets `jvm` + `androidTarget`. |
| Frontera Real/Lab por grafo de módulos | `:shared:lab` sólo depende de `:shared:core`. |
| `CombinationCount` (> 64 bits, formato) | Exacto hasta 2^256; tests incluidos. |
| `PlatformCapabilities` | Android; iOS previsto. |
| Modelo Wi-Fi + `WifiSecurityClassifier` | Fixtures por familia + MFP; vive en `:shared:assessment`. |
| `KnownNetworkMatcher` | Exact / Probable / Unknown / Ambiguous. |
| Security Assessment (Strategy Registry) | 8 estrategias; tests por familia. |
| Vault: modelo, puertos, casos de uso CRUD + secretos | Compensación explícita (ADR 0007). |
| Persistencia Vault (SQLDelight) | `:shared:persistence`; `vault.db`; tests JVM en memoria. |
| Secretos AEAD (Keystore) | Ciphertext en prefs; clave en Keystore. |
| Nearby: observación, matching, assessment, guardar/actualizar | Alias primero; `RecordSavedNetworkSighting`. |
| Vault UI | Listado + detalle + CRUD + secretos (Reveal/Hide/Copy/Replace/Remove). |
| Dominio lab + motor baseline | Lazy, límites, cancelación, métricas, `SearchLifecycle`. |
| Planificador (5 estrategias) + scoring prob/cost | Sin priors de credenciales reales. |
| UX de ejecución del lab | Preview, `DETENER` fijo (bottomBar), límites, resultado + métricas (UX-01). |
| Lab desde red cercana | **Implemented** (UX-02): CTA + `LabNetworkContext`; sin auth a AP. |
| Modo guiado del Lab | **Implemented** (UX-03): defaults automáticos; opciones técnicas colapsadas; mensaje PSK vs no-PSK. |
| Red conectada + elegibilidad auditoría | **Implemented** (PR1): conexión actual + eligibility + CTA Auditar. |
| Aislamiento target de auditoría | **Implemented** (PR2): `EncapsulatedPasswordVerifier` + `LabChallenge.withEncapsulatedVerifier`; strength analyzer separado. |
| Planner automático de auditoría | **Implemented** (PR3): `AutomaticPasswordAuditPlanner` multi-stage, ciego al target; presets Quick/Standard/Deep. |
| Quick Audit UI | **Implemented** (PR4): pantalla Auditoría rápida; presets + explicación; Vault/manual. |
| Ejecución auditoría + STOP | **Implemented** (PR5): Search Engine local + DETENER; verifier encapsulado; sin auth al AP. |
| Calibración sintética + estimación por rangos | **Implemented** (run + persistencia durable SharedPreferences, FASE 22). |
| Paralelismo controlado | **Implemented** (`WorkerAwareLabSearchEngine`); benchmarks formales **Implemented** (FASE 23). |
| Search engine v2 (indexed/scheduler) | **Implemented** (FASE 24): `IndexedCandidateSpace`, `DynamicRangeScheduler`, `IndexedParallelLabSearchEngine`; default multi-worker = V2. |
| Resultado del Lab en UI | **Implemented** integrado en la pantalla Lab (`ResultCard`). Ruta propia = Deferred. |
| Destinos UI | Cercanas · Guardadas · Laboratorio · Ajustes. |
| Onboarding (primera ejecución) | 3 pantallas; Omitir/Continuar; persistido; replay desde Ajustes. |
| Permission Center | Requerido / servicio / opcional; request contextual; Abrir ajustes. |
| Análisis de seguridad dedicado | Resumen, significado, autenticación, hallazgos, recomendaciones defensivas, detalles técnicos. |
| Tests ViewModel (Nearby, Vault, Lab, Onboarding, Permissions, Security Analysis) | JUnit + `runTest`. |
| Compose UI tests (`androidTest`) | Nearby…Lab — fakes; **ejecutados en CI emulador** (FASE 21). |
| Instrumentación Android | SQLDelight / Keystore / lifecycle — **CI emulador** API 29+35. |
| Tooling CI | JVM job + Android emulator job (`docs/ci-emulator.md`). |
| Docs `01`–`12`, ADRs, release checklist, test evidence | Índice vivo aquí; auditoría de realidad en `docs/12-current-state-audit.md`. |

## Partial

| Área | Notas |
| --- | --- |
| GeoLocation en UI | Dominio/persistencia listos; UI usa sólo `LocationLabel`. |
| Hardening RC | Auditoría #17; pase manual pendiente. |
| Compose / androidTest | Suite en CI emulador (API 29+35). Wi‑Fi físico sigue manual. |
| Visión documental única | Completada en FASE 17 (`docs/12-current-state-audit.md`). |

## Deferred

| Área | Notas |
| --- | --- |
| Revelar secreto con biometría | Arquitectura lista (`RevealSavedNetworkSecret`). |
| Lab Result como pantalla propia | Hoy vive en la misma pantalla de ejecución. |
| Sesiones lab resumibles | Pause/Resume + checkpoint (FASE 25). |
| Biometría Vault + hardening | FASE 26–27. |
| Observabilidad local + evidence RC + release eng. | FASE 28–30. |
| UX final pass | FASE 31 (sin features grandes). |
| Targets iOS reales | Requiere macOS/Xcode; ver `docs/11-ios-readiness.md` (FASE 32). |
