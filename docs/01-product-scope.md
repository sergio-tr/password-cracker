# 01 · Alcance del producto

## Objetivo

Aplicación Android didáctica de auditoría Wi-Fi que permite:

- Descubrir redes Wi-Fi visibles y mostrar su información técnica relevante.
- Identificar y **normalizar** sus mecanismos de seguridad/autenticación.
- Explicar al usuario, en lenguaje comprensible, qué significa la configuración.
- Identificar redes previamente guardadas (matching robusto, no por BSSID único).
- Guardar redes conocidas con alias y ubicación descriptiva.
- Gestionar de forma segura credenciales introducidas por el usuario (Vault, CRUD).
- **Auditar la resistencia de una contraseña Wi-Fi conocida** (Quick Audit): el
  Search Engine local mide cuánto tarda en encontrarla dentro de un presupuesto;
  la red real solo aporta contexto y elegibilidad — nunca se autentica contra el AP.
- Disponer de un **laboratorio sintético** para estudiar algoritmos de exploración
  de espacios de búsqueda, con progreso en tiempo real, límites y cancelación.

## Dos dominios, una frontera dura

| Real Wi-Fi Assessment | Search Engine (local) |
| --- | --- |
| Escanea redes reales | Verifica candidatos solo en memoria local |
| Inspecciona metadatos de Android | Puede usar challenges sintéticos o contraseña conocida del usuario |
| Identifica y evalúa seguridad | Ejecuta estrategias de búsqueda con límites y cancelación |
| Guarda redes y credenciales | Recopila métricas; nunca envía candidatos al router |

**Prohibido**: conectar un generador de candidatos o un `LabSearchEngine` a
autenticación Wi-Fi real. La arquitectura impide que el laboratorio dependa de
APIs de conexión Android para verificar candidatos.

## Known-password audit (producto)

Flujo novice: red conectada elegible → Auditar contraseña → Vault o manual →
Automático → INICIAR → métricas → DETENER o fin de presupuesto → informe.

- El motor es **exclusivamente local** (`EncapsulatedPasswordVerifier`).
- El planner es **ciego al target** (`AutomaticPasswordAuditPlanner`).
- El informe **separa** configuración Wi‑Fi y resistencia de contraseña
  (`WifiPasswordAuditResult`).
- Historial de runs comparables = follow-up; no forma parte del alcance actual.

## No-objetivos

- No se implementan heurísticas orientadas a atacar credenciales reales.
- No se realizan conexiones ni intentos de autenticación contra redes externas.
- La geolocalización real es opcional y sólo se almacena si el usuario lo pide.
