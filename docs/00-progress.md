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
| UX de ejecución del lab | Preview, `DETENER` fijo (bottomBar), límites, resultado + métricas (UX-01). **LAB search-space:** letras Aa, rango min/max (tope suave 16), límites flexibles + hasta DETENER. |
| Lab desde red cercana | **Partial** (Lab Mode ON): CTA Lab + contexto; prototipo precargado; Search Engine sólo en Lab. |
| Lab desde Vault | **Partial** (Lab Mode ON): CTA Lab; seed one-shot si revelada; sin auth a AP. CTA real «Auditar esta red» siempre. |
| Modo guiado del Lab | **Partial** (FIX-04 + FIX-07/08/12): bucle Crear y probar → Iniciar → resultado; familia → perfil PSK/WEP auth-aware; Advanced LocalPrototype usa el mismo planner; PSK ≥ 8; OPEN/Enterprise sin CTA PSK; regresión en JVM + Compose; manual pendiente usuario. |
| Detección de red conectada | **Implemented** (PR1): `CurrentWifiConnectionProvider` + badge «Conectado» en Nearby. |
| RealWifiAudit architecture | **IMPLEMENTED**: scan/target/security/strategy/planner; SE V2 integration; External verifier abstraction. Production external verifier **NOT IMPLEMENTED** (`UnsupportedExternalCandidateVerificationPort` → BackendUnavailable). Real automatic audit **BLOCKED** por ese backend. Sin Vault/known-secret. Guías: docs/15–17. |
| PasswordAudit UI legacy | **Removed** del producto (Screen/ViewModel/DI/nav). Planner/motor siguen en Lab. Domain `VerificationMode.LOCAL_AUDIT` conservado para gate/LAB_NETWORK_VALIDATION tests. |
| Aislamiento del target conocido (Lab) | **Implemented**: `EncapsulatedPasswordVerifier` / `BlackBoxChallengeVerifier` + planners ciegos. |
| Planner automático (Lab) | **Partial** (FIX-06…12): PSK/WEP auth-aware + explainability; Guided/Advanced LocalPrototype; Guided RandomHidden → progressive; pesos heurísticos. Manual UX pending. |
| Dual-mode LOCAL / LAB_NETWORK_VALIDATION (F0–F2) | **Implemented** (domain/gate/orchestrator/adapter). UI PasswordAudit que lo exponía = **removed**; Probe one-shot usa `SINGLE_REAL`. **Physical validated**: pendiente checklist. |
| LAB product integration (F3) | **Implemented** (Code): registro Nearby/Vault, capability UX, sesión/STOP, evidence durable. Entry UI principal = Lab/Probe. |
| Prueba Wi‑Fi real / RealWifiProbe (`SINGLE_REAL`) | **Implemented** (Code): flavor `lab`, `FixedLabAccessPoint`, one-shot — **separado** de RealWifiAudit. **Physical validated**: **MANUAL NOT EXECUTED**. |
| Lab search ejecución / STOP / resultados | **Implemented** en LabScreen (Search Engine local, métricas, cancelación cooperativa, informe resistencia). |
| Calibración sintética + estimación por rangos | **Implemented** (run + persistencia durable SharedPreferences, FASE 22). |
| Paralelismo controlado | **Implemented** (`WorkerAwareLabSearchEngine`); benchmarks formales **Implemented** (FASE 23). |
| Search engine v2 (indexed/scheduler) | **Implemented** (FASE 24): `IndexedCandidateSpace`, `DynamicRangeScheduler`, `IndexedParallelLabSearchEngine`; default multi-worker = V2. |
| Resultado del Lab en UI | **Implemented** integrado en la pantalla Lab (`ResultCard`). Ruta propia = Deferred. |
| Destinos UI | Cercanas · Guardadas · Laboratorio (sólo Lab Mode ON) · Ajustes. Branding visible **WiFiSearcher**. |
| Lab Mode = visibilidad producto | **Implemented**: OFF oculta tab/CTAs/registry/calibración/benchmarks/comparativa LAB; ON las restaura en runtime. |
| CandidateVerifier seam | **Implemented**: LocalSynthetic / LocalKnownSecret / BlackBoxLab / NetworkBackedUnsupported; RealWifiAudit → `ExternalCandidateVerificationPort` (prod Unavailable). |
| Onboarding (primera ejecución) | **Partial**: flujo existe; strings migrados FIX-01; manual pendiente usuario. |
| Permission Center | **Partial**: inventario OK; strings + ArrowBack FIX-01/02; manual pendiente usuario. |
| Análisis de seguridad dedicado | **Partial**: pantalla existe; strings migrados FIX-01; manual pendiente usuario. |
| Tests ViewModel (Nearby, Vault, Lab, Onboarding, Permissions, Security Analysis, RealWifiAudit) | JUnit + `runTest`. |
| Compose UI tests (`androidTest`) | Nearby…Lab + RealWifiAudit localization — fakes; **ejecutados en CI emulador** (FASE 21). |
| Instrumentación Android | SQLDelight / Keystore / lifecycle — **CI emulador** API 29+35. |
| Tooling CI | JVM job + Android emulator job (`docs/ci-emulator.md`). |
| Docs `01`–`13`, ADRs, release checklist, test evidence | Índice vivo aquí; walkthrough known-password en `docs/13-known-password-audit-walkthrough.md`. |

