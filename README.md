# WiFiSearcher

Aplicación Android **didáctica** de descubrimiento y auditoría Wi‑Fi, con un núcleo
compartible (Kotlin Multiplatform) preparado para una futura implementación iOS.

Nombre visible de producto: **WiFiSearcher**. El identificador técnico histórico
(`com.wifiauditlab.android`, módulos `:shared:…`) se mantiene por compatibilidad.

```text
WiFiSearcher
├── Real environment (Lab Mode OFF = default)
│   ├── Nearby
│   ├── Vault
│   ├── Security Assessment
│   └── RealWifiAudit (strategy / planner / SE V2; external verifier pending)
│
└── Lab Mode (optional, OFF by default)
    ├── Synthetic Lab / Local Prototype / BlackBox RandomHidden
    ├── Search Engine V2 / planners / benchmarks / calibration
    └── RealWifiProbe (flavor lab; one-shot FixedLab credential validation)
```

## Dos dominios, frontera dura

- **Real Wi-Fi Assessment** (`:shared:assessment`): descubre redes, normaliza su
  seguridad, las explica, gestiona el Vault y orquesta `RealWifiAudit`.
- **Synthetic / local Search Engine** (`:shared:lab`): exploración de espacios de
  búsqueda con `CandidateVerifier` inyectable. **Nunca** autentica candidatos
  contra un AP real desde el motor Lab.

> Activar **Lab Mode** en Ajustes muestra el tab Laboratorio y CTAs asociados.
> Con Lab Mode OFF esas superficies desaparecen; Nearby, Vault, análisis y
> RealWifiAudit siguen disponibles. El backend externo de verificación de
> candidatos de producción **no está implementado** (capability Unavailable).

## Módulos

| Módulo | Tipo | Contenido |
| --- | --- | --- |
| `:shared:core` | KMP (jvm + android) | Value objects base: `CombinationCount`, `PlatformCapabilities`. |
| `:shared:assessment` | KMP | Dominio Wi-Fi, clasificador, matcher, assessment, Vault, RealWifiAudit. |
| `:shared:lab` | KMP | Motor de búsqueda local, planificador, `CandidateVerifier`, límites. |
| `:shared:persistence` | KMP | SQLDelight Vault. |
| `:androidApp` | App Android | Adaptadores, DI (Koin) y UI Compose (Material 3). |

## Requisitos de compilación

- JDK 17+ (probado con JDK 21).
- Android SDK con compile/target SDK del proyecto.
- El Gradle wrapper descarga la distribución configurada automáticamente.

## Comandos habituales

```bash
./gradlew test                     # tests unitarios JVM de módulos compartidos
./gradlew :shared:lab:jvmTest      # tests del Search Engine / lab
./gradlew :androidApp:assembleDebug
```

## Documentación

Ver `docs/` (alcance, arquitectura, dominio, Wi-Fi, seguridad, Vault,
laboratorio, motor de búsqueda, UI/UX, testing e iOS readiness) y los ADR en
`docs/adr/`. Guías RealWifiAudit: `docs/15`–`docs/17`.
