# Walkthrough — Connected Known-Password Audit

Flujo novice (sin abrir opciones avanzadas):

```text
Cercanas
→ seleccionar red conectada elegible (WPA/WPA2/WPA3 Personal o transición)
→ Auditar contraseña
→ introducir contraseña (manual) o Usar contraseña guardada
→ dejar duración 1 min (Automático)
→ INICIAR AUDITORÍA
→ ver métricas locales
→ DETENER o esperar el fin del presupuesto
→ leer Informe (Medido / Estimado / Modelado)
```

Garantías:

- El motor **no** autentica candidatos contra el router/AP.
- El planner automático es ciego al target (cambiar la contraseña no cambia el plan).
- Las opciones avanzadas (duración/intentos personalizados) son opcionales; el modo
  Automático con 1 minuto basta para un usuario sin conocimientos técnicos.
