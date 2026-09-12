# Test evidence

Evidencia de la suite automática y de los casos manuales obligatorios del RC.
La auditoría automática de FASE 16 está en `main` (PR #17). Rellenar la columna
**Evidencia** del pase manual con fecha, dispositivo y resultado.

## Automático (CI: `ktlintCheck`, `test`, `jvmTest`, `assembleDebug`)

| Área | Dónde | Qué cubre |
| --- | --- | --- |
| Unit / dominio | `:shared:core`, `:shared:assessment`, `:shared:lab` | CombinationCount, Vault, matcher, assessment, lab domain |
| Integración casos de uso | `VaultUseCasesTest`, `NearbyUseCasesTest`, compensación | CRUD, secretos, rollback, save-or-update |
| Base de datos | `SqlDelightSavedNetworkRepositoryTest` | CRUD + BSSID en SQLite memoria |
| Vault security | `VaultModelTest`, `KeystoreSecretVault` (compila) | `toString` redactado; AEAD en adaptador Android |
| Search engine | `DefaultLabSearchEngineTest`, `CandidateSpaceTest` | first/middle/last, límites, cancel, determinismo |
| Cancelación / límites | `SearchLimitsTest`, engine + parallel | duration, attempts, STOP cooperativo |
| ViewModels | `NearbyViewModelTest`, `VaultViewModelTest`, `LabViewModelTest` | orquestación sin lógica de negocio |
| Compose UI / instrumentación | — | No ejecutado en CI (sin emulador) |

## Casos manuales

### Wi-Fi

| Caso | Cómo verificar | Evidencia |
| --- | --- | --- |
| Permiso aceptado | Actualizar → conceder | Escaneo pasa a Results o Throttled |
| Permiso denegado | Denegar | `PermissionRequired` + acción a Ajustes |
| Ubicación desactivada | Apagar ubicación | `LocationServicesDisabled` |
| Sin redes | Entorno vacío | Lista vacía / estado explícito |
| Muchas redes | Zona densa | Scroll de tarjetas, alias primero si conocida |
| Red conocida | Guardar y re-escanear | Alias de usuario primero; last seen |
| Nuevo BSSID | Mismo SSID, otro AP | Se fusiona en `knownBssids` |
| Scan throttled | Actualizar en ráfaga | Estado Throttled con últimas observaciones |

### Vault

| Caso | Cómo verificar | Evidencia |
| --- | --- | --- |
| Create | FAB / desde Nearby | Aparece en listado |
| Read | Abrir detalle | Alias, SSID, seguridad, secreto oculto |
| Edit alias / ubicación | Diálogos del detalle | Lista se actualiza |
| Add / replace / reveal / hide / remove secret | Sección Contraseña | Nunca se revela solo |
| Delete network | Confirmación | Desaparece; secreto no queda referenciado |
| Reinicio | Matar app y abrir | Filas SQLDelight persisten |

### Lab

| Caso | Cómo verificar | Evidencia |
| --- | --- | --- |
| Run normal / found / not found | Dígitos, longitud 2–3 | Resultado + métricas |
| Attempt / time limit | Límites bajos | `LimitReached` |
| Cancel inmediato / a mitad | STOP | Cancelling → Cancelled, métricas conservadas |
| Espacio enorme | Alfanumérico largo | Impractical + "approximately" |
| Rotate / background | Según ciclo de vida | Job se cancela en `onCleared` |

Registrar aquí fecha, dispositivo y resultado cuando se ejecute el pase manual del RC.
