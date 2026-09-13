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

El Search Engine funciona exclusivamente de forma local: puede utilizarse con
challenges sintéticos o con contraseñas conocidas encapsuladas; nunca envía
candidatos a redes externas ni autentica contra access points.

## Known-password audit (componentes)

| Componente | Módulo | Rol |
| --- | --- | --- |
| `CurrentWifiConnectionProvider` | assessment + androidApp | Detección de red conectada |
| `PasswordAuditEligibilityChecker` | assessment | Elegibilidad (PSK personal) |
| `EncapsulatedPasswordVerifier` | lab | Target aislado; verificación local |
| `AutomaticPasswordAuditPlanner` | lab | Plan multi-stage ciego al secreto |
| `PasswordAuditResultComposer` | assessment | `WifiPasswordAuditResult`; config vs resistencia |
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
