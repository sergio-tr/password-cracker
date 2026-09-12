# 01 · Alcance del producto

## Objetivo

Aplicación Android didáctica de auditoría Wi-Fi que permite:

- Descubrir redes Wi-Fi visibles y mostrar su información técnica relevante.
- Identificar y **normalizar** sus mecanismos de seguridad/autenticación.
- Explicar al usuario, en lenguaje comprensible, qué significa la configuración.
- Identificar redes previamente guardadas (matching robusto, no por BSSID único).
- Guardar redes conocidas con alias y ubicación descriptiva.
- Gestionar de forma segura credenciales introducidas por el usuario (Vault, CRUD).
- Disponer de un **laboratorio sintético** para estudiar algoritmos de exploración
  de espacios de búsqueda, con progreso en tiempo real, límites y cancelación.

## Dos dominios, una frontera dura

| Real Wi-Fi Assessment | Synthetic Security Lab |
| --- | --- |
| Escanea redes reales | Crea desafíos sintéticos |
| Inspecciona metadatos de Android | Genera un secreto oculto local |
| Identifica y evalúa seguridad | Ejecuta estrategias de búsqueda |
| Guarda redes y credenciales | Recopila métricas, para por tiempo/intentos, se cancela |

**Prohibido**: conectar un generador de candidatos o un `LabSearchEngine` a una
red Wi-Fi real. La arquitectura impide que el laboratorio dependa de
`WifiScanner`, `WifiConnectionManager` o cualquier API Android de red.

## No-objetivos

- No se implementan heurísticas orientadas a atacar credenciales reales.
- No se realizan conexiones ni intentos de autenticación contra redes externas.
- La geolocalización real es opcional y sólo se almacena si el usuario lo pide.
