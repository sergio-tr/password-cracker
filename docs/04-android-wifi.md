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

## Red conectada y elegibilidad de auditoría

| Clase (androidApp / assessment) | Rol |
| --- | --- |
| `AndroidCurrentWifiConnectionProvider` | Adaptador de `CurrentWifiConnectionProvider`; lee SSID/BSSID de la conexión activa. |
| `DefaultPasswordAuditEligibilityChecker` | Cruza observación + conexión actual; solo WPA/WPA2/WPA3 Personal elegibles. |
| `NetworkConnectionMatch` | Exact / Probable / Unknown / Ambiguous entre observación y conexión. |

En Nearby, la red conectada muestra badge «Conectado» y, si es elegible, CTA
«Auditar contraseña». La auditoría exige conexión activa a esa red; si se pierde,
el ViewModel bloquea el inicio.

## Permisos

- Android 13+: `NEARBY_WIFI_DEVICES` (con `neverForLocation`).
- Anteriores: `ACCESS_FINE_LOCATION` y servicios de ubicación activos.
