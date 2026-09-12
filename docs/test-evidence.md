# Test evidence

Evidencia de la suite automática y de los casos manuales obligatorios del RC.
Actualizado en FASE 20 (`test/android-ui-instrumentation`).

## Automático — JVM / KMP (CI actual)

CI (`.github/workflows/ci.yml`): `ktlintCheck` → `test`/`jvmTest` → `assembleDebug`.

| Área | Dónde | Qué cubre | Estado |
| --- | --- | --- | --- |
| Unit / dominio | `:shared:*` | CombinationCount, Vault, matcher, assessment, lab | **AUTOMATED PASS** (CI) |
| Integración casos de uso | `VaultUseCasesTest`, `NearbyUseCasesTest` | CRUD, secretos, rollback | **AUTOMATED PASS** |
| SQLDelight JVM | `SqlDelightSavedNetworkRepositoryTest` | CRUD + BSSID in-memory | **AUTOMATED PASS** |
| ViewModels | Nearby/Vault/Lab/Onboarding/Permissions/Security | Orquestación UI | **AUTOMATED PASS** |
| Redaction | `VaultModelTest` | `NetworkSecret.toString()` | **AUTOMATED PASS** |

## Automático — Compose UI + instrumentación (FASE 20)

Compilan con `:androidApp:compileDebugAndroidTestKotlin` / empaquetado
`androidTest`. En el entorno de desarrollo de esta fase **no había dispositivo ni
emulador conectado** (`connectedDebugAndroidTest` → *No connected devices*).

| Área | Ficheros | Estado |
| --- | --- | --- |
| Compose Nearby / Onboarding / Permissions / Security / Vault / Lab | `*ComposeTest.kt` | **IMPLEMENTED BUT NOT EXECUTED** |
| SQLDelight Android driver | `AndroidSqlDelightRepositoryTest` | **IMPLEMENTED BUT NOT EXECUTED** |
| KeystoreSecretVault | `KeystoreSecretVaultInstrumentedTest` | **IMPLEMENTED BUT NOT EXECUTED** |
| Lifecycle red+secreto | `NetworkSecretLifecycleInstrumentedTest` | **IMPLEMENTED BUT NOT EXECUTED** |
| Permission manager smoke | `AndroidWifiPermissionManagerInstrumentedTest` | **IMPLEMENTED BUT NOT EXECUTED** |

FASE 21 añadirá ejecución en CI con emulador.

## Manual only

| Caso | Por qué manual |
| --- | --- |
| Escaneo Wi‑Fi con redes reales | No debe hacer flaky CI |
| Diálogos OEM de permiso / “no volver a preguntar” | Variante por fabricante |
| Clipboard / recent-apps visual | Comportamiento de sistema |
| Pase RC completo en dispositivo físico | Ver `release-checklist.md` |

### Plantilla Wi‑Fi (manual)

| Caso | Cómo verificar | Evidencia |
| --- | --- | --- |
| Permiso aceptado | Actualizar → conceder | |
| Permiso denegado | Denegar | |
| Ubicación desactivada | Apagar ubicación | |
| Scan throttled | Actualizar en ráfaga | Mensaje “Escaneo limitado temporalmente…” |
| Red conocida | Guardar y re-escanear | Alias primero |

### Vault / Lab (manual smoke)

CRUD Vault tras reinicio, reveal/hide, Lab found/limit/STOP en dispositivo real.
Registrar fecha, dispositivo y resultado al ejecutar el pase RC.
