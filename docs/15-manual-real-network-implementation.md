# 15 · Punto de implementación manual — prueba real de contraseña

Documento alineado con el código actual. Rutas y símbolos **exactos**.

Buscar en el repo: `USER_IMPLEMENTATION_POINT_REAL_WIFI` (una sola ocurrencia en producción).

---

# Política de red (producto)

```text
Network target = red seleccionada en Nearby (o Vault)

SSID     = requerido (debe coincidir con la Wi‑Fi actualmente asociada)
BSSID    = selector adicional opcional (si está disponible se pasa al Specifier)
Familia  = WPA2 / WPA3 / WPA2+WPA3 Personal
Permisos = gate Android
```

`NetworkConnectionMatch` (Exact/Probable) **no** se modifica.
`RealNetworkPasswordEligibilityChecker` mapea Exact **y** Probable → `Executable`.

---

# Flujo completo

```text
androidApp/.../ui/nearby/NearbyScreen.kt
  RealPasswordTestSection → Button(onClick = onOpenRealPasswordTest)
→
androidApp/.../MainActivity.kt
  RealNetworkPasswordTestTargetStore.set(...)
  navigate(Routes.REAL_PASSWORD_TEST)
→
androidApp/.../ui/realtest/RealNetworkPasswordTestScreen.kt
  viewModel.startRealNetworkTest()
→
androidApp/.../ui/realtest/RealNetworkPasswordTestViewModel.kt
  eligibilityChecker.check(...) → Executable?
  → RealNetworkPasswordExecutionPort.execute(request)
→
shared/assessment/.../real/RealNetworkPasswordExecution.kt
  RealNetworkPasswordExecutionPort
→
androidApp/.../wifi/AndroidRealNetworkPasswordExecutionAdapter.kt
  WifiNetworkSpecifier + requestNetwork + NetworkCallback
  ★ USER_IMPLEMENTATION_POINT_REAL_WIFI
```

DI: `AppModule.kt`  
`single<RealNetworkPasswordExecutionPort> { AndroidRealNetworkPasswordExecutionAdapter(androidContext()) }`

---

# Archivo 1 — Botón

```text
Ruta: androidApp/.../ui/nearby/NearbyScreen.kt
Composable: RealPasswordTestSection
Callback: onOpenRealPasswordTest
String: R.string.real_test_cta («Probar contraseña en esta red»)
Visible: RealNetworkPasswordEligibility.Executable (SSID match; BSSID opcional)
```

---

# Archivo 2 — Navegación

```text
Ruta: androidApp/.../MainActivity.kt
Routes.REAL_PASSWORD_TEST = "real_password_test"
Store: RealNetworkPasswordTestTargetStore (sin password en nav args)
```

---

# Archivo 3 — ViewModel

```text
Ruta: androidApp/.../ui/realtest/RealNetworkPasswordTestViewModel.kt
Método: fun startRealNetworkTest()
Fuente de verdad: eligibilityChecker.check → Executable
No duplica lógica Exact-only.
```

---

# Archivo 4 — Port

```text
Ruta: shared/assessment/.../real/RealNetworkPasswordExecution.kt
Interface: RealNetworkPasswordExecutionPort
Request: network + SensitiveCredential + RealNetworkConsent
Resultados: Accepted | Rejected | Inconclusive | Cancelled | Unavailable | Failed
```

---

# Archivo 5 — Adapter Android

```text
ESTE ES EL ÚNICO ARCHIVO DE PLATAFORMA PARA LA PRUEBA REAL MANUAL.

Ruta: androidApp/.../wifi/AndroidRealNetworkPasswordExecutionAdapter.kt
Método: execute(request)
Marker: USER_IMPLEMENTATION_POINT_REAL_WIFI

WifiNetworkSpecifier.Builder()
  .setSsid(ssid)
  .setBssid(...)   // sólo si request.network.bssid != null

onAvailable  → Accepted
onUnavailable → Inconclusive   (NO Rejected automático)
timeout / cancel → Inconclusive / Cancelled
```

Política BSSID testeable: `RealNetworkSpecifierPolicy.optionalBssidValue`.

---

# Lab Mode

| OFF | Cercanas, Guardadas, Ajustes + flujo real |
| ON  | + Laboratorio (Search Engine V2 local) |

Search Engine **no** está conectado a este adapter.
