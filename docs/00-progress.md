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
| Lab desde red cercana | **Partial** (UX-02 + FIX-03/04): CTA + contexto solo lectura + prototipo local en Lab; FIX-03/04 merged, CI green; manual pendiente usuario. |
| Modo guiado del Lab | **Partial** (FIX-04 + FIX-07/08): bucle Crear y probar → Iniciar → resultado con acciones Editar/Cambiar/Repetir; familia → perfil PSK auth-aware; PSK ≥ 8; OPEN/Enterprise sin CTA PSK; regresión FIX-08 en JVM + Compose; manual pendiente usuario. |
| Detección de red conectada | **Implemented** (PR1): `CurrentWifiConnectionProvider` + badge «Conectado» en Nearby. |
| Elegibilidad de auditoría de contraseña | **Implemented** (PR1): `PasswordAuditEligibilityChecker`; WPA/WPA2/WPA3 Personal; CTA «Auditar contraseña». |
| Aislamiento del target conocido | **Implemented** (PR2): `EncapsulatedPasswordVerifier` + `LabChallenge.withEncapsulatedVerifier`; `SecretStrengthAnalyzer` separado del planner. |
| Planner automático de auditoría | **Partial** (FIX-06…11): PSK auth-aware + explainability (FIX-09); **FIX-10:** WEP hex local (`WepHexProgressiveAuditPolicy`, 10/26); **FIX-11:** Guided RandomHidden usa `GenericProgressiveSearchPlanBuilder`; Advanced RandomHidden sigue el strategy optimizer; pesos = heurísticos. Manual UX pending. |
| Quick Audit UI | **Implemented** (PR4): `PasswordAuditScreen`; Vault diferido / manual; duración 30 s/1 min/5 min; modo Automático; `ArrowBack`; avanzado colapsado. |
| Ejecución de auditoría | **Implemented** (PR5): Search Engine local; métricas agregadas; `onCleared` cancela sesión. |
| STOP / cancelación (auditoría) | **Implemented** (PR5): `DETENER` en `Scaffold.bottomBar`; cancelación cooperativa. |
| Resultados de auditoría | **Implemented** (PR6): `WifiPasswordAuditResult`; config Wi‑Fi vs contraseña separados; recomendaciones; guía «Cómo mejorarla». |
| Informe de resistencia de contraseña | **Implemented** (PR6): `PasswordResistanceRating`; evidencia Medido/Estimado/Modelado. |
| Tests flujo novice (auditoría) | **Implemented** (PR7): `PasswordAuditComposeTest` — STOP + found + vault + restore automático; `PasswordAuditViewModelTest`. |
| Calibración sintética + estimación por rangos | **Implemented** (run + persistencia durable SharedPreferences, FASE 22). |
| Paralelismo controlado | **Implemented** (`WorkerAwareLabSearchEngine`); benchmarks formales **Implemented** (FASE 23). |
| Search engine v2 (indexed/scheduler) | **Implemented** (FASE 24): `IndexedCandidateSpace`, `DynamicRangeScheduler`, `IndexedParallelLabSearchEngine`; default multi-worker = V2. |
| Resultado del Lab en UI | **Implemented** integrado en la pantalla Lab (`ResultCard`). Ruta propia = Deferred. |
| Destinos UI | Cercanas · Guardadas · Laboratorio · Ajustes (labels nav localizados; cuerpos de pantallas ver Partial). |
| Onboarding (primera ejecución) | **Partial**: flujo existe; strings migrados FIX-01; manual pendiente usuario. |
| Permission Center | **Partial**: inventario OK; strings + ArrowBack FIX-01/02; manual pendiente usuario. |
| Análisis de seguridad dedicado | **Partial**: pantalla existe; strings migrados FIX-01; manual pendiente usuario. |
| Tests ViewModel (Nearby, Vault, Lab, Onboarding, Permissions, Security Analysis, Password Audit) | JUnit + `runTest`. |
| Compose UI tests (`androidTest`) | Nearby…Lab + **Password Audit** — fakes; **ejecutados en CI emulador** (FASE 21). |
| Instrumentación Android | SQLDelight / Keystore / lifecycle — **CI emulador** API 29+35. |
| Tooling CI | JVM job + Android emulator job (`docs/ci-emulator.md`). |
| Docs `01`–`13`, ADRs, release checklist, test evidence | Índice vivo aquí; walkthrough known-password en `docs/13-known-password-audit-walkthrough.md`. |

## Auth-aware — hecho vs pendiente (FIX-06…11)

| Hecho (Code+CI) | Pendiente |
| --- | --- |
| `WifiPskProgressiveAuditPolicy` + explainability UI (FIX-09) | Sin crack remoto / diccionarios rockyou |
| `WepHexProgressiveAuditPolicy` (10/26 hex, FIX-10) | Pesos de etapa = heurística fija (calibración = throughput) |
| Guided RandomHidden → `GenericProgressiveAuditPolicy` (FIX-11) | Advanced LocalPrototype puede bypassar política PSK (FIX-12) |
| Lab/Audit WEP + PSK elegibles localmente | Manual UX dispositivo |
| OPEN/Enterprise sin CTA shared-password | |
| Regresión JVM + Compose | |

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
| Observabilidad local + evidence RC + release eng. | Bloqueado hasta FIX-01…05. |
| Biometría / iOS / release eng. | Bloqueado por prioridad UX (manual gap audit). |
| Targets iOS reales | Requiere macOS/Xcode; ver `docs/11-ios-readiness.md` (FASE 32). |
