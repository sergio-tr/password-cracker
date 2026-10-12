# Test evidence

Evidencia de la suite autom√°tica y de los casos manuales del RC.
Actualizado en FIX-08 (regresi√≥n auth-aware PSK + cierre ola FIX-06/07).

## FIX-01..08 ‚Äî PRs merged / en curso (CI green)

| Fix | PR | √Årea |
| --- | --- | --- |
| FIX-01 Runtime localization | [#44](https://github.com/sergio-tr/password-cracker/pull/44) | `AppCompatActivity`, `localeConfig`, strings ES/EN |
| FIX-02 Navigation + usability | [#45](https://github.com/sergio-tr/password-cracker/pull/45) | `ArrowBack`, CD `navigate_back`, iconograf√≠a CTAs |
| FIX-03 Local network prototype | [#46](https://github.com/sergio-tr/password-cracker/pull/46) | Prototipo local configurable + motor encapsulado |
| FIX-04 Guided local prototype | [#47](https://github.com/sergio-tr/password-cracker/pull/47) | Flujo guiado novice, SSID/contrase√±a sin Avanzado |
| FIX-06 Auth-aware PSK policy | [#56](https://github.com/sergio-tr/password-cracker/pull/56) | `WifiPskProgressiveAuditPolicy`, planner multi-stage ‚â• 8 |
| FIX-07 Lab/Audit wiring | [#57](https://github.com/sergio-tr/password-cracker/pull/57) | `SecurityFamily.toSharedPasswordSearchProfile()`, PSK ‚â• 8 UI, fitter ‚â† planner |
| FIX-08 Auth-aware regression | ‚Äî | Regresi√≥n JVM + `ProductRegressionComposeTest`; docs honestas |

## AUTOMATED ‚Äî JVM

CI job `jvm` (`.github/workflows/ci.yml`):

| √Årea | Estado |
| --- | --- |
| `ktlintCheck` | PASS |
| `test` / `jvmTest` | PASS |
| `assembleDebug` + `compileDebugAndroidTestKotlin` | PASS |
| Lab cancelaci√≥n (`LabViewModelTest.stop_*`) | PASS |
| Quick Audit cancelaci√≥n (`PasswordAuditViewModelTest.stop*`) | PASS |
| Engine cancel (`DefaultLabSearchEngineTest.cancel_*`) | PASS |
| Known-password domain (`PasswordAuditResultComposerTest`, `AutomaticPasswordAuditPlannerTest`, `TargetIsolationTest`, `WifiPskProgressiveAuditPolicyTest`) | PASS |
| Auth-aware PSK VM (`LabViewModelTest` min length / OPEN¬∑Enterprise, `PasswordAuditViewModelTest` WPA3 stages) | PASS |

## AUTOMATED ‚Äî ANDROID EMULATOR

CI job `android-emulator` ¬∑ runner `ubuntu-latest` + KVM ¬∑
`:androidApp:connectedDebugAndroidTest`

| API | Rol | Estado |
| --- | --- | --- |
| 29 | Compatibilidad (cerca de minSdk 26) | PASS |
| 35 | Principal (= targetSdk) | PASS |

Detalle de APIs: `docs/ci-emulator.md`.

Logcat publicado: **sanitizado** (sin secretos / blobs Base64 largos).

| Campo | Valor |
| --- | --- |
| Suite Compose + instrumentaci√≥n | 16 clases `androidTest` + `ComposeHardcodedStringGuardTest` (JVM static guard) |
| `PasswordAuditComposeTest` | STOP bottomBar, found + recommendations, vault deferred, advanced restore |
| `ProductRegressionComposeTest` | Nav top-level sin back / child con back; Lab guiado; WPA2 STOP‚Üíeditar‚Üírepetir; OPEN/Enterprise sin PSK; FIX-08 auth-aware (WPA2 Crear y probar‚ÜíStart, PSK corta, WPA3 plan perfil-aware); packs ES/EN |
| `MainActivityRuntimeLocaleTest` | ES/EN/SYSTEM + locale persiste tras `Activity.recreate()` |
| `ComposeHardcodedStringGuardTest` | Static guard: no nuevos `Text("‚Ä¶")` / `contentDescription = "‚Ä¶"` user-facing en UI Compose |
| Keystore / SQLDelight / Compose | PASS en emulador |
| Calibraci√≥n persistente | SharedPreferences + Settings (FASE 22) |

## MANUAL ‚Äî PHYSICAL DEVICE

| Caso | Notas |
| --- | --- |
| Escaneo Wi‚ÄëFi con redes reales | No en CI |
| Di√°logos OEM de permiso | No en CI |
| Clipboard / recent-apps visual | Smoke manual |
| Pase RC completo | `release-checklist.md` |

## MANUAL ‚Äî CONNECTED PASSWORD AUDIT WALKTHROUGH

**MANUAL NOT EXECUTED** (pendiente de validaci√≥n en dispositivo f√≠sico):

1. Cambiar contrase√±a en router.
2. Reconectar tel√©fono.
3. Abrir app.
4. Confirmar badge Conectado.
5. Auditar contrase√±a.
6. Introducir/use Vault password.
7. Automatic.
8. Start.
9. Ver m√©tricas.
10. Stop.
11. Repetir y dejar encontrar si viable.
12. Cambiar por contrase√±a m√°s fuerte.
13. Reconectar.
14. Repetir y comparar.

## MANUAL ‚Äî FIX-01..04 UX REGRESSION (P1‚ÄìP4)

**MANUAL NOT EXECUTED** (pendiente de validaci√≥n en dispositivo f√≠sico por el usuario):

1. Settings ‚Üí English ‚Üí UI switches (Lab/Vault/Nearby titles)
2. Settings ‚Üí Espa√±ol ‚Üí back to Spanish
3. Ajustes ‚Üí Permisos ‚Üí ArrowBack returns
4. Lab Guided: SSID + password + Start without Advanced
5. DETENER works on prototype run
6. No AP authentication / local-only banner visible

## F2 ó LAB_NETWORK_VALIDATION local-only probe (2026-10-03)

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
| API 29ñ33 | `Unavailable(RequiresApi34)` |
| No STA concurrency | `Unavailable(RequiresStaConcurrency)` ó protects F1 NETWORK_CHANGED?STOP |
| Families | WPA2 / WPA3 / WPA2+WPA3 Personal only |

### PHYSICAL / INSTRUMENTED

**NOT EXECUTED** ? sin dispositivo fÌsico conectado en la sesiÛn de implementaciÛn F2.

## F3 ? Product integration + physical checklist (2026-10-09)

### AUTOMATED

| Check | Result |
| --- | --- |
| Evidence retention JVM (`BoundedInMemoryLabSessionEvidenceLogTest`) | PASS (local suite) |
| Capability reason mapping JVM | PASS (local suite) |
| `SharedPreferencesLabSessionEvidenceLogInstrumentedTest` | En CI emulator (determinista; sin AP) |
| Vault Compose registro lab (Lab Mode on/off) | En CI emulator |

### PHYSICAL CHECKLIST (no marcar PASS autom·ticamente)

Requisitos mÌnimos del dispositivo: Android API 34+; Wi?Fi; permisos; STA local-only compatible; red WPA2/WPA3 Personal de laboratorio; credencial conocida.

| Caso | Resultado | Notas (dispositivo / fecha) |
| --- | --- | --- |
| Capability: Available o causa concreta (no ´Unavailableª) | ? | |
| Gate: Lab Mode OFF ? denegada | ? | |
| Gate: Lab Mode ON, red no registrada ? denegada | ? | |
| Gate: registrada sin consentimiento ? no inicia | ? | |
| Gate: todo v·lido ? admitir | ? | |
| ValidaciÛn autorizada (1 probe, resultado tipado sanitizado) | ? | No mapear fallo genÈrico a ´contraseÒa incorrectaª |
| CancelaciÛn: DETENER ? Stopping?Cancelled + cleanup | ? | |
| Cambio/pÈrdida de red ? NetworkChanged/NetworkLost + STOP | ? | |
| Lab Mode OFF durante Running ? cierre inmediato | ? | |
| Registry revoke durante Running ? cierre | ? | |
| Sin STA concurrency ? RequiresStaConcurrency (no forzar) | ? | |

Leyenda de estado producto: **Implemented** ∑ **Automated tested** ∑ **Physical validated** ∑ **Unavailable on this device/platform**.

## F4 ? UX consolidation LOCAL vs LAB (2026-10-09)

| Check | Estado |
| --- | --- |
| Experiencia principal = Auditar contraseÒa localmente | **Implemented** |
| Tarjeta comparativa LOCAL vs LAB (operaciones distintas) | **Implemented** |
| LAB bajo opciones avanzadas / Lab Mode (OFF por defecto) | **Implemented** |
| Physical validated | N/A (solo UX); checklist F3 sigue pendiente |

## SINGLE_REAL ? Prueba Wi?Fi real (2026-10-12)

### AUTOMATED

| Check | Result |
| --- | --- |
| FixedLab match + gate (`FixedLabAccessPointTest`, `SingleRealCredentialValidationTest`) | PASS (JVM) |
| One-shot / concurrent / cancel / RequestUnavailable?wrong password | PASS (JVM, fake adapter) |
| `RealWifiProbeViewModelTest` | PASS (JVM, fake adapter) |
| Assemble `labDebug` + `standardDebug` | En CI |

### PHYSICAL WALKTHROUGH

**MANUAL NOT EXECUTED**

1. Compilar flavor lab (`labDebug`) con `LAB_WIFI_SSID` / `LAB_WIFI_BSSID` en `local.properties`.
2. Instalar en dispositivo API compatible (API 34+ recomendado).
3. Conectar el telÈfono al AP fijo configurado.
4. Activar Lab Mode.
5. Abrir Laboratorio ? opciones avanzadas ? **Prueba Wi?Fi real**.
6. Introducir la contrase?a correcta.
7. Confirmar consentimiento (?Confirmo que esta es mi red de pruebas?).
8. Pulsar **PROBAR CONTRASE?A**.
9. Registrar el resultado tipado que devuelve Android.
10. Limpiar sesiÛn (o esperar terminal).
11. Introducir una contrase?a deliberadamente distinta.
12. Ejecutar una nueva sesiÛn manual.
13. Registrar exactamente quÈ callback/result devuelve Android.
14. No interpretar autom·ticamente un fallo genÈrico (`RequestUnavailable`) como password incorrecta.
