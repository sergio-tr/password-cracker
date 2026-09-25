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

## Acceso indexado (V2)

`IndexedCandidateSpace` / `OdometerIndexedCandidateSpace` permiten
`candidateAt(index)` con codificación base-N sobre el mismo orden (y seed) que el
odómetro. Los workers saltan a un rango `[startInclusive, endExclusive)` sin
enumerar candidatos previos; dentro del rango se avanza como odómetro. El índice
usa `CombinationCount` (espacios > 64 bits).

## Tamaño del espacio: `CombinationCount`

El espacio puede superar 64 bits, así que no se usan `Int`, `Long` ni `Double`
para contarlo. `CombinationCount` envuelve un entero de precisión arbitraria
(`com.ionspin.kotlin:bignum`, ver ADR 0004) y soporta suma, resta, división,
resto, multiplicación, potencia, comparación, porcentajes y formato:

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

El baseline **determinista de un solo worker** (`DefaultLabSearchEngine`) sigue
siendo la referencia de corrección. Sobre él:

| Versión | Mecanismo | Notas |
| --- | --- | --- |
| V1 | `ParallelLabSearchEngine` + partición estática + `drop` | Referencia; seleccionable vía `LabParallelEngineVersion.V1`. |
| V2 (default multi-worker) | `IndexedParallelLabSearchEngine` + `DynamicRangeScheduler` | Rangos dinámicos, sin gaps/duplicados, cancelable; contadores exactos. |

`WorkerAwareLabSearchEngine` elige baseline (1 worker) o V2 (≥2).
`WorkerPoolConfig.recommendedWorkerCount` combina throughput de calibración +
clamp a `availableProcessors` (override permitido).

### Evidencia micro-benchmark (FASE 24)

Carga sintética: exhaustivo sobre espacio decimal length=2 (100 candidatos),
4 workers donde aplica. Los ms absolutos varían por máquina; la estructura sirve
para comparar en CI local (`SearchEngineV2BenchmarkTest`).

| engine | duration_ms (ejemplo local) | attempts |
| --- | ---: | ---: |
| sequential-baseline | 2 | 100 |
| indexed-sequential | 1 | 100 |
| parallel-v1 | 6 | 100 |
| parallel-v2-indexed | 1 | 100 |

En esta carga V2 no es peor que V1 (y evita el `drop` O(n)); por eso V2 es el
default multi-worker. `IndexedSequentialLabSearchEngine` queda opcional para
comparación; el baseline de corrección sigue siendo `DefaultLabSearchEngine`.

## Planner automático (known-password audit)

`DefaultAutomaticPasswordAuditPlanner` construye un `PasswordAuditPlan` multi-stage
**ciego al target**: recibe `PasswordAuditContext` (aplicabilidad +
`SharedPasswordSearchProfile` cuando aplica), `PasswordAuditPerformanceProfile` y
`PasswordAuditBudget` (al menos duración o intentos). No puede recibir contraseña,
longitud real, ni salida de `SecretStrengthAnalyzer`.

- Política PSK consciente del mecanismo (`WifiPskProgressiveAuditPolicy`): etapas
  con longitud mínima **≥ 8** (reglas WPA/WPA2/WPA3-Personal); alphabets reducidos
  primero (digits → lower → alnum → printable ASCII 8–12); pesos suman 100;
  stage expansivo puede solapar — no se deduplica en memoria.
- `SharedPasswordSearchProfile` (en `:shared:lab`) distingue WPA2 / WPA3 / transición
  / WPA genérico; androidApp mapea `SecurityFamily.toSharedPasswordSearchProfile()`
  en Lab guiado y Password Audit (FIX-07).
- `GuidedAlphabetFitter.fitForWifiPsk` (androidApp) sólo sugiere alfabeto en UI /
  modo Avanzado dentro de printable ASCII; **no** define el espacio de búsqueda guiado
  (sigue viniendo del planner + `WifiPskProgressiveAuditPolicy`).
- `GenericProgressiveAuditPolicy` se conserva para experimentos sintéticos del Lab
  (secretos cortos); el planner de auditoría de producto usa sólo la política PSK.
- `BLIND_CHALLENGE_POLICY`: printable ASCII 8–63 (tope de protocolo; búsqueda
  limitada por presupuesto).
- Presupuesto repartido por pesos centralizados (`StageBudgetAllocator`).
- Workers vía `WorkerPoolConfig.recommendedWorkerCount`; 1 worker → baseline;
  ≥2 → V2.
- Distingue `totalCandidateSpace` vs `budgetedAttemptCapacity` y reutiliza
  `SearchFeasibilityAnalyzer` sobre el espacio presupuestado.
- `AutomaticPlanExplanation` para UI Quick Audit (sin class names / bucket IDs).

Presets de dominio: Quick (30 s) / Standard (1 min, default) / Deep (5 min) / Custom.
UI Quick Audit = `PasswordAuditScreen` con ejecución local, STOP e informe
combinado (walkthrough: `docs/13-known-password-audit-walkthrough.md`).
Explicación novice: «procesos de búsqueda» / etapas / límite — sin nombres internos.

## Known-password audit path

```text
Password (Vault reveal / manual)
    → EncapsulatedPasswordVerifier.encapsulate(secret)
    → LabChallenge.withEncapsulatedVerifier(policy, verifier)
    → AutomaticPasswordAuditPlanner.createPlan(context, performance, budget)
    → WorkerAwareLabSearchEngine.run(…, cancellation)
    → PasswordAuditResultComposer → WifiPasswordAuditResult
```

El planner y el engine **nunca** reciben la contraseña ni salida de
`SecretStrengthAnalyzer`.

## STOP (auditoría y lab)

Cancelación cooperativa: `CancellationController` detiene scheduler y workers.

```text
UI bottomBar DETENER → ViewModel.stop → CancellationController → Cancelled
```

Tests: `DefaultLabSearchEngineTest.cancel_*`, `PasswordAuditViewModelTest.stop*`,
`PasswordAuditComposeTest.stop_alwaysVisible_thenCancellingThenCancelled_attemptsStable`.
Latencia ~200–250 ms (`progressInterval`); intentos congelados tras cancelar.
