# 16 · External verifier placeholder

## Estado

```text
External verifier abstraction: implemented
Production external verifier backend: NOT IMPLEMENTED
```

## Ruta

`shared/assessment/src/commonMain/kotlin/com/wifiauditlab/assessment/domain/realaudit/ExternalCandidateVerification.kt`

Clase de producción (única):

`UnsupportedExternalCandidateVerificationPort`

Marker (exactamente una vez en el código):

```text
USER_IMPLEMENTATION_POINT_EXTERNAL_VERIFIER
```

## Comportamiento

- `capability()` → `Unavailable`
- RealWifiAudit `prepare` → `BackendUnavailable`
- START disabled; `orchestrator.start()` no se invoca

No implementar aquí un backend Wi‑Fi ni loops `requestNetwork` por candidato
sin un diseño revisado.

DI (`AppModule`):

```text
single<ExternalCandidateVerificationPort> {
    UnsupportedExternalCandidateVerificationPort()
}
```
