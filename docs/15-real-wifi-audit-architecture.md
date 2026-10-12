# 15 · Arquitectura de auditoría Wi‑Fi real

**Estado:** Architecture ready / platform execution backend pending.

Orquestación, estrategia, plan, UI y métricas están cerrados en esta fase.
La ejecución experimental de plataforma queda detrás de
`USER_IMPLEMENTATION_POINT_REAL_WIFI_AUDIT` (`NotImplemented`).

## Flujo

```text
NearbyScreen
  RealWifiAuditSection → R.string.real_audit_cta ("Auditar esta red")
  onOpenRealWifiAudit(NearbyItem)
↓
MainActivity
  RealWifiAuditTargetStore.set(realWifiAuditTargetFromNearby(item))
  navigate(Routes.REAL_WIFI_AUDIT)
↓
RealWifiAuditScreen
↓
RealWifiAuditViewModel
  prepare(network, securityProfile)
  start() / stop()
↓
RealWifiAuditOrchestrator
↓
RealWifiAuditStrategyResolver   (SecurityFamily → strategy)
↓
RealWifiAuditPlanner            (plan: network + profile + strategy + limits)
↓
RealWifiAuditBackend            (contrato assessment, fuera de :shared:lab)
↓
AndroidRealWifiAuditBackend
↓
USER_IMPLEMENTATION_POINT_REAL_WIFI_AUDIT
```

## Diagrama

```text
                   Wi-Fi seleccionada
                           │
                           ▼
                 Security assessment
                           │
                           ▼
                  StrategyResolver
                           │
           ┌───────────────┼───────────────┐
           │               │               │
        Legacy          PSK/Personal      SAE/...
           │               │               │
           └───────────────┼───────────────┘
                           ▼
                       AuditPlan
                           │
                           ▼
                    Orchestrator
                           │
                           ▼
                  RealWifiAuditBackend
                           │
                           ▼
             AndroidRealWifiAuditBackend
                           │
                           ▼
       USER_IMPLEMENTATION_POINT_REAL_WIFI_AUDIT
```

En paralelo (Lab Mode):

```text
Lab Mode
   ↓
Search Engine V2
   ↓
CandidateVerifier local
```

`RealWifiAuditBackend` **no** está conectado a `CandidateVerifier`.
No hay strings mágicos ni metadata provisional que controlen esa frontera.

## Mapping SecurityFamily → Strategy

| SecurityFamily | Strategy |
| --- | --- |
| OPEN | OpenNetworkAssessment |
| WEP | LegacyPersonalKeyAudit |
| WPA_PERSONAL | LegacyPersonalKeyAudit |
| WPA2_PERSONAL | PersonalPskAudit |
| WPA3_PERSONAL | PersonalSaeAudit |
| WPA2_WPA3_PERSONAL | TransitionPersonalAudit |
| WPA2_ENTERPRISE | EnterpriseAssessment |
| WPA3_ENTERPRISE | EnterpriseAssessment |
| OWE | EnhancedOpenAssessment |
| PASSPOINT | PasspointAssessment |
| DPP | DppAssessment |
| UNKNOWN | Unsupported |

## Rutas de código

| Pieza | Ruta |
| --- | --- |
| CTA Nearby | `androidApp/.../ui/nearby/NearbyScreen.kt` → `RealWifiAuditSection` |
| Nav | `androidApp/.../MainActivity.kt` → `Routes.REAL_WIFI_AUDIT` |
| Store | `androidApp/.../ui/realaudit/RealWifiAuditModels.kt` → `RealWifiAuditTargetStore` |
| Screen | `androidApp/.../ui/realaudit/RealWifiAuditScreen.kt` |
| ViewModel | `androidApp/.../ui/realaudit/RealWifiAuditViewModel.kt` |
| Dominio | `shared/assessment/.../domain/realaudit/` |
| Backend Android | `androidApp/.../wifi/AndroidRealWifiAuditBackend.kt` |

## Lab Mode

- **OFF**: Cercanas / Guardadas / Ajustes; auditoría real disponible.
- **ON**: lo anterior + Laboratorio (Synthetic, Guided, Advanced, Local Prototype, SE V2, benchmarks, calibration). La auditoría real **sigue** disponible.

## BSSID

Opcional en el modelo (`RealNetworkReference.bssid`). **No** bloquea abrir ni iniciar la auditoría.
