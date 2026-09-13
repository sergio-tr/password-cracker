# 13 · Manual UX gap audit (bloqueante)

Fecha: **2026-09-13**.  
Fuente de verdad: **prueba manual del usuario** + inspección de código en `main` (post FIX-04).  
Prioridad sobre filas `Implemented` de `docs/00-progress.md` que contradigan este informe.

## Estado por problema (post FIX-01..04)

| ID | Problema | Fix | Estado automático | Estado manual |
| --- | --- | --- | --- | --- |
| P1 | Mezcla español / inglés | FIX-01 | **Code+CI green** — `AppCompatActivity` + `localeConfig` + strings migrados a `values`/`values-en`; tests i18n | **Manual pending user** |
| P2 | Cambiar idioma no cambia la app | FIX-01 | **Code+CI green** — recreate + `AppLanguagePreferences`; `ProductRegressionComposeTest` + `AppLanguagePreferencesInstrumentedTest` | **Manual pending user** |
| P3 | Navegación / UX poco Material | FIX-02 | **Partial** — `ArrowBack` en child screens verificado; copy novice Lab/Audit; sin `Text("Atrás")` | **Manual pending user** |
| P4 | Lab sin prototipo local de red | FIX-03A + FIX-03B | **Partial** — prototipo + evaluación + búsqueda PSK encapsulada (WPA personal); polish guiado = FIX-04; `LabComposeTest` + regresión | **Manual pending user** |

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
| Top level (Nearby, Vault, Lab, Settings) | Sin `ArrowBack` (barra inferior) |
| Password Audit / Security Analysis / Permission Center | `ArrowBack` + `onBack` → `popBackStack`; CD `navigate_back` |
| Recurso `audit_back` | Eliminado; solo `navigate_back` |
| Lab / Vault / Nearby CTAs | Iconografía en primarios (Refresh, Play/Stop, secret actions) |
| Copy novice (Lab guiado, Audit listo) | Sin jerga «Workers/Target/Reasonable» en superficies principales; Paralelismo solo en Avanzado |

Auditoría de código (2026-09-13): **no** hay `Text("Atrás")` en Compose. FIX-02 corregido cubre consistencia de navegación + copy de producto, no reintroducir botones de texto atrás.

### P4 — Lab sin prototipo local de red

**Partial (FIX-03A + FIX-03B):** el Lab permite crear un prototipo local configurable (`LabSecretMode.LocalPrototype`):

* SSID editable, presets (Abierta, WEP, WPA2/WPA3/transición, Enterprise), banda/estándar opcionales;
* evaluación `AssessNetworkSecurity` al cambiar perfil;
* **FIX-03B:** campo «Contraseña del prototipo» (oculta por defecto) solo en familias PSK personal;
* ruta producto: encapsulado → `withEncapsulatedVerifier` → planner automático (guiado) / motor existente;
* OPEN/Enterprise: evaluación sin campo ni CTA PSK engañoso;
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
| FIX-02 | `feature/navigation-copy-consistency` | — | ArrowBack child screens + copy novice Lab/Audit (sin `Text("Atrás")`) | En curso · **Manual pending user** |
| FIX-03 | `fix/03-local-network-prototype` | [#46](https://github.com/sergio-tr/password-cracker/pull/46) | Prototipo de red local configurable + motor local | Merged · **Code+CI green / Manual pending user** |
| FIX-04 | `fix/04-guided-local-prototype-audit` | [#47](https://github.com/sergio-tr/password-cracker/pull/47) | Flujo guiado novice del prototipo | Merged · **Code+CI green / Manual pending user** |
| FIX-05 | `fix/05-product-regression-docs` | — | Tests de regresión + docs honestas | En curso |

## Criterio para volver a `Implemented`

Solo tras: implementación + test automático + CI verde + flujo manual coherente documentado por el usuario.
