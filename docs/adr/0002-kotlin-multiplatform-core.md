# ADR 0002 · Núcleo compartible con Kotlin Multiplatform

- Estado: Aceptada
- Fecha: 2026-09-12

## Contexto

Se requiere un núcleo reutilizable en iOS para domain, application, ports,
security assessment, network matching, lab engine, métricas y configuración, sin
introducir `android.*` en `commonMain`.

## Decisión

Usar Kotlin Multiplatform. Los módulos `:shared:core`, `:shared:assessment` y
`:shared:lab` colocan toda su lógica en `commonMain`. Se habilitan los targets
`jvm` (para tests rápidos sin Android) y `androidTarget` (para consumo desde la
app). Los targets iOS se añadirán cuando se disponga de macOS/Xcode (ver
`docs/11-ios-readiness.md`); el proyecto se desarrolla en Windows.

## Consecuencias

- `./gradlew test` ejecuta la lógica de negocio en la JVM sin SDK de Android.
- No se puede compilar iOS en Windows, pero el código ya es *iOS-ready*.
- Las diferencias de plataforma se modelan con `PlatformCapabilities`.
