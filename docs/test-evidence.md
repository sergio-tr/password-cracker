# Test evidence

Evidencia de la suite automática y de los casos manuales del RC.
Actualizado en UX-01 (STOP fijo en Lab) y FASE 22/23 (calibración durable + CI).

## AUTOMATED — JVM

CI job `jvm` (`.github/workflows/ci.yml`):

| Área | Estado |
| --- | --- |
| `ktlintCheck` | PASS (run verde abajo) |
| `test` / `jvmTest` | PASS |
| `assembleDebug` + `compileDebugAndroidTestKotlin` | PASS |
| Lab cancelación (`LabViewModelTest.stop_*`) | PASS local UX-01 |

## AUTOMATED — ANDROID EMULATOR

CI job `android-emulator` · runner `ubuntu-latest` + KVM ·
`:androidApp:connectedDebugAndroidTest`

| API | Rol | Estado |
| --- | --- | --- |
| 29 | Compatibilidad (cerca de minSdk 26) | PASS |
| 35 | Principal (= targetSdk) | PASS |

Detalle de APIs: `docs/ci-emulator.md`.

Logcat publicado: **sanitizado** (sin secretos / blobs Base64 largos).

| Campo | Valor |
| --- | --- |
| Run URL (verde) | https://github.com/sergio-tr/password-cracker/actions/runs/34728294856 |
| Tests ejecutados | 48 (suite FASE 20 + calibration prefs test) |
| PASS / FAIL / SKIPPED | PASS (0 fail / 0 skipped en run verde) |
| Duración job | ~5–7 min por API tras JVM |
| Keystore / SQLDelight / Compose | PASS en emulador |
| Calibración persistente | SharedPreferences + Settings (FASE 22) |

## MANUAL — PHYSICAL DEVICE

| Caso | Notas |
| --- | --- |
| Escaneo Wi‑Fi con redes reales | No en CI |
| Diálogos OEM de permiso | No en CI |
| Clipboard / recent-apps visual | Smoke manual |
| Pase RC completo | `release-checklist.md` |
