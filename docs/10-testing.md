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
| Casos de uso del Vault (CRUD, secretos) | `VaultUseCasesTest` |

## Casos críticos cubiertos

- Cancelar durante la ejecución (`CancelAfterPolls`) → estado `Cancelled` inmediato.
- Cancelar en transición de bucket (plan multi-longitud).
- Límite de duración alcanzado (`AutoAdvancingTimeSource` determinista).
- Límite de intentos alcanzado.
- Secreto encontrado **exactamente** en el límite → `Found`; uno más allá → `LimitReached`.
- Exhausción del espacio sin encontrar → `Completed`.
- Aritmética de espacios enormes (2^100, 95^8, …).
- CRUD del repositorio; borrar red con secreto asociado; actualizar/eliminar secreto.
- Red conocida con BSSID nuevo → `Probable`; matching ambiguo → `Ambiguous`.

## Pendiente

- Tests de ViewModel y de Compose UI.
- Tests de integración Android (scanner, Keystore) e instrumentación.
