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
| Implementadas | Cercanas (Nearby), Guardadas (Vault), Laboratorio (config + ejecución + **resultado integrado**), Ajustes, **Onboarding** (primera ejecución), **Permission Center** (desde Ajustes), **Análisis de seguridad** (desde detalle de red), **Auditar contraseña** (Quick Audit). |
| Deferred | Lab Result como **ruta** propia (el resultado ya se muestra en Lab). |

## Principios aplicados

- **Progressive disclosure**: la tarjeta de red no se satura; los detalles
  técnicos van en el detalle.
- **Estados explícitos**: escaneo (`WifiScanState`) y búsqueda (`SearchState`) se
  modelan como enums/sealed, no como múltiples booleans.
- **Secretos ocultos** por defecto (`••••••••••••`); revelar es una acción explícita.
- **Cancelación siempre visible**: el botón `DETENER` vive en una barra de
  acciones fija (fuera del scroll) mientras el laboratorio **o** la auditoría
  rápida ejecutan; al pulsar pasa a `Deteniendo…` (deshabilitado) y termina en
  `CANCELADO`. La cancelación es prácticamente inmediata.
- **Quick Audit**: navegación con `ArrowBack` (sin botón textual «Atrás»);
  modo Automático por defecto; duración 30 s / 1 min / 5 min; Vault diferido;
  `saveToVault` OFF.
- **No bloquear el hilo principal**: la búsqueda corre en `Dispatchers.Default`;
  la UI se actualiza por batch.
- **Confirmaciones** sólo cuando evitan consecuencias reales (borrar una red).

## Nearby (ejemplo de tarjeta)

```
Casa                       Guardada
MOVISTAR_XXXX
WPA2/WPA3 · 5 GHz · Wi-Fi 6
Excelente
```

## Lab (durante la ejecución)

```
RUNNING
Intentos    1,284,420
Tiempo      00:00:13
Velocidad   98 k/s
Progreso    21.4 %
Fase        3 / 6
[ DETENER ] (barra fija)
```
