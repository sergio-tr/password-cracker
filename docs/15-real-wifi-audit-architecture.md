# 15 · Arquitectura de auditoría Wi‑Fi real

## Estado

```text
RealWifiAudit architecture: implemented
Strategy resolution: implemented
Planner: implemented
Search Engine V2 integration: implemented
External verifier abstraction: implemented
Production external verifier backend: NOT IMPLEMENTED
```

## Flujo

```text
Nearby
→ RealWifiAudit
→ StrategyResolver
→ Planner
→ capability check
→ Ready | BackendUnavailable
```

Password strategies (con backend disponible):

```text
Ready
→ Search Engine V2
→ ExternalCandidateVerificationPort
```

Sin backend (compilación actual):

```text
BackendUnavailable
→ START disabled
→ orchestrator.start() NO llamado
```

## Principio

```text
Search Engine does NOT know the target secret.

Local Lab:
    hidden secret lives only in BlackBoxChallengeVerifier.

Real mode:
    requires an external verification backend.
```

RealWifiAudit **no** usa Vault password, known-secret, `EncapsulatedPasswordVerifier`,
password manual ni `FixedLabAccessPoint`.

## State machine

```text
CheckingCapability
→ Ready
→ Searching / Preparing / Stopping
→ Completed | Failed

CheckingCapability
→ BackendUnavailable   (estable; START disabled)

CheckingCapability
→ Completed(Unsupported)   (familia no soportada)
```

No hay Preparing infinito.
No hay `BackendUnavailable → START → Completed(Unavailable)`.

## Diagrama

```text
                    Search Engine V2
                           ↓
                       candidate
                           ↓
                   CandidateVerifier
                     /           \
                    /             \
         BlackBox Lab        External backend
        hidden secret            pending
```

Placeholder de producción: `UnsupportedExternalCandidateVerificationPort`
(`USER_IMPLEMENTATION_POINT_EXTERNAL_VERIFIER`).

Ver `docs/16`, `docs/17`.
