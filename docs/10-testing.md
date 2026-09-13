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
| Lab | Engine + VM | Fake engine UI | — | Runs largos físicos |
| Quick Audit | VM (Vault/manual, presets, cancel, eligibility, attempt freeze) | — (unit; compose dedicado evitados por flakiness) | — | Flujo físico conectado |

## Clasificación `androidTest`

| Clase | Tipo |
| --- | --- |
| `NearbyComposeTest` … `LabComposeTest` | Compose UI (fakes; sin Wi‑Fi físico) |
| `AndroidSqlDelightRepositoryTest` | SQLDelight Android |
| `KeystoreSecretVaultInstrumentedTest` | Keystore real |
| `NetworkSecretLifecycleInstrumentedTest` | Lifecycle red+secreto |
| `AndroidWifiPermissionManagerInstrumentedTest` | Permission adapter smoke |

Sin dependencia de APs reales, Internet ni diálogos OEM.

## Cobertura JVM / KMP

Ver listado histórico de ficheros en el historial del repo; ViewModels en
`androidApp/src/test`, dominio en `shared/*/…Test`.

## Deferred

- Nightly con API adicional si el coste de la matriz PR lo justifica.
- Suite formal de benchmarks (FASE 23).
