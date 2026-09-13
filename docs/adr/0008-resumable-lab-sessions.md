# ADR 0008 · Sesiones de laboratorio pausables y reanudables

- Estado: Aceptada
- Fecha: 2026-09-13

## Contexto

El laboratorio sintético ya soporta cancelación cooperativa (`Stop -> Cancelled`,
ADR 0006). FASE 25 exige poder **pausar** una búsqueda larga y **reanudarla** más
tarde sin tratar la pausa como cancelación, sin saltar/duplicar rangos relevantes
y sin persistir el secreto sintético en claro cuando hay seed.

## Decisión

1. **Estados nuevos**: `Pausing` y `Paused` en `SearchState` / `SearchLifecycle`.
   `Paused` no es un terminal definitivo: puede volver a `Preparing` (resume) o a
   `Cancelled`/`Idle` (stop/abandonar). `Cancelled` sigue significando solo
   abandono explícito.
2. **Señal distinta**: `PauseSignal` / `PauseController` aparte de
   `CancellationSignal`. El motor emite `LabSearchEvent.Paused(metrics, cursor)`.
3. **Cursor exacto**: `LabSearchCursor` guarda `sessionId`, bucket, siguiente
   índice en el bucket, intentos y tiempo activo acumulado. El baseline y V2
   indexado reanudan con salto base-N (`OdometerIndexedCandidateSpace`) o
   `DynamicRangeScheduler(initialNextStart=…)`.
4. **Checkpoint**: `LabSessionCheckpoint` persiste definición reproducible del
   reto (`seed` + alfabeto + longitudes + id), estrategia, versión de plan,
   límites, cursor, métricas, workers y `engineVersion`. Puerto
   `LabSessionRepository` en `:shared:lab`; adaptador Android
   `SharedPreferencesLabSessionRepository` (prefs lab-only, fuera del vault).
5. **UI**: en ejecución `PAUSE` + `STOP`; en pausa `RESUME` + `STOP`. Resume
   rechaza cambio silencioso de estrategia o plan incompatible.

## Consecuencias

- Pause y cancel quedan semánticamente separados y testeables.
- Reiniciar el proceso puede restaurar una sesión `Paused`.
- Sin seed no se acepta pause/persistencia (evitar secreto en claro).
- V1 paralelo pausa en frontera de bucket o delega al baseline al reanudar; V2
  es el camino multi-worker por defecto.
