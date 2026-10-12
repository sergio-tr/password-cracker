# 04 · Android Wi-Fi

## Puerto

```kotlin
interface WifiScanner {
    fun observeState(): Flow<WifiScanState>
    suspend fun refresh(): WifiScanRequestResult
}
```

Estados (`WifiScanState`): `Idle`, `Loading`, `Results`, `Throttled`,
`PermissionRequired`, `LocationServicesDisabled`, `Unavailable`, `Error`.

`refresh()` devuelve `WifiScanRequestResult` (`STARTED`, `THROTTLED`,
`PERMISSION_REQUIRED`, `LOCATION_SERVICES_DISABLED`, `UNAVAILABLE`). **Nunca** se
asume que cada pulsación de *Actualizar* genera un escaneo físico nuevo: Android
limita `startScan()` y eso se refleja en `THROTTLED`.

## Separación de responsabilidades

| Clase (androidApp) | Rol |
| --- | --- |
| `AndroidWifiScanner` | Adaptador del puerto; escucha `SCAN_RESULTS_AVAILABLE_ACTION`. |
| `AndroidWifiMapper` | Traduce `ScanResult` → `WifiObservation`. |
| `AndroidWifiPermissionManager` | Resuelve permisos según versión (NEARBY_WIFI_DEVICES / ubicación). |
| `WifiSecurityClassifier` (`:shared:assessment`) | Normaliza `capabilities` → `WifiSecurityProfile`. |

El **classifier** vive en el núcleo de negocio (`:shared:assessment`), sin tipos
Android, por lo que es testeable sin emulador y reutilizable en iOS. El mapper
sólo adapta datos de la plataforma.

## Red conectada y prueba real de contraseña

| Clase (androidApp / assessment) | Rol |
| --- | --- |
| `AndroidCurrentWifiConnectionProvider` | Adaptador de `CurrentWifiConnectionProvider`; lee SSID/BSSID de la conexión activa. |
| `DefaultRealNetworkPasswordEligibilityChecker` | Mismo SSID + familia + permisos → `Executable`; BSSID confirmado = evidencia opcional. |
| `RealNetworkPasswordExecutionPort` | Puerto application-neutral (sin tipos Android). |
| `AndroidRealNetworkPasswordExecutionAdapter` | `WifiNetworkSpecifier` + `requestNetwork`; BSSID opcional en el Specifier. |
| `NetworkConnectionMatch` | Exact / Probable / … (sin cambiar); el checker real mapea Exact **y** Probable a Executable. |

En Nearby/Vault, si el SSID seleccionado coincide con la Wi‑Fi actual,
CTA «Probar contraseña en esta red» → `RealNetworkPasswordTestScreen`.
Sin hardcode ni `local.properties`: datos del scan + `CurrentWifiConnectionProvider`.

El Search Engine local **no** se lanza desde este flujo (solo Lab Mode).

### Lab dual-mode histórico (ADR-004 / F0–F3) — superficies Lab

| Pieza | Estado |
| --- | --- |
| Search Engine local (`CandidateVerifier`) | Vive bajo Lab; planners/workers/STOP intactos |
| `LAB_NETWORK_VALIDATION` sesión | Orquestador + monitors + budget — Lab only |
| Probe local-only F2 | `WifiNetworkSpecifier` + `requestNetwork` — Lab capability path |
| Prueba Wi‑Fi real (lab flavor / FixedLab) | One-shot `validateOnce` con AP fijo `LAB_WIFI_*` — solo `labDebug`; **no** es el flujo Nearby real |
| Registro lab (F3) | Nearby/Vault con Lab Mode ON |
| Evidence / backup (F3) | Sin cambio |

## Permisos

- Android 13+: `NEARBY_WIFI_DEVICES` (con `neverForLocation`).
- Anteriores: `ACCESS_FINE_LOCATION` y servicios de ubicación activos.
