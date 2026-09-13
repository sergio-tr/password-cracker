# Walkthrough — Connected Known-Password Audit

Flujo novice (sin abrir opciones avanzadas):

```text
Cercanas
→ confirmar badge «Conectado» en red elegible (WPA/WPA2/WPA3 Personal)
→ abrir detalle → Auditar contraseña
→ Usar contraseña guardada (Vault, reveal diferido) o Introducir otra contraseña
→ modo Automático · duración 1 min (default)
→ INICIAR AUDITORÍA
→ ver métricas locales (intentos, tiempo, velocidad, etapa, presupuesto)
→ DETENER (bottomBar) o esperar fin del presupuesto
→ Informe: config Wi‑Fi vs resistencia · Medido/Estimado/Modelado
→ recomendaciones + «Cómo mejorarla»
```

Opcional (avanzado): cambiar preset 30 s / 5 min o límites personalizados;
«Restablecer configuración automática» vuelve al modo novice.

## Garantías

- El Search Engine funciona exclusivamente de forma local. Nunca envía candidatos
  a redes externas ni autentica contra access points.
- El planner automático es ciego al target (cambiar la contraseña no cambia el plan).
- Vault: la contraseña no se muestra en pantalla; se revela solo al iniciar.
- Guardar en Vault: OFF por defecto; nunca automático.

## Validación manual (dispositivo físico)

Ver sección **MANUAL NOT EXECUTED** en `docs/test-evidence.md` — cambiar
contraseña en router, reconectar, auditar, comparar runs con contraseña más fuerte.

## Follow-up

- Historial de auditorías comparables (sin secretos).
- Localización completa de Lab/Nearby/Vault/Onboarding.
