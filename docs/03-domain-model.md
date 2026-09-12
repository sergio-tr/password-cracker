# 03 · Modelo de dominio

Se separa siempre una **observación** (temporal) de una **red guardada**
(persistente).

## Wi-Fi (`com.wifiauditlab.assessment.domain.wifi`)

| Tipo | Rol |
| --- | --- |
| `WifiObservation` | Avistamiento temporal de una red durante un escaneo. |
| `WifiSecurityProfile` | Configuración de seguridad **normalizada**. |
| `WifiSignal` | RSSI + calidad derivada (`SignalQuality`). |
| `WifiChannel` / `WifiBand` / `WifiStandard` | Canal, banda y generación Wi-Fi. |
| `SecurityFamily` | Familia normalizada (OPEN, WEP, WPA2/3 personal/enterprise, OWE, DPP…). |
| `NetworkIdentity` | Identidad para matching: SSID normalizado + familia de seguridad. |
| `Ssid` / `Bssid` | Value classes normalizadas (no strings sueltos). |

## Vault (`...domain.vault`)

| Tipo | Rol |
| --- | --- |
| `SavedWifiNetwork` | Red conocida persistente (puede tener varios BSSID). |
| `NewSavedWifiNetwork` | Payload de creación. |
| `SavedNetworkId` / `SecretId` | Identificadores tipados. |
| `NetworkSecret` | Credencial; `toString()` redacta el valor. |
| `LocationLabel` | Etiqueta de ubicación del usuario (Casa, Trabajo…). |
| `GeoLocation` | Coordenadas reales, opcionales. |

## Seguridad (`...domain.security`)

`SecurityAssessment`, `SecurityFinding`, `SecurityRating`, `Severity` y las
estrategias del *Strategy Registry*.

## Laboratorio (`com.wifiauditlab.lab.domain`)

`LabChallengeId`/`ChallengeId`, `LabChallenge`, `LabSecretPolicy`, `LabSearchPlan`,
`SearchBucket`, `SearchLimits`, `SearchMetrics`, `LabSearchProgress`,
`LabSearchEvent`, `LabSearchResult`, `SearchState`/`SearchLifecycle`,
`SearchSessionId`, `SearchStrategyId`, `Alphabet`, `LengthPolicy`.

## Regla

No usar strings primitivos cuando existe un concepto de dominio claro; usar value
classes (`Ssid`, `Bssid`, `SavedNetworkId`, `SecretId`, `SearchSessionId`, …).
