# Release checklist

Primera versión técnicamente fiable. La visión reconciliada del estado real está en
`docs/12-current-state-audit.md` (snapshot 2026-09-13, post PR6/PR7).
Publicar sigue requiriendo revisión humana y el pase manual.

## Auditoría (FASE 16 + PR7)

| Comprobación | Resultado |
| --- | --- |
| Secretos en texto plano persistidos | No. Ciphertext en Keystore + prefs `secret_vault`. |
| Logs / excepciones / `toString` de secretos | `NetworkSecret.toString()` redacta. Sin `Log`/`println` de secretos. |
| `android.*` en `commonMain` | No hay matches. |
| `GlobalScope` | No hay usos. |
| Jobs sin cancelar | `LabViewModel` y `PasswordAuditViewModel.onCleared` cancelan; Nearby/Vault usan `viewModelScope`. |
| Bloqueo del hilo principal | Lab y audit corren en `searchDispatcher` (Default). |
| Frontera Real/Lab | `:shared:lab` → sólo `:shared:core`. Search Engine nunca autentica contra AP. |
| Known-password audit | Verificación local vía `EncapsulatedPasswordVerifier`; informe separa config vs resistencia. |
| Localización | FIX-01 merged: strings ES/EN en Lab/Nearby/Vault/Onboarding/Permissions/Security; runtime locale activo. **Partial** — pase manual FIX-01..04 pendiente (`docs/test-evidence.md`). |

## Gaps conocidos (no bloquean el RC si se aceptan)

- Compose UI + instrumentación corren en CI con emulador (API 29 + 35). Escaneo Wi‑Fi físico, walkthrough connected audit y diálogos OEM siguen siendo **manual**.
- Localización FIX-01 merged pero sin pase manual del usuario (checklist FIX-01..04 en `test-evidence.md`).
- Lab prototipo local (FIX-03/04) sin validación manual en dispositivo.
- Biometría antes de revelar: arquitectura lista, no implementada.
- GeoLocation real: no expuesta en UI (sólo `LocationLabel`).
- Historial compare-runs: deferred.
- iOS: solo previsto.

## Antes de publicar

- [ ] CI verde en la rama de release
- [ ] Recorrer casos manuales de `docs/test-evidence.md` (incl. walkthrough connected audit) y anotar dispositivo/fecha
- [ ] Confirmar que no hay secretos en fixtures versionados
- [ ] Revisar `docs/00-progress.md`
- [ ] Ejecutar checklist MANUAL FIX-01..04 en `docs/test-evidence.md` (idioma, Permisos ArrowBack, Lab guiado, DETENER, banner local-only)
- [ ] Decidir versionName / versionCode
