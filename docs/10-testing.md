# 10 · Testing

Los tests del dominio y los casos de uso corren en el target `jvm` de KMP, sin
emulador ni Android SDK: `./gradlew test`.

## Cobertura actual (unit, JVM)

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
| ViewModel Onboarding | `OnboardingViewModelTest` (first run / skip / complete / reopen) |
| ViewModel Permission Center | `PermissionCenterViewModelTest` (denied / permanent / service / restored) |

## Casos críticos cubiertos

- Cancelar durante la ejecución (`CancelAfterPolls`) → estado `Cancelled` inmediato.
- Cancelar en transición de bucket (plan multi-longitud).
- Límite de duración alcanzado (`AutoAdvancingTimeSource` determinista).
- Límite de intentos alcanzado.
- Secreto encontrado **exactamente** en el límite → `Found`; uno más allá → `LimitReached`.
- Exhausción del espacio sin encontrar → `Completed`.
- Aritmética de espacios enormes (2^100, 2^256, 95^20, …).
- CRUD del repositorio; borrar red con secreto asociado; actualizar/eliminar secreto.
- Compensación explícita red↔secreto (fallos de create/update).
- Red conocida con BSSID nuevo → `Probable`; matching ambiguo → `Ambiguous`.
- Pool paralelo: mismos hallazgos que el baseline; intentos ≤ espacio.

## Deferred

- Tests de Compose UI.
- Tests de integración Android (scanner, Keystore) e instrumentación (`androidTest`).
- Suite formal de benchmarks (throughput / CPU / latencia de cancelación).
