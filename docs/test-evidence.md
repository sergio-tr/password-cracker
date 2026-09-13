# Test evidence

Evidencia de la suite automática y de los casos manuales del RC.
Actualizado en PR7 (Quick Audit Compose + reconciliación docs).

## AUTOMATED — JVM

CI job `jvm` (`.github/workflows/ci.yml`):

| Área | Estado |
| --- | --- |
| `ktlintCheck` | PASS |
| `test` / `jvmTest` | PASS |
| `assembleDebug` + `compileDebugAndroidTestKotlin` | PASS |
| Lab cancelación (`LabViewModelTest.stop_*`) | PASS |
| Quick Audit cancelación (`PasswordAuditViewModelTest.stop*`) | PASS |
| Engine cancel (`DefaultLabSearchEngineTest.cancel_*`) | PASS |
| Known-password domain (`PasswordAuditResultComposerTest`, `AutomaticPasswordAuditPlannerTest`, `TargetIsolationTest`) | PASS |

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
| Suite Compose + instrumentación | 13 clases `androidTest` |
| `PasswordAuditComposeTest` | STOP bottomBar, found + recommendations, vault deferred, advanced restore |
| Keystore / SQLDelight / Compose | PASS en emulador |
| Calibración persistente | SharedPreferences + Settings (FASE 22) |

## MANUAL — PHYSICAL DEVICE

| Caso | Notas |
| --- | --- |
| Escaneo Wi‑Fi con redes reales | No en CI |
| Diálogos OEM de permiso | No en CI |
| Clipboard / recent-apps visual | Smoke manual |
| Pase RC completo | `release-checklist.md` |

## MANUAL — CONNECTED PASSWORD AUDIT WALKTHROUGH

**MANUAL NOT EXECUTED** (pendiente de validación en dispositivo físico):

1. Cambiar contraseña en router.
2. Reconectar teléfono.
3. Abrir app.
4. Confirmar badge Conectado.
5. Auditar contraseña.
6. Introducir/use Vault password.
7. Automatic.
8. Start.
9. Ver métricas.
10. Stop.
11. Repetir y dejar encontrar si viable.
12. Cambiar por contraseña más fuerte.
13. Reconectar.
14. Repetir y comparar.
