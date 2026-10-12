# ADR-005 · Fixed Access Point One-Shot Credential Validation

- Estado: Accepted
- Fecha: 2026-10-12
- Relacionado: [ADR-004](./ADR-004-authorized-ap-lab-test.md),
  [ADR-003](./ADR-003-real-wifi-vs-synthetic-lab.md),
  [14-authorized-lab-validation-plan](../14-authorized-lab-validation-plan.md)

Código: `VerificationMode.SINGLE_REAL_CREDENTIAL_VALIDATION` · producto **Prueba Wi‑Fi real**.

## Contexto

Tras F0–F4, el stack `LabValidationSessionOrchestrator` + `AndroidNetworkValidationAdapter`
permite **una** solicitud local-only contra la red conectada y registrada.

Se necesita una superficie **didáctica** más estricta: probar **una** contraseña
introducida a mano contra **un** AP personal de laboratorio fijo (SSID+BSSID),
sin Search Engine, sin secuencia de candidatos y sin reutilizar el registry
genérico como allowlist.

## Decisión

| Aspecto | Decisión |
| --- | --- |
| Modo | `SINGLE_REAL_CREDENTIAL_VALIDATION` (además de `LOCAL_AUDIT` / `LAB_NETWORK_VALIDATION`) |
| Allowlist | `FixedLabAccessPoint` (exactamente un AP; SSID **y** BSSID) |
| Origen config | `LAB_WIFI_SSID` / `LAB_WIFI_BSSID` en `local.properties` → BuildConfig del flavor **`lab`** |
| Flavor | Feature compilada solo en `labDebug` / `labRelease` (`REAL_WIFI_PROBE_ENABLED=true`) |
| Producción (`standard`) | Feature ausente / Unavailable |
| Stack | Reutiliza gate, orquestador, adapter, credential, evidence, STOP — **no** segundo stack |
| Intentos | Un click → una sesión → máximo un `requestNetwork` → resultado terminal |
| Contraseña | Solo runtime; nunca BuildConfig, Vault automático, logs, evidence ni SavedState |
| Resultados | Tipados; `RequestUnavailable` ≠ contraseña incorrecta |

Arquitectura conceptual:

```text
production (standard)
    LOCAL_AUDIT
    synthetic Lab
    LAB_NETWORK_VALIDATION (registry)

lab flavor
    +
    SINGLE_REAL_CREDENTIAL_VALIDATION (FixedLabAccessPoint)
```

## Frontera más fuerte que el registry

`AuthorizedLabNetworkStore` admite redes marcadas por el usuario (SSID+familia).
`FixedLabAccessPoint` exige identidad exacta SSID+BSSID del BuildConfig y
conexión actual al mismo AP. No hay wildcard, pattern ni «otra red registrada».

## Consecuencias

- CI ensambla `standardDebug` (tests instrumentados) y `labDebug` (compilación de la feature).
- Walkthrough físico documentado como **MANUAL NOT EXECUTED** hasta evidencia en dispositivo.
- `LOCAL_AUDIT` / Search Engine V2 **no** usan el adapter como `CandidateVerifier`.

## Alternativas rechazadas

- Reutilizar solo el registry sin BSSID fijo.
- Exponer la feature en builds `standard`.
- Bucle de candidatos / SearchPlan sobre el AP real.
