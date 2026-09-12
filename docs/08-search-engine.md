# 08 · Motor de búsqueda

## Generación de candidatos

`CandidateSpace` describe un slice (alfabeto, longitud, tamaño exacto) sin
materializarlo. `OdometerCandidateSource` enumera ese espacio como un
**cuentakilómetros** perezoso:

- **lazy** e **incremental** (`Sequence<String>`);
- **memoria O(longitud)**, nunca O(espacio de búsqueda);
- **determinista**: orden natural del alfabeto, o permutación reproducible cuando
  hay `seed` (Fisher–Yates sembrado).

No se materializa el espacio: el `SearchPlanOptimizer` construye *buckets*
disjuntos (uno por longitud). Las estrategias (`UniformBaseline`,
`LengthPrioritized`, `TieredAlphabet`, `SyntheticProbabilityWeighted`,
`AdaptiveSynthetic`) solo cambian orden y puntuación. El score conceptual es
`probabilidad sintética / coste estimado`; los pesos viven en la estrategia, no
en el engine. Las dos estrategias ponderadas usan únicamente la distribución
declarada en `LabSecretPolicy`, nunca datasets de credenciales reales.

## Tamaño del espacio: `CombinationCount`

El espacio puede superar 64 bits, así que no se usan `Int`, `Long` ni `Double`
para contarlo. `CombinationCount` envuelve un entero de precisión arbitraria
(`com.ionspin.kotlin:bignum`, ver ADR 0004) y soporta suma, multiplicación,
potencia, comparación, porcentajes y formato:

```
12,340        8.2 M        17.4 B        2.3 × 10^24
```

El valor exacto se conserva siempre (`toExactString()`); la UI puede mostrar la
forma abreviada (`toAbbreviatedString()`).

## Límites (`SearchLimits`)

```kotlin
data class SearchLimits(
    val maxDuration: Duration?,
    val maxAttempts: CombinationCount?,
    val progressInterval: Duration,
    val batchSize: Int,
)
```

`maxDuration == null && maxAttempts == null` es **INVALID**: no se puede arrancar
una búsqueda ilimitada por accidente (validado en el constructor y en
`SearchLimits.validate`).

## Métricas y frecuencias

Las métricas se agregan **por batch**, nunca por candidato. Se separa la
frecuencia de proceso de candidatos de la frecuencia de refresco de UI
(`progressInterval`, configurable, p. ej. 250 ms). Se exponen: estado, intentos,
tiempo, intentos/segundo, bucket actual, espacio total, porcentaje procesado y
estimación restante cuando es calculable.

## Viabilidad y estimación

`SearchFeasibilityAnalyzer` clasifica un plan como `Reasonable`, `Expensive`,
`Impractical` o `Invalid` usando `SearchPerformanceEstimator` (throughput
configurable, calibrable en el dispositivo). La UI nunca presenta una búsqueda de
años como una operación normal.

## Paralelismo

Se implementa primero un baseline **determinista de un solo worker** con tests y
benchmarks. El pool de workers controlado queda para una fase posterior,
manteniendo cancelación, evitando duplicados y contadores thread-safe (ver
`docs/29`/benchmarking y ADR 0006).
