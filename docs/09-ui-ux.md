# 09 · UI / UX

Material 3, soporte light/dark, navegación con barra inferior (Redes, Guardadas,
Laboratorio, Ajustes).

## Pantallas

| Estado | Pantallas |
| --- | --- |
| Implementadas | Nearby Networks, Saved Networks (list + alta + borrado + revelar), Security Lab (config + ejecución + resultado), Settings. |
| Pendientes (scaffold documentado) | Onboarding, Permissions dedicada, Network Detail, Security Analysis detallada, Saved Network Detail/Edit, Lab Result como pantalla propia. |

## Principios aplicados

- **Progressive disclosure**: la tarjeta de red no se satura; los detalles
  técnicos van en el detalle.
- **Estados explícitos**: escaneo (`WifiScanState`) y búsqueda (`SearchState`) se
  modelan como enums/sealed, no como múltiples booleans.
- **Secretos ocultos** por defecto (`••••••••••••`); revelar es una acción explícita.
- **Cancelación siempre visible**: el botón `STOP` permanece mientras el
  laboratorio ejecuta, y la cancelación es prácticamente inmediata.
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
[ STOP ]
```
