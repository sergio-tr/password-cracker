# 13 · Manual UX gap audit (bloqueante)

Fecha: **2026-09-13**.  
Fuente de verdad: **prueba manual del usuario** + inspección de código en `main` (`269345b`).  
Prioridad sobre filas `Implemented` de `docs/00-progress.md` que contradigan este informe.

## Problemas confirmados

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

**Efecto:** con el dispositivo en inglés (o tras elegir English) el usuario ve ES e EN mezclados.

### P2 — Cambiar idioma en Ajustes no cambia la app

Causa raíz (código):

1. `MainActivity` es `ComponentActivity`, no `AppCompatActivity`.
2. No hay `AppCompatDelegate.installViewFactory()` en `attachBaseContext`.
3. No hay `android:localeConfig` / `LocaleConfig`.
4. Tras `setApplicationLocales` **no** se recrea la Activity.
5. La mayoría de pantallas usan strings **hardcoded** → inmunes al locale aunque el framework funcionara.

Selector: `SettingsScreen` → `AppLanguagePreferences.apply` → solo SharedPreferences + `setApplicationLocales`.

### P3 — Navegación / UX poco Material

| Hallazgo | Estado |
| --- | --- |
| Password Audit / Security Analysis | `ArrowBack` OK |
| Permission Center | **Sin** `navigationIcon` ni `onBack` |
| Recurso `audit_back` = «Atrás» | Definido, **no usado** en Compose (ruido) |
| Lab / Vault / Nearby | Labels y CTAs claros solo en ES hardcoded; sin pasada de iconografía coherente en hijo Permission Center |

La prueba manual del usuario (textos «Atrás» / acciones textuales confusas) se trata como **síntoma UX** a corregir en FIX-02 aunque el literal `Text("Atrás")` ya no esté en Compose.

### P4 — Lab sin prototipo local de red

El Lab actual:

* configura alfabeto, longitud, estrategia, límites, workers;
* genera secreto **oculto aleatorio** (`withHiddenSecret`);
* puede mostrar contexto **solo lectura** desde Nearby;
* **no** permite: perfil de red editable, mecanismo de auth seleccionable, contraseña objetivo custom, ni `EncapsulatedPasswordVerifier` / `withKnownSecret` desde la UI del Lab.

La auditoría known-password vive en **Password Audit** (red real conectada), no como prototipo local en Laboratorio.

## Correcciones planificadas (prioridad bloqueante)

| Fix | Rama | Objetivo |
| --- | --- | --- |
| FIX-01 | `fix/01-runtime-localization` | Locale runtime real + migrar strings visibles a `values` / `values-en` |
| FIX-02 | `fix/02-navigation-usability` | ArrowBack / iconografía / copy claro en child screens |
| FIX-03 | `fix/03-local-network-prototype` | Prototipo de red local configurable + motor local |
| FIX-04 | `fix/04-guided-local-prototype-audit` | Flujo guiado novice del prototipo |
| FIX-05 | `fix/05-product-regression-docs` | Tests de regresión + docs honestas |

## Criterio para volver a `Implemented`

Solo tras: implementación + test automático + CI verde + flujo manual coherente documentado.
