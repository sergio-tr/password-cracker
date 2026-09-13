# Walkthrough — Connected Known-Password Audit

Flujo novice (sin abrir opciones avanzadas):

```text
Cercanas
→ seleccionar red conectada elegible (WPA/WPA2/WPA3 Personal o transición)
→ Auditar contraseña
→ introducir contraseña (manual) o Usar del Vault
→ dejar presupuesto Estándar (Automático)
→ INICIAR AUDITORÍA
→ ver métricas locales
→ DETENER o esperar el fin del presupuesto
→ leer Informe (Medido / Estimado / Modelado)
```

Garantías:

- El motor **no** autentica candidatos contra el router/AP.
- El planner automático es ciego al target (cambiar la contraseña no cambia el plan).
- Las opciones avanzadas (custom duration/attempts) son opcionales; el modo
  Estándar basta para un usuario sin conocimientos técnicos.
