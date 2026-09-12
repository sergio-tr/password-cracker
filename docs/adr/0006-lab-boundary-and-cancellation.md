# ADR 0006 · Frontera del laboratorio y cancelación cooperativa

- Estado: Aceptada
- Fecha: 2026-09-12

## Contexto

1. El laboratorio sintético no debe poder depender de redes reales, ni siquiera
   por accidente desde UI o infraestructura.
2. La cancelación es requisito P0: `Stop -> Cancelled` prácticamente inmediato,
   con emisión de un evento terminal `Cancelled`.

## Decisión

- **Frontera**: `:shared:lab` depende únicamente de `:shared:core`. No declara
  dependencia a `:shared:assessment` ni a Android. La imposibilidad de que
  `LabSearchEngine` referencie `WifiScanner` está garantizada por el grafo de
  módulos de Gradle, no por convención.
- **Cancelación**: el contrato conceptual `LabSearchEngine.run(challenge, plan,
  limits)` se amplía con un parámetro `cancellation: CancellationSignal`. Motivo:
  si se cancelara la coroutine colectora (job.cancel), el motor no podría emitir
  el evento terminal `Cancelled` (emitir tras cancelación no está permitido). Con
  una señal cooperativa, el motor comprueba `isCancelled` como máximo una vez por
  batch y emite `Cancelled` de forma limpia. El batch es pequeño (por defecto
  4096) para que el `Stop` se perciba inmediato.

## Consecuencias

- El motor libera recursos correctamente y no deja búsquedas zombie.
- La cancelación es determinista y testeable sin hilos reales (`CancelAfterPolls`).
- El paralelismo controlado (`ParallelLabSearchEngine`) reutiliza la misma
  señal cooperativa y parte el espacio en rangos disjuntos para no duplicar
  candidatos ni romper contadores. El single-worker sigue siendo la referencia.