## Auth-aware — hecho vs pendiente (FIX-06…12)

| Hecho (Code+CI) | Pendiente |
| --- | --- |
| `WifiPskProgressiveAuditPolicy` + explainability UI (FIX-09) | Sin crack remoto / diccionarios rockyou |
| `WepHexProgressiveAuditPolicy` (10/26 hex, FIX-10) | Pesos de etapa = heurística fija (calibración = throughput) |
| Guided RandomHidden → `GenericProgressiveAuditPolicy` (FIX-11) | Manual UX dispositivo |
| Advanced LocalPrototype → mismo planner auth-aware (FIX-12) | |
| Lab/Audit WEP + PSK elegibles localmente | |
| OPEN/Enterprise sin CTA shared-password | |
| Regresión JVM + Compose | |

**Code+CI auth-aware (visión local):** cerrado con FIX-12. No hay más FIX numerados
abiertos para charset/etapas por método de auth. Lo que mantiene filas en
**Partial** es el **pase manual del usuario** (y pesos heurísticos conscientes).

## Partial

| Área | Notas |
| --- | --- |
| Localización ES/EN (runtime) | **Partial** (FIX-01 merged [#44](https://github.com/sergio-tr/password-cracker/pull/44), FIX-05 CI green): `AppCompatActivity` + `localeConfig` + recreate; strings migrados; `MainActivityRuntimeLocaleTest` (ES/EN/SYSTEM + recreate), `ComposeHardcodedStringGuardTest`. **No** Implemented sin pase manual usuario. |
| Navegación Material (child screens) | **Partial** (FIX-02 merged [#45](https://github.com/sergio-tr/password-cracker/pull/45), FIX-05 CI green): ArrowBack child screens / sin ArrowBack top-level verificado en `ProductRegressionComposeTest`. Manual pendiente usuario. |
| Prototipo local de red (Lab) | **Partial** (FIX-03A + FIX-03B + FIX-04, FIX-05 CI green): prototipo + bucle guiado; regresión STOP→editar→repetir, OPEN/Enterprise sin PSK en `ProductRegressionComposeTest` + `LabComposeTest`. Manual pendiente usuario. |
| GeoLocation en UI | Dominio/persistencia listos; UI usa sólo `LocationLabel`. |
| Hardening RC | Auditoría #17; pase manual pendiente. |
| Compose / androidTest | Suite en CI emulador (API 29+35). Wi‑Fi físico sigue manual. |
| Historial de auditorías (compare-runs) | Follow-up; no bloquea el informe actual. |

## Deferred

| Área | Notas |
| --- | --- |
| Revelar secreto con biometría | Arquitectura lista (`RevealSavedNetworkSecret`). |
| Lab Result como pantalla propia | Hoy vive en la misma pantalla de ejecución. |
| Sesiones lab resumibles | Pause/Resume + checkpoint (FASE 25). |
| Biometría Vault + hardening | FASE 26–27. |
| Observabilidad local + evidence RC + release eng. | Tras pase manual UX (FIX-01…05 Code+CI ya green). |
| Biometría / iOS / release eng. | Bloqueado por prioridad UX (manual gap audit). |
| Targets iOS reales | Requiere macOS/Xcode; ver `docs/11-ios-readiness.md` (FASE 32). |
