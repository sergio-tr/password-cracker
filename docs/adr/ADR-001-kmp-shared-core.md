# ADR-001 · Núcleo compartido en Kotlin Multiplatform

- Estado: Aceptada
- Fecha: 2026-09-12
- Relacionado: [0002-kotlin-multiplatform-core](./0002-kotlin-multiplatform-core.md)

## Contexto

El producto es Android hoy, pero debe permitir reutilizar dominio y casos de uso
en una futura app iOS sin reescribir la lógica de negocio.

## Decisión

Colocar dominio y aplicación en módulos KMP (`:shared:core`, `:shared:assessment`,
`:shared:lab`) con la lógica en `commonMain`. Ningún `import android.*` puede
aparecer en `commonMain`. Las capacidades de plataforma se exponen mediante
`PlatformCapabilities` y puertos, implementados por el adaptador de cada plataforma.

Targets habilitados: `jvm` (tests rápidos sin emulador) y `androidTarget`. Los
targets iOS quedan documentados como preparados vía `commonMain`, pero no se
compilan en Windows (ver ADR-001 relacionado y 11-ios-readiness).

## Consecuencias

- El dominio se testea en la JVM sin emulador.
- Portar a iOS consiste en añadir el target y los adaptadores, sin tocar `commonMain`.
- Cualquier API de Android vive en `androidMain`/`androidApp`, nunca en el núcleo.
