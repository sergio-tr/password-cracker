# 01 · Alcance del producto

Nombre visible: **WiFiSearcher** (package/applicationId históricos `wifiauditlab` sin cambio).

```text
WiFiSearcher
├── Real environment (Lab Mode OFF = default product)
│   ├── Nearby (scan real)
│   ├── Vault
│   ├── Security Assessment
│   └── Real Wi‑Fi audit (SE V2 + external CandidateVerifier; secret unknown)
│         → StrategyResolver / Planner / Orchestrator
│         → Search Engine V2 + CandidateVerifier → External backend
│         → UnsupportedExternalCandidateVerificationPort (placeholder)
│
└── Lab Mode ON (adds Lab; does not remove Real)
    ├── Synthetic Lab
    ├── Local Network Prototype
    ├── Search Engine V2 → CandidateVerifier (local only)
    ├── Automatic planners / workers / STOP / metrics
    └── Benchmarks / calibration
```

Contrato: el Search Engine local **no** se lanza desde el flujo de red real.
No hay CTA de «Probar contraseña» como producto principal.

## Objetivo

Aplicación Android didáctica de auditoría Wi-Fi que permite:

- Descubrir redes Wi-Fi visibles y mostrar su información técnica relevante.
- Identificar y **normalizar** sus mecanismos de seguridad/autenticación.
- Explicar al usuario, en lenguaje comprensible, qué significa la configuración.
- Identificar redes previamente guardadas (matching robusto, no por BSSID único).
- Guardar redes conocidas con alias y ubicación descriptiva.
- Gestionar de forma segura credenciales introducidas por el usuario (Vault, CRUD).
- **Auditar la red Wi‑Fi seleccionada** (producto real): estrategia según
  `SecurityFamily`, plan, Search Engine V2, métricas y STOP. El secreto objetivo
  es **desconocido** para la app; la verificación de candidatos requiere un
  backend externo (`ExternalCandidateVerificationPort`). Sin backend →
  `BackendUnavailable` (START disabled; no se llama a `orchestrator.start()`).
- Con **Lab Mode ON**, disponer de un **laboratorio** (Synthetic / Prototype /
  Search Engine V2 / planners / benchmarks / calibration) para estudiar espacios
  de búsqueda locales — sin mezclarlo con el backend real.

## Dos entornos, una frontera dura

| REAL ENVIRONMENT | LAB ENVIRONMENT |
| --- | --- |
| Nearby / Vault / assessment | Synthetic Lab / Prototype / Search Engine V2 |
| SE V2 + external CandidateVerifier (no known secret) | `CandidateVerifier` local / BlackBox |
| CTA «Auditar esta red» | Planners, workers, STOP, métricas, calibration |
| External verifier placeholder (Unavailable) | Nunca autentica contra el AP desde el motor |

**Prohibido**: known-secret / Vault / EncapsulatedPasswordVerifier en RealWifiAudit.
Lab Mode OFF oculta Lab; no elimina el código.

## No-objetivos

- No se implementan heurísticas orientadas a atacar credenciales reales (sin rockyou
  ni diccionarios de contraseñas filtradas).
- No se realizan conexiones ni intentos de autenticación automáticos contra redes
  externas en esta fase (backend pendiente detrás del marker).
- No se intenta descubrir contraseñas desconocidas de redes cercanas (by design).
- La geolocalización real es opcional y sólo se almacena si el usuario lo pide.
