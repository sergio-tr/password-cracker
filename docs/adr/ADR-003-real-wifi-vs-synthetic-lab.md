# ADR-003 · Separación física: Wi‑Fi real vs. Laboratorio sintético

- Estado: Aceptada
- Fecha: 2026-09-12
- Relacionado: [0006-lab-boundary-and-cancellation](./0006-lab-boundary-and-cancellation.md)

## Contexto

La app tiene dos dominios que jamás deben mezclarse:

- **Evaluación Wi‑Fi real**: escanear, inspeccionar, clasificar y guardar redes y
  credenciales del entorno del usuario.
- **Laboratorio de seguridad sintético**: retos con un secreto local oculto,
  estrategias de búsqueda, métricas, cancelación y límites.

El motor del laboratorio (`LabSearchEngine`) no debe poder depender de
`WifiScanner`, `WifiConnectionManager` ni de ninguna API de red de Android, y esa
frontera debe ser imposible de romper por accidente.

## Decisión

Imponer la frontera mediante el **grafo de módulos de Gradle**:

- `:shared:lab` depende únicamente de `:shared:core`.
- `:shared:lab` **no** depende de `:shared:assessment` (donde viven los puertos
  Wi‑Fi y el Vault).

Al no estar en el classpath, el laboratorio no puede importar tipos de red reales;
un intento de hacerlo rompe la compilación.

## Consecuencias

- La separación es estructural, no una convención revisable a mano.
- El laboratorio es totalmente sintético y determinista, testeable en la JVM.
- Cualquier necesidad de datos reales en el laboratorio obliga a un rediseño
  explícito (nuevo ADR), no a un import silencioso.
