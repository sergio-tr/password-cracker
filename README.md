# Wi-Fi Audit Lab

Aplicación Android **didáctica** de auditoría Wi-Fi, con un núcleo compartible
(Kotlin Multiplatform) preparado para una futura implementación iOS.

La app tiene **dos dominios físicamente separados**:

- **Real Wi-Fi Assessment** (`:shared:assessment`): descubre redes, normaliza su
  seguridad, las explica y gestiona un Vault de redes conocidas y credenciales.
- **Synthetic Security Lab** (`:shared:lab`): laboratorio sintético para estudiar
  algoritmos de exploración de espacios de búsqueda, con métricas, límites y
  cancelación. **Nunca** se conecta a una red real.

> El laboratorio genera un secreto local sintético y sólo lo busca contra sí
> mismo. `:shared:lab` no depende de `:shared:assessment` ni de ninguna API de
> red: la frontera está garantizada por el grafo de módulos de Gradle.

## Módulos

| Módulo | Tipo | Contenido |
| --- | --- | --- |
| `:shared:core` | KMP (jvm + android) | Value objects base: `CombinationCount`, `PlatformCapabilities`. |
| `:shared:assessment` | KMP | Dominio Wi-Fi, clasificador de seguridad, matcher, assessment, Vault y casos de uso. |
| `:shared:lab` | KMP | Motor de búsqueda sintético, planificador, límites, métricas y viabilidad. |
| `:androidApp` | App Android | Adaptadores (Wi-Fi, permisos, Keystore), DI (Koin) y UI Compose (Material 3). |

## Requisitos de compilación

- JDK 17+ (probado con JDK 21).
- Android SDK con `platforms;android-35` y `build-tools;35.0.0`.
- El Gradle wrapper descarga Gradle 8.11.1 automáticamente.

## Comandos habituales

```bash
./gradlew test                     # tests unitarios JVM de todos los módulos compartidos
./gradlew :shared:lab:jvmTest      # tests del laboratorio
./gradlew :androidApp:assembleDebug
```

## Documentación

Ver `docs/` (alcance, arquitectura, dominio, Wi-Fi, seguridad, Vault,
laboratorio, motor de búsqueda, UI/UX, testing e iOS readiness) y los ADR en
`docs/adr/`.
