# 13 · Manual UX gap audit (bloqueante)

Fecha: **2026-09-13**.  
Fuente de verdad: **prueba manual del usuario** + inspección de código en `main` (post FIX-04).  
Prioridad sobre filas `Implemented` de `docs/00-progress.md` que contradigan este informe.

## Estado por problema (post FIX-01..04)

| ID | Problema | Fix | Estado automático | Estado manual |
| --- | --- | --- | --- | --- |
| P1 | Mezcla español / inglés | FIX-01 | **Code+CI green** — `AppCompatActivity` + `localeConfig` + strings migrados a `values`/`values-en`; tests i18n | **Manual pending user** |
| P2 | Cambiar idioma no cambia la app | FIX-01 | **Code+CI green** — recreate + `AppLanguagePreferences`; `ProductRegressionComposeTest` + `AppLanguagePreferencesInstrumentedTest` | **Manual pending user** |
| P3 | Navegación / UX poco Material | FIX-02 | **Code+CI green** — `ArrowBack` + CD `navigate_back` en child screens; iconografía CTAs | **Manual pending user** |
| P4 | Lab sin prototipo local de red | FIX-03 + FIX-04 | **Code+CI green** — prototipo local + flujo guiado novice; `LabComposeTest` + `ProductRegressionComposeTest` | **Manual pending user** |

FIX-05 (`fix/05-product-regression-docs`) añade `ProductRegressionComposeTest` y cierra docs; **no** promueve a `Implemented` sin pase manual del usuario.

## Problemas confirmados (snapshot pre-FIX, referencia histórica)

### P1 — Mezcla español / inglés

| Pantalla | Recursos | Hardcoded |
| --- | --- | --- |
| Password Audit UI | Mayoría `stringResource` | Pocos literales; **ViewModel** errores en ES fijo |
| Settings (cards principales) | Localizado | Benchmark / calibration en **EN** + «Estado:» en ES |
| Bottom nav | Localizado | — |
| Lab | **0** `stringResource` | 100 % ES |
| Vault | **0** | 100 % ES |
| Onboarding | **0** | 100 % ES |
| Nearby | Parcial (CTA audit) | ~75 % ES |
| Permission Center | **0** | ES UI + estados **EN** (`Granted`, `Missing`, …) |
| Security Analysis | Chrome localizado | Cuerpo ~80 % ES |

**Efecto (pre-FIX-01):** con el dispositivo en inglés (o tras elegir English) el usuario ve ES e EN mezclados.

**Post FIX-01 (código):** strings visibles migrados; runtime locale activo. Validación manual pendiente.

### P2 — Cambiar idioma en Ajustes no cambia la app

Causa raíz (código, pre-FIX-01):

1. `MainActivity` es `ComponentActivity`, no `AppCompatActivity`.
2. No hay `AppCompatDelegate.installViewFactory()` en `attachBaseContext`.
3. No hay `android:localeConfig` / `LocaleConfig`.
4. Tras `setApplicationLocales` **no** se recrea la Activity.
5. La mayoría de pantallas usan strings **hardcoded** → inmunes al locale aunque el framework funcionara.

**Post FIX-01 (código):** `AppCompatActivity`, `localeConfig`, recreate tras cambio de idioma, strings migrados. Validación manual pendiente.

### P3 — Navegación / UX poco Material

| Hallazgo | Estado |
| --- | --- |
| Password Audit / Security Analysis | `ArrowBack` OK |
| Permission Center | **FIX-02**: `ArrowBack` + `onBack` → `popBackStack`; CD unificado `navigate_back` |
| Recurso `audit_back` | **Eliminado** en FIX-02; solo `navigate_back` |
| Lab / Vault / Nearby | FIX-02: iconografía en CTAs primarios (Refresh, secret actions, Play/Stop CD); localización FIX-01 |

La prueba manual del usuario (textos «Atrás» / acciones textuales confusas) se trata como **síntoma UX** a corregir en FIX-02 aunque el literal `Text("Atrás")` ya no esté en Compose.

### P4 — Lab sin prototipo local de red

**Partial (FIX-03):** el Lab permite crear un prototipo local configurable (`LabSecretMode.LocalPrototype`):

* SSID editable, familia PSK personal (WPA/WPA2/WPA3/WPA2+WPA3), banda/estándar opcionales;
* contraseña objetivo en memoria (se borra al iniciar);
* búsqueda local vía `EncapsulatedPasswordVerifier` + `LabChallenge.withEncapsulatedVerifier`;
* banner «Búsqueda solo local» siempre visible en modo prototipo.

**Partial (FIX-04):** flujo guiado novice del prototipo local:

* modo guiado arranca en `LocalPrototype` (chip «Secreto aleatorio» opcional);
* tarjeta con pasos SSID → seguridad WPA2/WPA3 → contraseña → Iniciar prueba;
* alfabeto y longitud autoajustados al escribir la contraseña;
* workers/estrategia/alfabeto solo bajo Opciones avanzadas;
* resumen novice (resistencia observada + aviso local-only) tras Found/Límite/Cancelado.

El modo **Random hidden** sigue usando `withHiddenSecret`. La auditoría known-password contra red conectada permanece en **Password Audit**.

## Correcciones (prioridad bloqueante)

| Fix | Rama | PR | Objetivo | Estado |
| --- | --- | --- | --- | --- |
| FIX-01 | `fix/01-runtime-localization` | [#44](https://github.com/sergio-tr/password-cracker/pull/44) | Locale runtime real + migrar strings visibles a `values` / `values-en` | Merged · **Code+CI green / Manual pending user** |
| FIX-02 | `fix/02-navigation-usability` | [#45](https://github.com/sergio-tr/password-cracker/pull/45) | ArrowBack / iconografía / copy claro en child screens | Merged · **Code+CI green / Manual pending user** |
| FIX-03 | `fix/03-local-network-prototype` | [#46](https://github.com/sergio-tr/password-cracker/pull/46) | Prototipo de red local configurable + motor local | Merged · **Code+CI green / Manual pending user** |
| FIX-04 | `fix/04-guided-local-prototype-audit` | [#47](https://github.com/sergio-tr/password-cracker/pull/47) | Flujo guiado novice del prototipo | Merged · **Code+CI green / Manual pending user** |
| FIX-05 | `fix/05-product-regression-docs` | — | Tests de regresión + docs honestas | En curso |

## Criterio para volver a `Implemented`

Solo tras: implementación + test automático + CI verde + flujo manual coherente documentado por el usuario.
