# Test evidence

Evidencia de la suite automática y de los casos manuales del RC.
Actualizado en FIX-08 (regresión auth-aware PSK + cierre ola FIX-06/07).

## FIX-01..08 — PRs merged / en curso (CI green)

| Fix | PR | Área |
| --- | --- | --- |
| FIX-01 Runtime localization | [#44](https://github.com/sergio-tr/password-cracker/pull/44) | `AppCompatActivity`, `localeConfig`, strings ES/EN |
| FIX-02 Navigation + usability | [#45](https://github.com/sergio-tr/password-cracker/pull/45) | `ArrowBack`, CD `navigate_back`, iconografía CTAs |
| FIX-03 Local network prototype | [#46](https://github.com/sergio-tr/password-cracker/pull/46) | Prototipo local configurable + motor encapsulado |
| FIX-04 Guided local prototype | [#47](https://github.com/sergio-tr/password-cracker/pull/47) | Flujo guiado novice, SSID/contraseña sin Avanzado |
| FIX-06 Auth-aware PSK policy | [#56](https://github.com/sergio-tr/password-cracker/pull/56) | `WifiPskProgressiveAuditPolicy`, planner multi-stage ≥ 8 |
| FIX-07 Lab/Audit wiring | [#57](https://github.com/sergio-tr/password-cracker/pull/57) | `SecurityFamily.toSharedPasswordSearchProfile()`, PSK ≥ 8 UI, fitter ≠ planner |
| FIX-08 Auth-aware regression | — | Regresión JVM + `ProductRegressionComposeTest`; docs honestas |

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
| Known-password domain (`PasswordAuditResultComposerTest`, `AutomaticPasswordAuditPlannerTest`, `TargetIsolationTest`, `WifiPskProgressiveAuditPolicyTest`) | PASS |
| Auth-aware PSK VM (`LabViewModelTest` min length / OPEN·Enterprise, `PasswordAuditViewModelTest` WPA3 stages) | PASS |

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
| Suite Compose + instrumentación | 16 clases `androidTest` + `ComposeHardcodedStringGuardTest` (JVM static guard) |
| `PasswordAuditComposeTest` | STOP bottomBar, found + recommendations, vault deferred, advanced restore |
| `ProductRegressionComposeTest` | Nav top-level sin back / child con back; Lab guiado; WPA2 STOP→editar→repetir; OPEN/Enterprise sin PSK; FIX-08 auth-aware (WPA2 Crear y probar→Start, PSK corta, WPA3 plan perfil-aware); packs ES/EN |
| `MainActivityRuntimeLocaleTest` | ES/EN/SYSTEM + locale persiste tras `Activity.recreate()` |
| `ComposeHardcodedStringGuardTest` | Static guard: no nuevos `Text("…")` / `contentDescription = "…"` user-facing en UI Compose |
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

## MANUAL — FIX-01..04 UX REGRESSION (P1–P4)

**MANUAL NOT EXECUTED** (pendiente de validación en dispositivo físico por el usuario):

1. Settings → English → UI switches (Lab/Vault/Nearby titles)
2. Settings → Español → back to Spanish
3. Ajustes → Permisos → ArrowBack returns
4. Lab Guided: SSID + password + Start without Advanced
5. DETENER works on prototype run
6. No AP authentication / local-only banner visible

## F2 � LAB_NETWORK_VALIDATION local-only probe (2026-10-03)

### AUTOMATED

| Check | Result |
| --- | --- |
| `:shared:assessment:jvmTest` (incl. failure-reason mapper + orchestrator) | PASS |
| `:shared:lab:jvmTest` | PASS |
| `:androidApp:testDebugUnitTest` | PASS |
| `ktlintCheck` | PASS |
| `:androidApp:assembleDebug` | PASS |

### Capability demonstrated in code (not claimed Available on all devices)

| Condition | Outcome |
| --- | --- |
| API 34+ AND STA concurrency local-only AND Wi-Fi on AND scan/nearby permission | `ApAuthCapability.Available` + `AuthenticationResultCapable` |
| API 29�33 | `Unavailable(RequiresApi34)` |
| No STA concurrency | `Unavailable(RequiresStaConcurrency)` � protects F1 NETWORK_CHANGED?STOP |
| Families | WPA2 / WPA3 / WPA2+WPA3 Personal only |

### PHYSICAL / INSTRUMENTED

**NOT EXECUTED** ? sin dispositivo f�sico conectado en la sesi�n de implementaci�n F2.

## F3 ? Product integration + physical checklist (2026-10-09)

### AUTOMATED

| Check | Result |
| --- | --- |
| Evidence retention JVM (`BoundedInMemoryLabSessionEvidenceLogTest`) | PASS (local suite) |
| Capability reason mapping JVM | PASS (local suite) |
| `SharedPreferencesLabSessionEvidenceLogInstrumentedTest` | En CI emulator (determinista; sin AP) |
| Vault Compose registro lab (Lab Mode on/off) | En CI emulator |

### PHYSICAL CHECKLIST (no marcar PASS autom�ticamente)

Requisitos m�nimos del dispositivo: Android API 34+; Wi?Fi; permisos; STA local-only compatible; red WPA2/WPA3 Personal de laboratorio; credencial conocida.

| Caso | Resultado | Notas (dispositivo / fecha) |
| --- | --- | --- |
| Capability: Available o causa concreta (no �Unavailable�) | ? | |
| Gate: Lab Mode OFF ? denegada | ? | |
| Gate: Lab Mode ON, red no registrada ? denegada | ? | |
| Gate: registrada sin consentimiento ? no inicia | ? | |
| Gate: todo v�lido ? admitir | ? | |
| Validaci�n autorizada (1 probe, resultado tipado sanitizado) | ? | No mapear fallo gen�rico a �contrase�a incorrecta� |
| Cancelaci�n: DETENER ? Stopping?Cancelled + cleanup | ? | |
| Cambio/p�rdida de red ? NetworkChanged/NetworkLost + STOP | ? | |
| Lab Mode OFF durante Running ? cierre inmediato | ? | |
| Registry revoke durante Running ? cierre | ? | |
| Sin STA concurrency ? RequiresStaConcurrency (no forzar) | ? | |

Leyenda de estado producto: **Implemented** � **Automated tested** � **Physical validated** � **Unavailable on this device/platform**.
