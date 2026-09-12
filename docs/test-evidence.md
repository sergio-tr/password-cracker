# Test evidence

Evidencia de la suite automática y de los casos manuales del RC.
Actualizado en FASE 21 (`chore/android-emulator-ci`).

## AUTOMATED — JVM

CI job `jvm` (`.github/workflows/ci.yml`):

| Área | Estado |
| --- | --- |
| `ktlintCheck` | Ver run de CI |
| `test` / `jvmTest` | Ver run de CI |
| `assembleDebug` + `compileDebugAndroidTestKotlin` | Ver run de CI |

## AUTOMATED — ANDROID EMULATOR

CI job `android-emulator` · runner `ubuntu-latest` + KVM ·
`:androidApp:connectedDebugAndroidTest`

| API | Rol | Estado |
| --- | --- | --- |
| 29 | Compatibilidad (cerca de minSdk 26) | Ver run de CI |
| 35 | Principal (= targetSdk) | Ver run de CI |

Detalle de APIs: `docs/ci-emulator.md`.

Logcat publicado: **sanitizado** (sin secretos / blobs Base64 largos).

Rellenar tras el primer run verde:

| Campo | Valor |
| --- | --- |
| Run URL | _pendiente primer verde_ |
| Tests ejecutados | _pendiente_ |
| PASS / FAIL / SKIPPED | _pendiente_ |
| Duración job / boot | _pendiente_ |
| Keystore / SQLDelight / Compose | _pendiente_ |

## MANUAL — PHYSICAL DEVICE

| Caso | Notas |
| --- | --- |
| Escaneo Wi‑Fi con redes reales | No en CI |
| Diálogos OEM de permiso | No en CI |
| Clipboard / recent-apps visual | Smoke manual |
| Pase RC completo | `release-checklist.md` |
