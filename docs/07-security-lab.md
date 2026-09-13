# 07 · Security Lab

El Search Engine funciona exclusivamente de forma local. Puede utilizarse con
challenges sintéticos; con un **prototipo de red local** configurable en la UI
del Lab; y con contraseñas conocidas aportadas por el usuario para auditorías
locales. Nunca envía candidatos a redes externas ni realiza autenticaciones
contra access points (sin handshakes, PMKID, deauth ni envío al router).

Modos de verificación:

- **Sintético aleatorio** — `LabChallenge.withHiddenSecret` (secreto oculto
  generado localmente; modo por defecto del Lab).
- **Prototipo local** — perfil Wi-Fi sintético (SSID, familia PSK, banda/estándar
  opcionales) + contraseña objetivo en memoria; verificación vía
  `EncapsulatedPasswordVerifier` + `LabChallenge.withEncapsulatedVerifier`.
- **Known-password audit** — mismo encapsulado en Password Audit (red real
  conectada); `withKnownSecret` queda reservado a tests/benchmarks.

La UI puede contextualizar el experimento con una red observada
(`LabNetworkContext` en androidApp): muestra SSID/familia/banda y un banner de
«Simulación local» / auditoría local, pero **nunca** conecta `LabSearchEngine`
ni `CandidateSource` a autenticación Wi‑Fi real.

Garantía de módulos: `:shared:lab` **no** depende de `:shared:assessment`.

## Flujo conceptual

```
LabChallenge -> SearchPlanOptimizer / AutomaticPasswordAuditPlanner
        -> LabSearchPlan -> LabSearchEngine -> LabSearchResult
                                |
    CandidateSource / CandidateVerifier / SearchMetricsCollector / CancellationController
```

Pipelines separados para auditoría de contraseña conocida:

```
Known password
    |
    +--------> EncapsulatedPasswordVerifier   (motor)
    |
    +--------> SecretStrengthAnalyzer         (assessment; no alimenta al planner)

Network audit context + performance + budget
        |
        v
AutomaticPasswordAuditPlanner  (ciego al target)
        |
        v
PasswordAuditPlan
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

interface AutomaticPasswordAuditPlanner {
    fun createPlan(
        context: PasswordAuditContext,
        performance: PasswordAuditPerformanceProfile,
        budget: PasswordAuditBudget,
    ): PasswordAuditPlanResult
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
  logs ni serialización. La verificación se hace vía `asVerifier()` /
  verifier encapsulado.
- Revelar el candidato encontrado en un lab **sintético** es intencionado
  (objetivo didáctico). En auditoría de contraseña conocida, el resultado de
  «encontrado / no encontrado en presupuesto» se presentará sin confundirlo con
  un ataque al AP.

## Quick Audit UI

Flujo novice (sin opciones avanzadas):

```text
Cercanas → red conectada elegible → Auditar contraseña
→ Vault o manual → Automático → duración 1 min → INICIAR
```

Presupuestos UI: 30 s / 1 min / 5 min / Personalizado (default **1 min**).
El modo Avanzado está colapsado; incluye «Restablecer configuración automática».

Tras la ejecución, el informe separa **configuración Wi‑Fi** y **resistencia de
contraseña**, etiqueta Medido/Estimado/Modelado y ofrece recomendaciones + guía
«Cómo mejorarla» (sin login al router).

## Quick Audit — ejecución y STOP

Al iniciar, la UI muestra métricas agregadas (intentos, tiempo, velocidad, etapa,
progreso de presupuesto) y un `DETENER` fijo en `Scaffold.bottomBar`.

Cancelación:

```text
UI → ViewModel.stop → CancellationController → engine/workers/scheduler → Cancelled
```

Latencia: el motor observa cancelación en límites de batch (`progressInterval`
≈ 250 ms por defecto; Quick usa 200 ms). No se espera un bucket entero enorme;
el tradeoff es throughput vs respuesta percibida — 250 ms es el compromiso
validado también en el Lab.

Lifecycle: rotación/recomposición no reinicia la búsqueda (ViewModel retenido).
`onCleared` cancela la sesión activa; Pause/Resume queda deferred (sesiones
resumibles).

