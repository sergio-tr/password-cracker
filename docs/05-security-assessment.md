# 05 · Security Assessment

## Strategy Registry

```kotlin
interface SecurityAssessmentStrategy {
    fun supports(profile: WifiSecurityProfile): Boolean
    suspend fun assess(profile: WifiSecurityProfile): SecurityAssessment
}
```

`SecurityAssessmentRegistry` selecciona la primera estrategia que soporta el
perfil; un *catch-all* (`UnsupportedAssessmentStrategy`) mantiene el resultado
total. Esto evita `when(security){…}` gigantes repartidos por ViewModels: añadir
un mecanismo nuevo = añadir una estrategia nueva.

## Estrategias

| Estrategia | Familias | Veredicto típico |
| --- | --- | --- |
| `OpenNetworkAssessmentStrategy` | OPEN | INSECURE |
| `LegacyNetworkAssessmentStrategy` | WEP, WPA1 | INSECURE |
| `PersonalNetworkAssessmentStrategy` | WPA2/WPA3/transición personal | MODERATE / HIGH |
| `EnterpriseNetworkAssessmentStrategy` | WPA2/WPA3 Enterprise | HIGH |
| `OweAssessmentStrategy` | OWE | MODERATE |
| `PasspointAssessmentStrategy` | Passpoint | HIGH |
| `DppAssessmentStrategy` | DPP | HIGH |
| `UnsupportedAssessmentStrategy` | fallback | LOW |

## Salida

`SecurityAssessment` separa el lenguaje comprensible (`headline`,
`plainExplanation`) del técnico (`technicalSummary`, `findings`) para permitir
*progressive disclosure* en la UI (primero "Seguridad: Alta / WPA3-Personal",
luego "¿Qué significa?" y "Detalles técnicos").
