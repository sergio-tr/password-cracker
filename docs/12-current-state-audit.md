# 12 · Current state audit

**Snapshot vivo — 2026-09-13 (post PR6/PR7, rama `feature/password-audit-product-polish`).**

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

## Capability matrix (extracto PR7)

| Capability | Status | Tests | UI |
| --- | --- | --- | --- |
| Module frontier Real vs Lab | **Implemented** | Compile-time | N/A |
| Connected network detection | **Implemented** | `DefaultPasswordAuditEligibilityCheckerTest` | Badge «Conectado» |
| Password audit eligibility | **Implemented** | Eligibility + VM tests | CTA «Auditar contraseña» |
| Known target isolation | **Implemented** | `TargetIsolationTest` | — |
| Automatic planner (auth-aware PSK) | **Partial** (FIX-06/07/08 Code+CI) | `AutomaticPasswordAuditPlannerTest`, `WifiPskProgressiveAuditPolicyTest`, `ProductRegressionComposeTest` | Presets Quick/Standard/Deep; manual UX pending |
| Quick Audit UI | **Implemented** | `PasswordAuditComposeTest` | `PasswordAuditScreen` |
| Execution + STOP | **Implemented** | VM + Compose STOP tests | bottomBar `DETENER` |
| Results + strength report | **Implemented** | `PasswordAuditResultComposerTest` | Informe Medido/Estimado/Modelado |
| ES/EN localization | **Partial** | Audit tests usan `R.string` | Settings picker + audit/nav; Lab/Nearby/Vault/Onboarding ES hardcoded |
| Compare-runs history | **Deferred** | — | Follow-up |
| Compose UI tests | **Implemented** | 16+ clases `androidTest` incl. `ProductRegressionComposeTest` (FIX-05/08) | CI emulador |
| Android instrumentation | **Implemented** | Keystore/SQLDelight/lifecycle | CI emulador |

---

## Correcciones respecto a snapshot anterior (2026-09-12)

| Afirmación obsoleta | Realidad actual |
| --- | --- |
| «Sin emulador / androidTest» | CI ejecuta `connectedDebugAndroidTest` en API 29 + 35. |
| «Zero androidTest sources» | 16+ ficheros bajo `androidApp/src/androidTest`, incl. `PasswordAuditComposeTest`, `ProductRegressionComposeTest`. |
| «Planner genérico 1–8 dígitos en auditoría producto» | `WifiPskProgressiveAuditPolicy`: minLength ≥ 8, perfil WPA2/WPA3/transición; `GenericProgressiveAuditPolicy` sólo Lab sintético. |
| Calibración «solo memoria» | `SharedPreferencesCalibrationRepository` (FASE 22). |
| Known-password audit ausente | PR1–PR7 completos; walkthrough en `docs/13-known-password-audit-walkthrough.md`. |

---

## Evidence inventory (tests)

- **JVM/KMP**: dominio assessment + lab + persistence; `PasswordAuditViewModelTest`.
- **androidTest (13)**: Compose (Nearby, Vault, Lab, Audit, Onboarding, Permissions,
  Security Analysis) + instrumentación (SQLDelight, Keystore, lifecycle, permissions,
  calibration prefs).
- **Manual**: escaneo Wi‑Fi físico, flujo connected audit — ver `docs/test-evidence.md`.
