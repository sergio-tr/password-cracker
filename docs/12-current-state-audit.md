# 12 · Current state audit (FASE 17)

Auditoría de realidad del repositorio `password-cracker` a fecha **2026-09-12**.
Fuente de verdad: **código + tests + grafo de módulos + CI**. La documentación
se alinea a esta tabla; si hay conflicto, gana el código.

**Alcance de esta fase:** inspeccionar y reconciliar. **No** se añade funcionalidad.

**Módulos reales** (`settings.gradle.kts`):

| Módulo | Rol |
| --- | --- |
| `:shared:core` | `CombinationCount`, `PlatformCapabilities` |
| `:shared:assessment` | Wi‑Fi real, Vault, assessment, casos de uso |
| `:shared:lab` | Laboratorio sintético (sólo depende de `:shared:core`) |
| `:shared:persistence` | SQLDelight → `SavedNetworkRepository` |
| `:androidApp` | Compose UI, ViewModels, adaptadores Android, Koin |

**CI** (`.github/workflows/ci.yml`): `ktlintCheck` → `test`/`jvmTest` → `assembleDebug`.
Sin emulador / `androidTest`.

---

## Capability matrix

| Capability | Documented status (antes de FASE 17) | Actual code status | Tests | Integrated in UI | Gap | Decision |
| --- | --- | --- | --- | --- | --- | --- |
| Module frontier Real vs Lab | Implemented | **Implemented** — `lab` → `core` only | Compile-time | N/A | — | **Keep** |
| `CombinationCount` (>64 bits) | Implemented | **Implemented** | `CombinationCountTest` | Lab estimates | — | **Keep** |
| Wi‑Fi model + classifier | Implemented | **Implemented** in `:shared:assessment` | Classifier + fixtures | Nearby cards / detail | Docs once said “(core)” | **Keep** (docs fixed) |
| Known-network matcher | Implemented | **Implemented** | `DefaultKnownNetworkMatcherTest` | Via `ObserveNearbyNetworks` | — | **Keep** |
| Security assessment registry | Implemented | **Implemented** (8 strategies) | Registry + strategies tests | Nearby detail (collapsed) | No dedicated analysis screen | **Keep domain**; screen = FASE 19 |
| Vault CRUD + secret ports | Implemented | **Implemented** | Use-case + compensation | Vault screen | Biometry Deferred | **Keep** |
| SQLDelight network persistence | Implemented | **Implemented** (`vault.db`) | JVM `SqlDelight…Test` | Yes | No Android driver instrumented test | **Keep**; instrument = FASE 20 |
| Keystore secret vault | Implemented | **Implemented** | Unit only (no instrument) | Reveal/copy flow | No androidTest | **Keep**; instrument = FASE 20 |
| Nearby scan / permissions UX | Implemented | **Implemented** | ViewModel + use cases | Nearby + Permission Center | Progressive request (not all at launch) | **Keep** |
| Vault UI (search/sort/filter/CRUD/secrets) | Implemented | **Implemented** | `VaultViewModelTest` | Vault tab | No Compose UI tests | **Keep**; UI tests = FASE 20 |
| Sightings (BSSID + lastSeen) | Implemented | **Implemented** | Use-case + Nearby VM | Nearby auto | — | **Keep** |
| Lab domain + SearchLifecycle | Implemented | **Implemented** (8 states) | Transition + challenge tests | Lab screen | — | **Keep** |
| Sequential search engine | Implemented | **Implemented** `DefaultLabSearchEngine` | Critical engine suite | Via `WorkerAware` when workers=1 | — | **Keep** (baseline) |
| Parallel search engine | `00-progress` Implemented; `08` was “future” | **Implemented** `ParallelLabSearchEngine` + `WorkerPoolConfig` | `ParallelLabSearchEngineTest` | Worker chips 1/2/4 | No formal throughput evidence | **Keep**; benchmarks = FASE 23; **docs reconciled** |
| Search plan strategies (5) | Implemented | **Implemented** | Coverage + scorer tests | Strategy chips | — | **Keep** |
| Lab execution UX (preview/STOP/result card) | Implemented (integrated) | **Implemented** on **same** Lab screen (`ResultCard`) | `LabViewModelTest` | Yes | Separate “Lab Result” route Deferred | **Keep integrated result**; optional own screen stays Deferred (FASE deferred, not contradiction) |
| Feasibility analyzer | Implemented | **Implemented** | Plan/feasibility tests | Estimates card | — | **Keep** |
| Calibration service | Implemented | **Implemented** run + refine + invalidation | `SearchCalibrationServiceTest` | Auto on Lab VM init + Settings | — | **Keep** |
| Calibration persistence | Implemented (FASE 22) | **Implemented** `SharedPreferencesCalibrationRepository` | JVM + androidTest | Settings: last / throughput / Recalibrate | No PII stored | **Keep** |
| Benchmark module / dashboard | Implemented (FASE 23) | **Implemented** suite + Settings | `LabBenchmarkServiceTest` | Settings Lab benchmarking | Synthetic only | **Keep** |
| ViewModel unit tests | `00-progress` yes; `10-testing` was “pending” | **Implemented** Nearby/Vault/Lab | 3 files under `androidApp/src/test` | N/A | Doc contradiction | **Docs reconciled** in this audit lineage |
| Compose UI tests | Implemented (FASE 20+21) | **Implemented** + **CI emulator** | androidTest | N/A | Wi‑Fi físico manual | **Keep**; nightly API extra opcional |
| Android instrumentation | Implemented (FASE 20+21) | **Implemented** + **CI emulator** | Keystore/SQLDelight | N/A | — | **Keep** |
| Emulator CI | Missing → Implemented (FASE 21) | **Implemented** API 29+35 | connectedDebugAndroidTest | N/A | Coste matriz | Ver `docs/ci-emulator.md` |
| Onboarding | Deferred → Implemented (FASE 18) | **Implemented** | `OnboardingViewModelTest` | First run + Settings replay | — | **Keep** |
| Permission Center | Deferred → Implemented (FASE 18) | **Implemented** | `PermissionCenterViewModelTest` | Settings → Centro de permisos | Compose UI tests later | **Keep** |
| Security Analysis Detail screen | Deferred → Implemented (FASE 19) | **Implemented** | Family-parameterized VM tests | Nearby detail CTA | Compose UI later | **Keep** |
| GeoLocation UI | Partial | Domain+DB yes; UI `LocationLabel` only | Domain/persistence | Label only | No location picker | Deferred / later product |
| Biometric reveal | Deferred | Not implemented | None | No | Architecture ready | Deferred |
| iOS targets | Deferred | Not present | None | N/A | Needs macOS | Deferred (`docs/11`) |
| Docs coherence | Partial | This audit + reconciled `00`/`08`/`10`/ADRs | N/A | N/A | Was contradictory | **This phase** |

