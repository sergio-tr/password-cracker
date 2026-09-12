# 11 · iOS readiness

El núcleo compartible se diseña desde el principio para reutilizarse en iOS
mediante Kotlin Multiplatform.

## Qué está listo para compartir

- `:shared:core`, `:shared:assessment`, `:shared:lab` viven en `commonMain` sin
  ninguna dependencia `android.*`.
- Toda diferencia de plataforma se expresa como capacidad (`PlatformCapabilities`),
  no como condicional disperso.
- Los puertos (`WifiScanner`, `SecretVault`, `SavedNetworkRepository`,
  `LocationProvider`) son interfaces que iOS implementará con adaptadores propios.
- `NetworkSecret`/`SecretVault` están detrás de una abstracción reemplazable por
  **Keychain** en iOS.
- `CombinationCount` usa un big-integer multiplataforma, no `java.math.BigInteger`.

## Qué falta para un target iOS real

- Añadir los targets `iosX64`/`iosArm64`/`iosSimulatorArm64` a los módulos
  compartidos (requiere macOS/Xcode; el proyecto se desarrolla en Windows, por lo
  que ahora sólo se habilitan `jvm` + `androidTarget`).
- Implementar adaptadores iOS: descubrimiento/inspección Wi-Fi (limitado por
  iOS → reflejado vía `PlatformCapabilities`), Keychain, persistencia y UI (SwiftUI
  o Compose Multiplatform).

## Capacidades por plataforma (previsión)

| Capacidad | Android | iOS (previsto) |
| --- | --- | --- |
| `nearbyWifiDiscovery` | Sí | Limitado / No |
| `currentWifiInspection` | Sí | Parcial (con entitlements) |
| `secureSecretStorage` | Sí (Keystore) | Sí (Keychain) |
| `geolocation` | Sí | Sí |

El laboratorio sintético es 100% portable: no depende de ninguna capacidad de red.
