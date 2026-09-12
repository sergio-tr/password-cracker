# 07 · Synthetic Security Lab

Laboratorio **totalmente desacoplado** de redes reales. Genera un secreto oculto
local y estudia algoritmos de exploración del espacio de candidatos.

## Flujo conceptual

```
LabChallenge -> SearchPlanOptimizer -> LabSearchPlan -> LabSearchEngine -> LabSearchResult
                                                            |
                    CandidateSource / CandidateVerifier / SearchMetricsCollector / CancellationController
```

## Interfaces

```kotlin
interface LabSearchStrategy {
    val id: SearchStrategyId
    fun supports(challenge: LabChallenge): Boolean
    suspend fun createPlan(challenge: LabChallenge, limits: SearchLimits): LabSearchPlan
}

interface LabSearchEngine {
    fun run(
        challenge: LabChallenge,
        plan: LabSearchPlan,
        limits: SearchLimits,
        cancellation: CancellationSignal,
    ): Flow<LabSearchEvent>
}
```

> Nota de diseño: el `run` conceptual del enunciado se amplió con un
> `CancellationSignal` (ver ADR 0006), porque emitir un evento terminal
> `Cancelled` es imposible si se cancela la coroutine colectora.

## Eventos

`Preparing`, `Started`, `Progress`, `CandidateFound`, `LimitReached`,
`Cancelled`, `Completed`, `Failed`.

## Aislamiento

- El secreto de `LabChallenge` es privado: no se expone por getters, `toString`,
  logs ni serialización. La verificación se hace vía `asVerifier()`.
- Revelar el candidato encontrado en `LabSearchResult` es intencionado: es el
  objetivo didáctico y se trata de un secreto **sintético**, nunca una credencial
  real del Vault.
