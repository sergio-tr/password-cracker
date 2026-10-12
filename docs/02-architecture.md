# 02 · Arquitectura

## Estilo

Ports & Adapters / Clean Architecture. Dependencias permitidas:

```
UI  ->  Application  ->  Domain
Android adapters  ->  Ports
Database / Keystore  ->  Ports
```

Prohibido: `Domain -> Android`, `Domain -> Compose`, `Domain -> SQLite`, y lógica
de negocio relevante dentro de ViewModels.

## Mapa de módulos

```
:androidApp  (Compose UI, ViewModels, adaptadores Android, DI Koin)
    |            |                 |              |
    v            v                 v              v
:shared:persistence          :shared:lab    (también assessment/core)
    |                              |
    v                              |
:shared:assessment                 |
    |                              |
    +--------------+---------------+
                   v
              :shared:core
```

- `:shared:core`: kernel de value objects (`CombinationCount`, `PlatformCapabilities`).
- `:shared:assessment`: dominio **Real Wi-Fi Assessment** (Wi-Fi, seguridad, matcher,
  Vault, auditoría de contraseña) + casos de uso + puertos.
- `:shared:persistence`: adaptador SQLDelight de `SavedNetworkRepository`
  (depende de `:shared:assessment`; el dominio no ve SQLDelight).
- `:shared:lab`: dominio **Search Engine local** (motor, planificador, límites,
  métricas, viabilidad, `EncapsulatedPasswordVerifier`,
  `AutomaticPasswordAuditPlanner`). Depende **sólo** de `:shared:core`.
- `:androidApp`: adaptadores e infraestructura Android + UI.

## Garantía de la frontera Real/Lab

`:shared:lab/build.gradle.kts` no declara ninguna dependencia hacia
`:shared:assessment` ni hacia Android. Por tanto es **imposible**, en tiempo de
compilación, que `LabSearchEngine` referencie `WifiScanner` o cualquier API de
red. La frontera no se apoya en disciplina de código sino en el grafo de módulos.

### Dos entornos (contrato de producto)

```text
REAL ENVIRONMENT (Lab Mode OFF = producto por defecto)
  Nearby / Vault
    → RealNetworkPasswordEligibility (Exact BSSID)
    → RealNetworkPasswordTestScreen
    → RealNetworkPasswordExecutionPort
    → AndroidRealNetworkPasswordExecutionAdapter (TODO / NotImplemented)

LAB ENVIRONMENT (Lab Mode ON = Real + Lab)
  LabScreen
    → Synthetic Lab / Local Network Prototype
    → Search Engine V2 → CandidateVerifier (local)
    → planners / workers / STOP / metrics / calibration / benchmarks
```

El Search Engine **no** se conecta a `RealNetworkPasswordExecutionPort`.
Vive sólo bajo Lab y usa `CandidateVerifier` local.

### Punto de extensión Lab: `CandidateVerifier`

```text
                          CandidateVerifier
                               ▲
                               │
 Search Engine V2 ─────────────┤
                               │
               ┌───────────────┼──────────────────┐
               │               │                  │
        Synthetic       Known-secret       unsupported
     LocalSynthetic   LocalKnownSecret   NetworkBackedUnsupported
  SyntheticExact…   Encapsulated…     UnsupportedNetwork…
```

- El motor, el scheduler y la generación de candidatos dependen **sólo** de
  `CandidateVerifier`.
- Un backend distinto se introduce detrás de esa interfaz; no acoplar
  `WifiManager` / `ConnectivityManager` / `NetworkValidationAdapter` ni
  `:shared:assessment` dentro de `:shared:lab`.
- `UnsupportedNetworkCandidateVerifier` es un placeholder arquitectónico:
  falla cerrado y no contiene implementación de red.

**Lab Mode** (`LabModePreferences` / `LabModeSurfaces`): preferencia global
(default false) que **oculta** superficies Lab en UI cuando está OFF; **no**
elimina ni desconecta el código Lab (DI, motor, Guided/Advanced, prototipos,
calibración, benchmarks). Con Lab Mode ON reaparecen las mismas superficies.

## Real password probe + Lab search (componentes)

| Componente | Módulo | Rol |
| --- | --- | --- |
| `CurrentWifiConnectionProvider` | assessment + androidApp | Detección de red conectada |
| `RealNetworkPasswordEligibilityChecker` | assessment | Exact → Executable; Probable → informativo |
| `RealNetworkPasswordExecutionPort` | assessment | Puerto de ejecución real (sin Android types) |
| `AndroidRealNetworkPasswordExecutionAdapter` | androidApp | Stub tipado `NotImplemented` |
| `EncapsulatedPasswordVerifier` | lab | Target aislado; verificación local (Lab) |
| `AutomaticPasswordAuditPlanner` | lab | Plan multi-stage ciego al secreto (Lab) |
| `PasswordAuditResultComposer` | assessment | Resultados locales Lab |
| `SecretStrengthAnalyzer` | assessment | Análisis estructural; **no** alimenta al planner |

## Capacidades de plataforma

Las limitaciones específicas de cada plataforma se resuelven con
`PlatformCapabilities` (banderas de capacidad), no con condicionales dispersos:

```kotlin
interface PlatformCapabilities {
    val nearbyWifiDiscovery: Boolean
    val currentWifiInspection: Boolean
    val secureSecretStorage: Boolean
    val geolocation: Boolean
}
```

## Testabilidad

El dominio y los casos de uso se prueban en el target `jvm` de cada módulo KMP,
sin emulador ni Android SDK (`./gradlew test`).