---

## Contradiction resolutions (explicit)

### 1. ViewModel tests: `00-progress` vs `10-testing`

- **Reality:** `NearbyViewModelTest`, `VaultViewModelTest`, `LabViewModelTest` exist.
- **Resolution:** `10-testing.md` lists them under cobertura actual. Status = **Implemented**.

### 2. Parallelism: `00-progress` vs `08-search-engine`

- **Reality:** `ParallelLabSearchEngine` + UI worker selector + DI `WorkerAwareLabSearchEngine`.
- **Resolution:** `08-search-engine.md` describes the pool as implemented; benchmarks formal remain **Partial**. Status of engine = **Implemented**.

### 3. Calibration: “exists” vs “memory only”

- **Reality (FASE 22):** `CalibrationRepository` + `SharedPreferencesCalibrationRepository`; invalidation by engine/strategy/workers/ABI/appVersion/age; Settings UI.
- **Resolution:** **Calibration run + persistence = Implemented**.

### 4. Lab result: integrated vs pending dedicated screen

- **Reality:** terminal outcome + metrics render in `LabScreen` (`ResultCard`). There is **no** separate navigation route.
- **Resolution:** **Integrated lab result = Implemented**. **Dedicated Lab Result screen = Deferred** (product polish, not a missing core capability).

---

## Decisions for subsequent phases

| Phase | Branch | Depends on this audit |
| --- | --- | --- |
| 18 | `feature/onboarding-permissions` | Onboarding + Permission Center (**merged** `#20` → `main`) |
| 19 | `feature/security-analysis-to-main` | Dedicated assessment screen (**PR contra `main`**; no apilar) |
| 20 | `test/android-ui-instrumentation` | Compose + Android instrument |
| 21 | `chore/android-emulator-ci` | Emulator matrix from project SDKs |
| 22 | `feature/persistent-search-calibration` | Durable `CalibrationRepository` |
| 23 | `feature/lab-benchmarking` | Benchmark module + dashboard |
| 24 | `perf/lab-search-engine-v2` | Indexed space / scheduler — **requiere baseline de FASE 23** |
| 25 | `feature/resumable-lab-sessions` | Pause/resume checkpoints (ADR si cambia lifecycle) |
| 26 | `feature/vault-biometric-authorization` | `SecretRevealAuthorizer` port + Android biometrics |
| 27 | `chore/vault-security-hardening` | Clipboard, FLAG_SECURE, backups, redaction |
| 28 | `feature/local-observability` | Ring buffer + Diagnostics + export sanitizado |
| 29 | `chore/automate-rc-evidence` | Evidence bundle bajo `docs/evidence/<version>/` |
| 30 | `chore/android-release-engineering` | R8, signing via env, `docs/release-process.md` |
| 31 | `feature/product-ux-final-pass` | Flujo completo; sin features grandes |
| 32 | `feature/ios-targets` | Solo con macOS/Xcode; ADR UI antes de implementar |

---

## Evidence inventory (tests found)

29 test classes under `**/src/**Test` (excluding `build/`), including domain, use cases, persistence JVM, lab engine/parallel/calibration, and three Android unit ViewModel tests. **Zero** `androidTest` sources.
