# CI emulator API selection (FASE 21)

Project SDKs (`gradle/libs.versions.toml`):

| Setting | Value |
| --- | --- |
| `androidMinSdk` | 26 |
| `androidTargetSdk` | 35 |
| `androidCompileSdk` | 35 |

## Matrix (max 2)

| Role | API | Why |
| --- | --- | --- |
| A — Compatibilidad | **29** | Cercana al mínimo (26) con imágenes `google_apis`/`x86_64` estables y aceleración KVM fiable en `ubuntu-latest`. API 26 es frágil/lenta en runners alojados. |
| B — Principal | **35** | Coincide con `targetSdk` / `compileSdk`. |

Ambos jobs dependen del job JVM y corren en paralelo entre sí (`fail-fast: false`).

Si el tiempo total de CI resultara excesivo, la opción preferida sería:

```text
PR CI      → API 35 (principal)
nightly    → API 29 adicional
```

sin perder la cobertura; no se implementa nightly hasta medirlo.

## Runner

`ubuntu-latest` + KVM (`reactivecircus/android-emulator-runner@v2`) + animaciones desactivadas + GPU `swiftshader_indirect`.
