# 10 · Testing

Los tests del dominio y los casos de uso corren en el target `jvm` de KMP:
`./gradlew test` / `jvmTest`.

## CI (FASE 21)

Workflow: `.github/workflows/ci.yml`

| Job | Qué hace |
| --- | --- |
| `jvm` | `ktlintCheck` → `test`/`jvmTest` → `assembleDebug` + `compileDebugAndroidTestKotlin` |
| `android-emulator` | Emulador KVM → `:androidApp:connectedDebugAndroidTest` (matriz API 29 + 35) |

Justificación de APIs: `docs/ci-emulator.md`.

Artifacts (siempre / al fallar JVM): reports JUnit/HTML, logcat **sanitizado**
(`.github/scripts/sanitize-logcat.sh`), timing. Sin cachear Keystore/DB/estado AVD.

## Matriz de cobertura

| Feature | JVM / ViewModel | Compose UI (`androidTest`) | Instrumentación Android | Manual |
| --- | --- | --- | --- | --- |
| Nearby | VM + use cases | Loading…Security CTA | — | Escaneo Wi‑Fi físico |
| Onboarding | VM | First run / skip / replay | — | — |
| Permission Center | VM | Estados + callbacks | Smoke adapter | Diálogos OEM |
| Security Analysis | VM (8 familias) | Progressive disclosure | — | — |
| Vault UI | VM + use cases | List/CRUD/secret UX | — | Clipboard OEM |
| Vault DB | SQLDelight JVM | — | `AndroidSqliteDriver` | — |
| Secretos | Redaction + use cases | Mask UI | `KeystoreSecretVault` + lifecycle | — |
| Lab | Engine + VM | Fake engine UI + STOP | — | Runs largos físicos |
| Quick Audit | `PasswordAuditViewModelTest` (presets, Vault, eligibility, cancel, missing-target) | `PasswordAuditComposeTest` (STOP, found, vault, advanced restore) | — | Flujo físico conectado |
| Known-password domain | `AutomaticPasswordAuditPlannerTest`, `TargetIsolationTest`, `PasswordAuditResultComposerTest` | — | — | — |

## Clasificación `androidTest`

| Clase | Tipo |
| --- | --- |
| `NearbyComposeTest` … `LabComposeTest` | Compose UI (fakes; sin Wi‑Fi físico) |
| `ProductRegressionComposeTest` | FIX-05 regresión producto: i18n packs, nav chrome, Lab guiado + prototipo OPEN/Enterprise/WPA2 STOP→editar |
| `MainActivityRuntimeLocaleTest` | FIX-01A runtime locale ES/EN/SYSTEM + persistencia tras recreate |
| `ComposeHardcodedStringGuardTest` (JVM) | FIX-05 static guard literales Compose user-facing |
| `AppLanguagePreferencesInstrumentedTest` | Persistencia idioma + divergencia ES/EN |
| `PasswordAuditComposeTest` | Quick Audit — STOP bottomBar, found, vault deferred, restore automático |
| `AndroidSqlDelightRepositoryTest` | SQLDelight Android |
| `KeystoreSecretVaultInstrumentedTest` | Keystore real |
| `NetworkSecretLifecycleInstrumentedTest` | Lifecycle red+secreto |
| `AndroidWifiPermissionManagerInstrumentedTest` | Permission adapter smoke |
| `SharedPreferencesCalibrationRepositoryTest` | Calibración durable |

Sin dependencia de APs reales, Internet ni diálogos OEM.

## Localización (testing)

- Post FIX-01: Compose tests usan `R.string.*` / `activity.getString(R.string.*)`.
- `ProductRegressionComposeTest` + `AppLanguagePreferencesInstrumentedTest` + `MainActivityRuntimeLocaleTest` verifican divergencia ES/EN y persistencia runtime.
- `ComposeHardcodedStringGuardTest` evita regresiones de literales hardcoded en pantallas Compose.
- Mensajes dinámicos del `PasswordAuditViewModel` no tienen cobertura i18n completa.

## Cobertura JVM / KMP

ViewModels en `androidApp/src/test`, dominio en `shared/*/…Test`. Destacados
para auditoría: `DefaultPasswordAuditEligibilityCheckerTest`,
`HeuristicSecretStrengthAnalyzerTest`, `PasswordAuditResultComposerTest`,
`AutomaticPasswordAuditPlannerTest`, `TargetIsolationTest`.

## Deferred

- Nightly con API adicional si el coste de la matriz PR lo justifica.
- Tests Compose i18n para pantallas aún hardcoded ES.
