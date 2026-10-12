# 01 · Alcance del producto

Nombre visible: **WiFiSearcher** (package/applicationId históricos `wifiauditlab` sin cambio).

```text
WiFiSearcher
├── Real environment (Lab Mode OFF = default product)
│   ├── Nearby (scan real)
│   ├── Vault
│   ├── Security Assessment
│   └── Real password probe
│         → RealNetworkPasswordExecutionPort
│         → Android adapter (may be NotImplemented)
│
└── Lab Mode ON (adds Lab; does not remove Real)
    ├── Synthetic Lab
    ├── Local Network Prototype
    ├── Search Engine V2 → CandidateVerifier (local only)
    ├── Automatic planners / workers / STOP / metrics
    └── Benchmarks / calibration
```

Contrato: el Search Engine local **no** se lanza desde el flujo de red real.
«Probar mi contraseña (solo local)» ya no es el CTA de producto real.

## Objetivo

Aplicación Android didáctica de auditoría Wi-Fi que permite:

- Descubrir redes Wi-Fi visibles y mostrar su información técnica relevante.
- Identificar y **normalizar** sus mecanismos de seguridad/autenticación.
- Explicar al usuario, en lenguaje comprensible, qué significa la configuración.
- Identificar redes previamente guardadas (matching robusto, no por BSSID único).
- Guardar redes conocidas con alias y ubicación descriptiva.
- Gestionar de forma segura credenciales introducidas por el usuario (Vault, CRUD).
- **Probar una contraseña contra la red Wi‑Fi actualmente conectada** (producto real):
  sólo con identidad Exact (BSSID), consentimiento y `RealNetworkPasswordExecutionPort`.
  La implementación Android puede estar pendiente (`NotImplemented`); no usa el
  Search Engine.
- Con **Lab Mode ON**, disponer de un **laboratorio** (Synthetic / Prototype /
  Search Engine V2 / planners / benchmarks / calibration) para estudiar espacios
  de búsqueda locales, con progreso, límites y cancelación — sin mezclarlo con
  el CTA de producto real.

## Dos entornos, una frontera dura

| REAL ENVIRONMENT | LAB ENVIRONMENT |
| --- | --- |
| Nearby / Vault / assessment | Synthetic Lab / Prototype / Search Engine V2 |
| `RealNetworkPasswordExecutionPort` | `CandidateVerifier` local |
| CTA «Probar contraseña en esta red» (Exact) | Planners, workers, STOP, métricas, calibration |
| Adapter Android (puede ser NotImplemented) | Nunca autentica contra el AP desde el motor |

**Prohibido**: conectar Search Engine V2 a `RealNetworkPasswordExecutionPort` o
a autenticación Wi‑Fi real. Lab Mode OFF oculta Lab; no elimina el código.

## No-objetivos

- No se implementan heurísticas orientadas a atacar credenciales reales (sin rockyou
  ni diccionarios de contraseñas filtradas).
- No se realizan conexiones ni intentos de autenticación contra redes externas.
- No se intenta descubrir contraseñas desconocidas de redes cercanas (by design).
- La geolocalización real es opcional y sólo se almacena si el usuario lo pide.
