# ADR 0003 · Stack de tooling

- Estado: Aceptada
- Fecha: 2026-09-12

## Contexto

El enunciado fija la arquitectura pero deja abierta la elección concreta de
librerías. Son decisiones internas que se resuelven con buenas prácticas y se
registran aquí.

## Decisión

| Área | Elección | Motivo |
| --- | --- | --- |
| Build | Gradle 8.11.1 + version catalog | Wrapper reproducible; catálogo central. |
| Lenguaje | Kotlin 2.1.0 | KMP + compose compiler plugin. |
| Android | AGP 8.7.3, compileSdk 35, minSdk 26 | minSdk 26 habilita AES-GCM en Keystore. |
| UI | Jetpack Compose + Material 3 | Requisito de UX. |
| DI | Koin 4.0 | Ligero, multiplatform-friendly. |
| Concurrencia | kotlinx-coroutines | Flujos y cancelación cooperativa. |
| Fechas/tiempo | kotlinx-datetime + kotlin.time | Multiplataforma. |
| Big integer | `com.ionspin.kotlin:bignum` | Ver ADR 0004. |

## Consecuencias

- JDK 17 como target de bytecode (alineado Kotlin/Java en todos los módulos).
- Las versiones viven en `gradle/libs.versions.toml`.
