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
| Implementadas | Cercanas (Nearby), Guardadas (Vault), Laboratorio (modo guiado prototipo local + Avanzado + ejecución + **resultado integrado**), Ajustes, **Onboarding** (primera ejecución), **Permission Center** (desde Ajustes), **Análisis de seguridad** (desde detalle de red), **Auditar contraseña** (Quick Audit). |
| Deferred | Lab Result como **ruta** propia (el resultado ya se muestra en Lab). |

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
  Pendiente FIX-05 manual.
- **Quick Audit**: navegación con `ArrowBack` (`Icons.AutoMirrored.Filled.ArrowBack`,
  contentDescription localizado); modo Automático por defecto; duración 30 s / 1 min /
  5 min; Vault con reveal diferido; `saveToVault` OFF; icono `PlayArrow` en INICIAR.
- **Idioma**: selector en Ajustes (Sistema / Español / English) vía AppCompat
  per-app language; audit, settings, nav y security analysis localizados.
  Lab/Nearby/Vault/Onboarding aún hardcoded ES (Partial).
- **No bloquear el hilo principal**: la búsqueda corre en `Dispatchers.Default`;
  la UI se actualiza por batch.
- **Confirmaciones** sólo cuando evitan consecuencias reales (borrar una red).

## Nearby (ejemplo de tarjeta)

```
Casa                       Guardada
MOVISTAR_XXXX
WPA2/WPA3 · 5 GHz · Wi-Fi 6
Excelente                  [Conectado]
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
