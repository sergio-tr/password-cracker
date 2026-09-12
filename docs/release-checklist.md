# Release checklist

Primera versión técnicamente fiable. La auditoría de FASE 16 está en `main`
(PR #17). Publicar sigue requiriendo revisión humana y el pase manual.

## Auditoría (FASE 16)

| Comprobación | Resultado |
| --- | --- |
| Secretos en texto plano persistidos | No. Ciphertext en Keystore + prefs `secret_vault`. |
| Logs / excepciones / `toString` de secretos | `NetworkSecret.toString()` redacta. Sin `Log`/`println` de secretos. |
| `android.*` en `commonMain` | No hay matches. |
| `GlobalScope` | No hay usos. |
| Jobs sin cancelar | `LabViewModel.onCleared` cancela; Nearby/Vault usan `viewModelScope`. |
| Bloqueo del hilo principal | Lab corre en `searchDispatcher` (Default). Calibración en background. |
| Fugas de repositorio | SQLDelight driver en Koin singleton; observeAll es Flow. |
| Migraciones | Esquema Vault v1 único; no hay migraciones pendientes de v0. |
| Excepciones no controladas | Engine emite `Failed`; Vault compensa create/update. |
| Estados UI imposibles | `SearchState` / `SearchLifecycle` explícitos; scan/vault sealed. |
| Frontera Real/Lab | `:shared:lab` → sólo `:shared:core`. |

## Gaps conocidos (no bloquean el RC si se aceptan)

- Tests Compose / instrumentación no corren en CI (sin emulador).
- Calibración se guarda en memoria de proceso (`InMemoryCalibrationStore`).
- Biometría antes de revelar: arquitectura lista, no implementada.
- GeoLocation real: no expuesta en UI (sólo `LocationLabel`).
- iOS: solo previsto.

## Antes de publicar

- [x] CI `build` verde en la PR de hardening (#17)
- [ ] Recorrer casos manuales de `docs/test-evidence.md` y anotar dispositivo/fecha
- [ ] Confirmar que no hay secretos en fixtures versionados
- [ ] Revisar `docs/00-progress.md`
- [ ] Decidir versionName / versionCode
