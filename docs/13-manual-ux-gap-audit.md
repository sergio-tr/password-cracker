# 13 · Manual UX gap audit (bloqueante)

Fecha: **2026-09-25**.  
Fuente de verdad: **prueba manual del usuario** + inspección de código en `main` (post FIX-12).  
Prioridad sobre filas `Implemented` de `docs/00-progress.md` que contradigan este informe.

## Estado por problema (post FIX-01..12)

| ID | Problema | Fix | Estado automático | Estado manual |
| --- | --- | --- | --- | --- |
| P1 | Mezcla español / inglés | FIX-01 | **Code+CI green** — strings migrados; `ProductLocalizationRegressionTest`; `ComposeHardcodedStringGuardTest` (static guard Compose UI) | **Manual pending user** |
| P2 | Cambiar idioma no cambia la app | FIX-01 + FIX-05 | **Code+CI green** — `MainActivityRuntimeLocaleTest` (ES→EN, EN→ES, EN→SYSTEM, recreate persiste); `AppLanguagePreferencesInstrumentedTest` | **Manual pending user** |
| P3 | Navegación / UX poco Material | FIX-02 + FIX-05 | **Partial** — `ProductRegressionComposeTest`: ArrowBack en Audit/Security/Permissions; top-level Nearby/Vault/Lab/Settings sin ArrowBack | **Manual pending user** |
| P4 | Lab sin prototipo local de red | FIX-03A + FIX-03B + FIX-04 + FIX-05 | **Partial** — `LabComposeTest` + `ProductRegressionComposeTest`: WPA2 STOP→editar→repetir; OPEN/Enterprise sin campo PSK (`lab_prototype_no_password_audit`) | **Manual pending user** |
| P5 | Planner/Lab auth-aware (PSK/WEP, etapas, Guided+Advanced) | FIX-06…12 | **Code+CI green** — políticas PSK/WEP, explainability, Guided RandomHidden progressive, Advanced LocalPrototype = mismo planner; regresión JVM + emulador | **Manual pending user** — validar flujo producto en dispositivo |

FIX-05/08/12 cierran regresión automática + docs honestas; **no** promueven a `Implemented` sin pase manual del usuario.
Auth-aware Code+CI: **cerrado** (sin más FIX numerados abiertos).

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
* tarjeta con pasos SSID → seguridad → contraseña → evaluación de configuración → **Crear y probar** → **Iniciar prueba**;
* `GuidedAlphabetFitter` ajusta alfabeto al escribir (planner ciego al target, sin filtrar longitud);
* workers/estrategia/alfabeto solo bajo Opciones avanzadas (colapsadas por defecto);
* resultado separa **evaluación de configuración de red** y **resistencia de contraseña** (búsqueda);
* copy explícito: WPA2→WPA3 no implica que la misma contraseña sea menos predecible;
* acciones post-resultado: Editar contraseña, Cambiar seguridad, Repetir (preserva perfil del prototipo).

**FIX-05 (automático):** `ProductRegressionComposeTest`, `MainActivityRuntimeLocaleTest` (recreate), `ComposeHardcodedStringGuardTest`. Manual sigue pendiente.

El modo **Random hidden** sigue usando `withHiddenSecret`. La auditoría known-password contra red conectada permanece en **Password Audit**.

## Correcciones (prioridad bloqueante)

| Fix | Rama | PR | Objetivo | Estado |
| --- | --- | --- | --- | --- |
| FIX-01 | `fix/01-runtime-localization` | [#44](https://github.com/sergio-tr/password-cracker/pull/44) | Locale runtime real + migrar strings visibles a `values` / `values-en` | Merged · **Code+CI green / Manual pending user** |
| FIX-02 | `feature/navigation-copy-consistency` | [#45](https://github.com/sergio-tr/password-cracker/pull/45) | ArrowBack child screens + copy novice Lab/Audit (sin `Text("Atrás")`) | Merged · **Code+CI green / Manual pending user** |
| FIX-03 | `fix/03-local-network-prototype` | [#46](https://github.com/sergio-tr/password-cracker/pull/46) | Prototipo de red local configurable + motor local | Merged · **Code+CI green / Manual pending user** |
| FIX-04 | `fix/04-guided-local-prototype-audit` | [#47](https://github.com/sergio-tr/password-cracker/pull/47) | Flujo guiado novice del prototipo | Merged · **Code+CI green / Manual pending user** |
| FIX-05 | `test/product-regression-suite` | — | Suite regresión producto + docs honestas | **Code+CI green / Manual pending user** |
| FIX-06 | — | [#56](https://github.com/sergio-tr/password-cracker/pull/56) | `WifiPskProgressiveAuditPolicy` + planner auth-aware PSK | Merged · **Code+CI green / Manual pending user** |
| FIX-07 | — | [#57](https://github.com/sergio-tr/password-cracker/pull/57) | Lab guiado + Audit cableados; PSK ≥ 8; fitter separado | Merged · **Code+CI green / Manual pending user** |
| FIX-08 | `test/auth-aware-regression-and-docs` | — | Regresión auth-aware PSK + docs honestas (cierre ola) | En curso · **Automated CI green / Manual pending user** |

## Criterio para volver a `Implemented`

Solo tras: implementación + test automático + CI verde + flujo manual coherente documentado por el usuario.
