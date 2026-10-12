# 16 · Punto de implementación manual — backend Wi‑Fi real

**Estado:** Architecture ready / platform execution backend pending.

Documento alineado con el código tras el refactor de auditoría.

## ÚNICO PUNTO MANUAL

```text
androidApp/src/main/kotlin/com/wifiauditlab/android/wifi/AndroidRealWifiAuditBackend.kt

buscar:

USER_IMPLEMENTATION_POINT_REAL_WIFI_AUDIT
```

Una sola ocurrencia en producción. Por defecto `execute` responde
`Unavailable(NotImplemented)`.

## Qué ya está resuelto antes de llegar aquí

- Red seleccionada por el usuario (`plan.network`)
- `SecurityFamily` / `WifiSecurityProfile` (`plan.securityProfile`)
- Estrategia (`plan.strategy`)
- `RealWifiAuditPlan` (`plan.limits`, `plan.strategyLabel`)
- Métricas / `RealWifiAuditProgressSink`
- Cancelación (`cancel()` / flag `cancelled`)
- Lifecycle UI / ViewModel / navegación

## Objetos disponibles en el marker

```text
plan.network.ssid
plan.network.bssid?          // opcional
plan.network.securityFamily
plan.network.displayName
plan.securityProfile
plan.strategy                // sealed RealWifiAuditStrategy
plan.limits
plan.strategyLabel
progress                     // RealWifiAuditProgressSink
cancelled / cancel()
```

## Qué NO implementar aquí (política actual)

- Automatización de intentos de credenciales / candidatos contra el AP
- Lógica de red dentro de `:shared:lab`
- Conectar Search Engine V2 (`CandidateVerifier`) a autenticación Wi‑Fi real

Cualquier backend experimental dependiente del mecanismo de autenticación debe
quedar **únicamente** detrás de esta frontera.
