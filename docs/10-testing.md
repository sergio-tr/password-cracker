# 10 · Testing

Los tests del dominio y los casos de uso corren en el target `jvm` de KMP, sin
emulador ni Android SDK: `./gradlew test` / `jvmTest`.

## Matriz de cobertura (FASE 20)

| Feature | JVM / ViewModel | Compose UI (`androidTest`) | Instrumentación Android | Missing / manual |
| --- | --- | --- | --- | --- |
| Nearby | `NearbyViewModelTest`, `NearbyUseCasesTest` | Loading, empty, results, known/unknown, permission, location, throttled, error, refresh, detail, save, Security Analysis CTA | — | Escaneo Wi‑Fi físico / OEM |
| Onboarding | `OnboardingViewModelTest` | First run, 1→2→3, skip, completed, Settings replay | — | — |
| Permission Center | `PermissionCenterViewModelTest` | Granted / Missing / permanent denial / location disabled + acciones | Smoke `AndroidWifiPermissionManager` | Diálogos OEM de permiso |
| Security Analysis | `SecurityAnalysisViewModelTest` (8 familias) | Mismas familias + progressive disclosure técnico | — | — |
| Vault UI | `VaultViewModelTest`, use cases | Empty, list, search, filter, sort, create, detail, alias, secret hide/reveal, delete confirm | — | Clipboard OEM |
| Vault persistencia | `SqlDelight…Test` (JVM memoria) | — | `AndroidSqliteDriver` CRUD + reopen | — |
| Secretos | Use cases + `VaultModelTest` redaction | UI mask only (sin plaintext en asserts) | `KeystoreSecretVault` P0 + lifecycle red↔secret | — |
| Lab | Engine suite + `LabViewModelTest` | Config, feasibility, found, STOP/cancel, LimitReached (engine fake) | — | Runs largos en dispositivo |
| Wifi mapper | Classifier fixtures JVM | — | — | Mapper con tipos framework + redes reales |

## Cobertura JVM / KMP (unit)

| Área | Fichero |
| --- | --- |
| Aritmética de combinaciones (incl. > 64 bits) | `CombinationCountTest` |
| Límites de búsqueda (incl. ilimitada = inválida) | `SearchLimitsTest` |
| Generación lazy y determinista de candidatos | `OdometerCandidateSourceTest` |
| Planificador y viabilidad (Reasonable/Expensive/Impractical) | `SearchPlanAndFeasibilityTest` |
| Motor de búsqueda | `DefaultLabSearchEngineTest` |
| Clasificador de seguridad | `WifiSecurityClassifierTest` |
| Matcher de redes conocidas | `DefaultKnownNetworkMatcherTest` |
| Registro de estrategias de assessment | `SecurityAssessmentRegistryTest` |
| Casos de uso del Vault (CRUD, secretos, query, sightings) | `VaultUseCasesTest` |
| Persistencia SQLDelight (JVM) | `SqlDelightSavedNetworkRepositoryTest` |
| Estrategias de plan / scoring | `SearchStrategyCoverageTest`, `PlanScorerTest` |
| Paralelismo controlado | `ParallelLabSearchEngineTest` |
| Calibración / rangos | `SearchCalibrationServiceTest` |
| ViewModel Nearby | `NearbyViewModelTest` (`androidApp/src/test`) |
| ViewModel Vault | `VaultViewModelTest` |
| ViewModel Lab | `LabViewModelTest` |
| ViewModel Onboarding | `OnboardingViewModelTest` |
| ViewModel Permission Center | `PermissionCenterViewModelTest` |
| ViewModel Security Analysis | `SecurityAnalysisViewModelTest` |

## Compose UI (`androidApp/src/androidTest`)

| Área | Fichero | Notas |
| --- | --- | --- |
| Nearby | `NearbyComposeTest` | Fakes de scanner/repo; mensaje throttled visible |
| Onboarding | `OnboardingComposeTest` | Fake prefs |
| Permission Center | `PermissionCenterComposeTest` | Usa `PermissionCenterContent` (sin Koin) |
| Security Analysis | `SecurityAnalysisComposeTest` | Parametrizado por familia |
| Vault | `VaultComposeTest` | Secreto sintético `ui-test-secret-aa` |
| Lab | `LabComposeTest` | Engine scripted / hanging |

Estado de ejecución en este entorno: ver `docs/test-evidence.md`
(**IMPLEMENTED BUT NOT EXECUTED** si no hay emulador).

## Instrumentación Android (`androidApp/src/androidTest`)

| Área | Fichero |
| --- | --- |
| SQLDelight + `AndroidSqliteDriver` | `AndroidSqlDelightRepositoryTest` |
| Keystore AEAD | `KeystoreSecretVaultInstrumentedTest` |
| Lifecycle red + secreto | `NetworkSecretLifecycleInstrumentedTest` |
| Permission manager smoke | `AndroidWifiPermissionManagerInstrumentedTest` |

## Casos críticos cubiertos (dominio)

- Cancelar durante la ejecución → `Cancelled` inmediato.
- Límites de duración / intentos; Found exactamente en el límite.
- Compensación explícita red↔secreto.
- Pool paralelo: mismos hallazgos que el baseline.

## Deferred / FASE 21+

- Ejecución automática de `androidTest` en CI con emulador (FASE 21).
- Suite formal de benchmarks (FASE 23).
- Escaneo Wi‑Fi físico: smoke manual únicamente.
