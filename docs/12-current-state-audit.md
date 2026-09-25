# 12 · Current state audit

**Snapshot vivo — 2026-09-25 (post FIX-12, `main` @ FIX-06…12 merged).**

Fuente de verdad: **código + tests + grafo de módulos + CI**. La documentación
se alinea a esta tabla; si hay conflicto, gana el código.

**Módulos reales** (`settings.gradle.kts`):

| Módulo | Rol |
| --- | --- |
| `:shared:core` | `CombinationCount`, `PlatformCapabilities` |
| `:shared:assessment` | Wi‑Fi real, Vault, assessment, auditoría known-password |
| `:shared:lab` | Search Engine local (sólo depende de `:shared:core`) |
| `:shared:persistence` | SQLDelight → `SavedNetworkRepository` |
| `:androidApp` | Compose UI, ViewModels, adaptadores Android, Koin |

**CI** (`.github/workflows/ci.yml`): job `jvm` + job `android-emulator` (API 29 + 35)
→ `:androidApp:connectedDebugAndroidTest`. Ver `docs/ci-emulator.md`.

---

## Visión producto (local) — Code+CI

| Necesidad | Status Code+CI |
| --- | --- |
| Gestor redes + passwords (Vault AEAD/Keystore) | **Implemented** |
| Lab cracker local (motor V2, límites, STOP, calibración) | **Implemented** |
| Charset/etapas por método de auth (PSK ≥ 8 printable, WEP hex 10/26) | **Implemented** (FIX-06…12) |
| Guided + Advanced LocalPrototype auth-aware | **Implemented** (FIX-12) |
| Guided RandomHidden etapas sintéticas | **Implemented** (FIX-11) |
| Sin crack remoto / rockyou / handshakes a AP | **Implemented** (by design) |
| Pesos de etapa empíricos ASAP | **No** — heurística fija (consciente) |
| Pase manual UX en dispositivo | **Pendiente usuario** (mantiene Partial en UX) |

**Cierre auth-aware numerado:** no quedan FIX-06…12 abiertos. Siguiente trabajo de
producto es **manual UX** o ítems **Deferred** (biometría, iOS, historial), no más
PRs de código para la visión local auth-aware.

---

## Capability matrix (extracto)

| Capability | Status | Tests | UI |
| --- | --- | --- | --- |
| Module frontier Real vs Lab | **Implemented** | Compile-time | N/A |
| Connected network detection | **Implemented** | `DefaultPasswordAuditEligibilityCheckerTest` | Badge «Conectado» |
| Password audit eligibility (PSK + WEP) | **Implemented** | Eligibility + VM tests | CTA «Auditar contraseña» |
| Known target isolation | **Implemented** | `TargetIsolationTest` | — |
| Automatic planner (PSK + WEP hex) | **Partial\*** (FIX-06…12 Code+CI) | Planner + policy + `LabViewModelTest` Advanced/Guided | Presets Quick/Standard/Deep; explainability FIX-09 |
| Guided + Advanced LocalPrototype | **Implemented** (FIX-12) | `LabViewModelTest.advanced_localPrototype_*` | Mismo planner; overrides alfabeto = UI only |
| Guided RandomHidden progressive | **Implemented** (FIX-11) | `GenericProgressiveSearchPlanBuilderTest`, `LabViewModelTest` | Sin `searchPlanSummary` PSK |
| Advanced RandomHidden | **Implemented** | `LabViewModelTest.advanced_randomHidden_*` | Strategy optimizer |
| Quick Audit UI | **Implemented** | `PasswordAuditComposeTest` | `PasswordAuditScreen` |
| Execution + STOP | **Implemented** | VM + Compose STOP tests | bottomBar `DETENER` |
| Results + strength report | **Implemented** | `PasswordAuditResultComposerTest` | Informe Medido/Estimado/Modelado |
| ES/EN localization | **Partial** | Locale + hardcoded-string guards | Code+CI; manual pending |
| Compare-runs history | **Deferred** | — | Follow-up |
| Compose UI tests | **Implemented** | `androidTest` + regression | CI emulador |
| Android instrumentation | **Implemented** | Keystore/SQLDelight/lifecycle | CI emulador |

\*Partial = pesos de etapa heurísticos + manual UX; el cableado auth-aware está completo.

---

## Correcciones respecto a snapshots anteriores

| Afirmación obsoleta | Realidad actual |
| --- | --- |
| «Sin emulador / androidTest» | CI ejecuta `connectedDebugAndroidTest` en API 29 + 35. |
| «Planner genérico 1–8 dígitos en auditoría producto» | PSK ≥ 8 / WEP hex vía políticas progresivas; genérico sólo Guided RandomHidden. |
| «Advanced LocalPrototype bypasea PSK» (pre FIX-12) | Advanced LocalPrototype usa `AutomaticPasswordAuditPlanner`. |
| Calibración «solo memoria» | `SharedPreferencesCalibrationRepository` (FASE 22). |
| Known-password audit ausente | PR1–PR7 + FIX-06…12; walkthrough en `docs/13-known-password-audit-walkthrough.md`. |

---

## Evidence inventory (tests)

- **JVM/KMP**: dominio assessment + lab + persistence; `PasswordAuditViewModelTest`, `LabViewModelTest`.
- **androidTest**: Compose (Nearby, Vault, Lab, Audit, Onboarding, Permissions,
  Security Analysis, ProductRegression) + instrumentación.
- **Manual**: escaneo Wi‑Fi físico, flujo connected audit — ver `docs/test-evidence.md`
  y `docs/13-manual-ux-gap-audit.md`.
