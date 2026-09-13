# ADR — Connected Known-Password Audit (foundation)

- Estado: Accepted
- Fecha: 2026-09-13
- Relacionado: [0006-lab-boundary-and-cancellation](./0006-lab-boundary-and-cancellation.md), [ADR-003-real-wifi-vs-synthetic-lab](./ADR-003-real-wifi-vs-synthetic-lab.md), [13-known-password-audit-walkthrough](../13-known-password-audit-walkthrough.md)

## Contexto

Queremos auditar la resistencia de una **contraseña Wi-Fi conocida** (introducida
o recuperada del Vault) midiendo cuánto tarda el Search Engine **local** en
encontrarla. La red real solo aporta contexto; nunca se autentica contra el AP.

## Decisión

1. **Conexión actual** (PR1): `CurrentWifiConnectionProvider` +
   `NetworkConnectionMatch` + `PasswordAuditEligibilityChecker` viven en
   `:shared:assessment`.
2. **Target encapsulado** (PR2): `EncapsulatedPasswordVerifier` y
   `LabChallenge.withEncapsulatedVerifier(policy, verifier)` en `:shared:lab`.
   El `policy` de planificación es **ciego** al secreto. `SecretStrengthAnalyzer`
   analiza el secreto en un pipeline separado y **no** alimenta al planner.
3. **Automatic planner** (PR3): `AutomaticPasswordAuditPlanner` en `:shared:lab`.
4. **Quick Audit UI** (PR4): presets 30 s / 1 min / 5 min (default 1 min);
   origen Vault diferido / manual; save-to-Vault OFF; modo Automático;
   `ArrowBack`; opciones avanzadas colapsadas + restablecer.
5. **Execution + STOP** (PR5): motor local + cancelación cooperativa.
6. **Results + strength** (PR6): `PasswordAuditResultComposer` con
   Measured / Estimated / Modelled.
7. **Localization + docs** (PR7): strings ES/EN + walkthrough.

El motor verifica candidatos solo vía `CandidateVerifier`; no hay handshakes,
PMKID, deauth ni envío de candidatos al router.

## Consecuencias

- Cambiar el target no cambia el espacio de búsqueda planificado.
- `withKnownSecret` permanece para tests/benchmarks sintéticos; no usarlo para
  auditorías Wi-Fi reales.
- Un usuario novice puede completar el flujo sin abrir opciones avanzadas.

## Measured / Estimated / Modelled

La UI distingue tiempos medidos localmente, estimaciones de calibración/presupuesto
y modelos estructurales. Ninguno se presenta como "tiempo exacto de un atacante"
sin calificar.
