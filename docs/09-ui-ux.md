# 09 · UI / UX

Material 3, soporte light/dark del sistema, navegación con barra inferior
(Cercanas, Guardadas, Laboratorio, Ajustes). Un usuario nuevo ve en Ajustes qué
hace cada pestaña. El detalle de una red cercana muestra primero nombre,
seguridad, señal y si está guardada; los datos avanzados se despliegan a
demanda. El laboratorio explica reto, coste, límites y DETENER antes y durante
la ejecución. Desde el detalle de una red cercana se puede abrir el Lab con
contexto (`LabNetworkContext`) sin autenticar contra el AP.

## Pantallas

| Estado | Pantallas |
| --- | --- |
| Implementadas | Branding **WiFiSearcher**. Lab OFF: Cercanas, Guardadas, Ajustes + flujo real «Probar contraseña» (`RealNetworkPasswordTestScreen`). Lab ON: añade **Laboratorio** (Synthetic / Prototype / Search Engine V2 / benchmarks / calibration). Nearby: Connected/Unknown **icon-only** + contentDescription. Onboarding, Permission Center, Análisis de seguridad. |
| Deferred | Implementación Android del adapter real (sigue `NotImplemented`). Lab Result como ruta propia. |

## Principios aplicados

- **Progressive disclosure**: la tarjeta de red no se satura; los detalles
  técnicos van en el detalle.
- **Estados explícitos**: escaneo (`WifiScanState`) y búsqueda (`SearchState`) se
  modelan como enums/sealed, no como múltiples booleans.
- **Secretos ocultos** por defecto (`••••••••••••`); revelar es una acción explícita.
- **Cancelación siempre visible**: el botón `DETENER` vive en una barra de
  acciones fija (`Scaffold.bottomBar`, fuera del scroll) mientras el laboratorio
  **o** la auditoría rápida ejecutan; al pulsar pasa a `Deteniendo…` (deshabilitado)
  y termina en `CANCELADO`. Icono `Icons.Filled.Stop`.
- **Lab guiado (FIX-04, Partial)**: arranca en prototipo local con pasos en
  lenguaje llano (SSID, WPA2/WPA3, contraseña, Iniciar prueba); alfabeto autoajustado;
  chip «Secreto aleatorio» opcional; resumen novice + aviso local-only al terminar.
  FIX-03/04 merged, CI green; `ProductRegressionComposeTest` cubre SSID visible sin Avanzado.
  **Manual pending user** — no marcar Implemented.
- **Real password test** (producto): `ArrowBack`; password + consentimiento; CTA con
  **mismo SSID** que la conexión actual (BSSID opcional); durante Running, `DETENER`
  fijo; resultados tipados (`Accepted` / `Inconclusive` / `Cancelled` / …).
- **Lab Mode OFF/ON**: OFF = producto real; ON = Real + Lab (Search Engine sólo en Lab).
- **Prueba Wi‑Fi FixedLab** (solo flavor `lab`): desde Laboratorio; AP fijo `LAB_WIFI_*`;
  no Search Engine; distinto del CTA Nearby real.
- **Idioma (FIX-01, Partial)**: selector en Ajustes (Sistema / Español / English) vía AppCompat
  per-app language + recreate; strings migrados a `values`/`values-en` (Lab, Nearby, Vault,
  Onboarding, Permissions, Security, Settings). FIX-01 merged [#44](https://github.com/sergio-tr/password-cracker/pull/44), CI green.
  **Manual pending user** — no marcar Implemented.
- **Navegación (FIX-02, Partial)**:
  - **Top level** (barra inferior: Cercanas, Guardadas, [Laboratorio si Lab ON], Ajustes): sin `ArrowBack`.
  - **Child** (`permissions`, `security_analysis`, `real_password_test`, `real_wifi_probe`):
    `ArrowBack` + CD `navigate_back` → `popBackStack` (limpia el target store). No hay botones
    de texto «Atrás».
  - **Copy novice (FIX-02)**: en Lab guiado y Quick Audit (superficies listas, no Avanzado
    colapsado) se evita jerga primaria («Workers», «Target», «Reasonable»); términos técnicos
    (p. ej. Paralelismo) solo en Opciones avanzadas con ayuda.
  - Iconografía en CTAs primarios (Refresh, Play/Stop, acciones Vault). Rama
    `feature/navigation-copy-consistency`. **Manual pending user**.
- **No bloquear el hilo principal**: la búsqueda corre en `Dispatchers.Default`;
  la UI se actualiza por batch.
- **Confirmaciones** sólo cuando evitan consecuencias reales (borrar una red).

## Nearby (ejemplo de tarjeta)

```
Casa                       Guardada
MOVISTAR_XXXX
WPA2/WPA3 · 5 GHz · Wi-Fi 6
Excelente                  [icono Wi‑Fi / contentDescription Conectado]
```

Red conectada elegible: CTA «Auditar contraseña» en el detalle.

## Quick Audit (durante la ejecución)

```
Auditando Casa
Intentos     1.284.420
Tiempo       00:13
Velocidad    98 k/s
Etapa        3 de 5
Presupuesto  68 %
[ DETENER ] (bottomBar fija, testTag audit_stop)
```

Tras completar: informe con badges Medido / Estimado / Modelado, resistencia
observada, recomendaciones y «Cómo mejorarla».
