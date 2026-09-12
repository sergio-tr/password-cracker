# ADR 0004 · CombinationCount sobre big-integer multiplataforma

- Estado: Aceptada
- Fecha: 2026-09-12

## Contexto

El tamaño del espacio de búsqueda puede superar 64 bits. Está prohibido usar
`Int`, `Long` (como representación universal) o `Double` para contar
combinaciones exactas. `java.math.BigInteger` no es multiplataforma.

## Decisión

Introducir el value object `CombinationCount` (en `:shared:core`) que envuelve
`com.ionspin.kotlin:bignum` (`BigInteger` multiplataforma). El resto del dominio
sólo depende de `CombinationCount`, no de la librería, de modo que sea sustituible.

Soporta: suma, multiplicación, potencia, comparación, cálculo de porcentaje y
formato (exacto agrupado y abreviado: `8.2 M`, `2.3 × 10^24`). El valor exacto se
conserva siempre.

## Consecuencias

- La aritmética de espacios enormes es exacta y testeada (`2^100`, `95^8`, …).
- Los contadores de intentos procesados usan `Long` internamente (una ejecución
  acotada nunca se aproxima a `Long.MAX`), mientras que el tamaño del espacio y
  `maxAttempts` usan `CombinationCount`.
