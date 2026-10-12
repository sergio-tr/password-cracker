# WiFiSearcher

Aplicación Android **didáctica** de descubrimiento y auditoría Wi‑Fi, con un núcleo
compartible (Kotlin Multiplatform) preparado para una futura implementación iOS.

Nombre visible de producto: **WiFiSearcher**. El identificador técnico histórico
(`com.wifiauditlab.android`, módulos `:shared:…`) se mantiene por compatibilidad.

```text
WiFiSearcher
├── Real environment
│   ├── Nearby
│   ├── Vault
│   ├── Security Assessment
│   └── Local Password Audit
│
└── Lab Mode (optional, OFF by default)
    ├── Synthetic Lab
    ├── Network Prototype
    ├── Benchmarks / calibration
    └── Single Real Credential Validation (one-shot; never Search Engine → AP)
```

## Dos dominios, frontera dura

- **Real Wi-Fi Assessment** (`:shared:assessment`): descubre redes, normaliza su
  seguridad, las explica y gestiona un Vault de redes conocidas y credenciales.
- **Synthetic / local Search Engine** (`:shared:lab`): exploración de espacios de
  búsqueda con `CandidateVerifier` inyectable. **Nunca** autentica candidatos
  contra un AP real.

> Activar **Lab Mode** en Ajustes muestra el tab Laboratorio y CTAs asociados.
> Con Lab Mode OFF esas superficies desaparecen por completo; Nearby, Vault,
> análisis y auditoría local siguen disponibles.

## Módulos

| Módulo | Tipo | Contenido |
| --- | --- | --- |
| `:shared:core` | KMP (jvm + android) | Value objects base: `CombinationCount`, `PlatformCapabilities`. |
| `:shared:assessment` | KMP | Dominio Wi-Fi, clasificador, matcher, assessment, Vault y casos de uso. |
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
`docs/adr/`.
