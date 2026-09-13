# ADR — Connected Known-Password Audit (foundation)

- Estado: Accepted (parcial — planner automático)
- Fecha: 2026-09-13
- Relacionado: [0006-lab-boundary-and-cancellation](./0006-lab-boundary-and-cancellation.md), [ADR-003-real-wifi-vs-synthetic-lab](./ADR-003-real-wifi-vs-synthetic-lab.md)

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
   El `policy` de planificación es **ciego** al secreto (no usa su longitud ni
   caracteres). `SecretStrengthAnalyzer` analiza el secreto en un pipeline
   separado y **no** alimenta al planner.
3. **Automatic planner** (PR3): `AutomaticPasswordAuditPlanner` /
   `DefaultAutomaticPasswordAuditPlanner` en `:shared:lab`. Inputs permitidos:
   aplicabilidad + performance + budget. Salida: `PasswordAuditPlan` multi-stage
   con explicación novice-friendly. Mismos inputs ⇒ mismo plan; cambiar el
   target password no puede alterar el plan.
4. El motor sigue verificando candidatos solo vía `CandidateVerifier`; no hay
   handshakes, PMKID, deauth ni envío de candidatos al router.

## Consecuencias

- Cambiar el target no cambia el espacio de búsqueda planificado.
- `withKnownSecret` permanece para tests/benchmarks sintéticos; no usarlo para
  auditorías Wi-Fi reales.
- **Automatic planner implemented.** **Quick Audit UI implemented** (entrada de
  contraseña / Vault, presets, explicación del plan). Ejecución con STOP y
  resultado combinado llegan en PRs posteriores.

## Measured / Estimated / Modelled

Reservado: la UI distinguirá tiempos medidos localmente, estimaciones de
calibración y modelos de coste de autenticación (`AuthenticationCostModel`
mínimo en lab). Ninguno se presentará como "tiempo exacto de un atacante" sin
calificar.
