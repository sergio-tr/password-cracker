# 17 · RealWifiAudit + Lab black-box

## Estado

```text
RealWifiAudit architecture: implemented
Strategy resolution: implemented
Planner: implemented
Search Engine V2 integration: implemented
External verifier abstraction: implemented
Production external verifier backend: NOT IMPLEMENTED
```

## Real

```text
Nearby
→ RealWifiAudit
→ StrategyResolver
→ Planner
→ capability check
→ Ready | BackendUnavailable
```

- Secreto objetivo **desconocido** para la app.
- Sin Vault / known-secret / EncapsulatedPasswordVerifier / password manual.
- Backend placeholder → `BackendUnavailable` + START disabled (no toast, no outcome de ejecución).

## Lab (independiente)

```text
Search Engine V2
→ BlackBoxChallengeVerifier
→ Found
```

Lab **no** consulta `ExternalCandidateVerificationPort`.

`EncapsulatedPasswordVerifier` permanece solo para Synthetic / Prototype / tests Lab.

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
