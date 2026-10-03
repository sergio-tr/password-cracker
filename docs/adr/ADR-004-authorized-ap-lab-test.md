# ADR-004 · LOCAL_AUDIT vs LAB_NETWORK_VALIDATION (laboratorio autorizado)

- Estado: Accepted — **F0 DONE · F1 DONE** (2026-10-03)
- Fecha: 2026-10-03
- Relacionado: [ADR-003](./ADR-003-real-wifi-vs-synthetic-lab.md),
  [ADR-connected-known-password-audit](./ADR-connected-known-password-audit.md),
  [0006-lab-boundary-and-cancellation](./0006-lab-boundary-and-cancellation.md),
  [14-authorized-lab-validation-plan](../14-authorized-lab-validation-plan.md)

Código: `VerificationMode.LAB_NETWORK_VALIDATION` (antes referido como AUTHORIZED_AP_TEST).

## Contexto

El producto es una herramienta **didáctica / de investigación** sobre redes
**propias**, de **laboratorio** o **expresamente autorizadas**. Debe poder:

1. Auditar una contraseña **ya conocida** solo en local (`LOCAL_AUDIT`) — ya
   implementado.
2. Estudiar e, cuando la plataforma lo permita, ejecutar un modo
   `LAB_NETWORK_VALIDATION` que compruebe candidatos mediante interacción real con
   el AP de la red a la que el dispositivo **está conectado**.

Esto **no** es exploración ofensiva: no se permite elegir una red arbitraria del
escaneo y lanzar pruebas de credenciales contra ella.

El requisito **no se descarta** por etiquetarlo genéricamente como «ataque».
Las restricciones de Android se registran como **limitaciones técnicas**.

## Decisión de producto

| Modo | Verificación | Objetivo permitido | Entrada |
| --- | --- | --- | --- |
| `LOCAL_AUDIT` | `EncapsulatedPasswordVerifier` en memoria | Contexto de red (SSID/familia); sin hablar con el AP | Cercanas (conectada) o Vault (credencial) |
| `LAB_NETWORK_VALIDATION` | Orquestador de sesión (**F1 DONE**); sondeo AP real = **Planned F2** (`AndroidValidationAdapter` = Unavailable en stock) | **Solo** la conexión Wi‑Fi **actual** | **Solo** Cercanas → badge Conectado → Auditar |

Reglas fail-closed para `LAB_NETWORK_VALIDATION` (gate `AuthorizedApTestGate`):

1. `labModeEnabled` (Ajustes; default false).
2. SSID/BSSID objetivo ≡ conexión Wi‑Fi actual (match Exact o Probable).
3. La red está marcada explícitamente como **laboratorio / autorizada** en la app.
4. Consentimiento explícito del usuario en ese arranque (no global eterno).
5. Capability de `NetworkValidationAdapter` = Available.
6. No se admiten SSID/BSSID arbitrarios introducidos a mano como objetivo.
7. Si falla cualquiera → **Denied(reasons)**; no arranca.
8. Límites duros de velocidad, intentos y cancelación cooperativa (**F1**).
9. Si cambia la red durante la prueba → **STOP** inmediato (**F1**).
10. Vault puede aportar **credencial/candidato**, pero **nunca** redefine el
    objetivo: el objetivo lo fija siempre la conexión actual.

## Separación arquitectónica (crítica)

```
:shared:lab          → motor de búsqueda + CandidateVerifier inyectable
                       (sin APIs Android; sin dependencia de assessment)

:shared:assessment   → elegibilidad, autorización de lab, puertos de sondeo AP

androidApp           → adaptadores WifiManager / ConnectivityManager
```

- `LOCAL_AUDIT` sigue usando el `LabSearchEngine` de alto throughput con
  verificador encapsulado.
- `LAB_NETWORK_VALIDATION` **no** reutiliza ese bucle a miles/millones de intentos/s
  contra el AP. Usa un **orquestador aparte** (`LabValidationSessionOrchestrator`,
  **F1 DONE**) con presupuestos muy bajos, porque las APIs públicas de Android no
  soportan verificación silenciosa y masiva de passphrases.

Romper ADR-003 (lab → assessment / WifiManager) queda **prohibido**.

## Análisis: recuperación de PSK del sistema Android

Finalidad legítima: evitar reintroducir una credencial del propio entorno de
pruebas. **No** es extracción de credenciales de terceros.

### 1) Android estándar (app Play / sin privilegios especiales)

| API | Resultado |
| --- | --- |
| `WifiManager.getConfiguredNetworks()` | Lista de redes configuradas; `WifiConfiguration.preSharedKey` viene **enmascarada** (`"*"`) si hay valor. |
| Lectura de la PSK de la red conectada | **No disponible** para apps normales. Limitación deliberada de la plataforma. |

**Arquitectura alternativa (producto actual):** PSK manual, Vault propio
(Keystore AEAD), o seed one-shot Vault→Lab tras reveal explícito (LAB-06).

### 2) Device Owner / Profile Owner (dispositivo administrado)

- Puede **crear/actualizar** configuraciones Wi‑Fi (APIs privilegiadas /
  `WifiManager.addNetworkSuggestions` / flujos DO según versión).
- **No** implica una API pública estable para que *cualquier* app DO lea la PSK
  de redes que **no creó**.
- En la práctica: el laboratorio administrado debe **registrar** la PSK al dar
  de alta la red de prueba (Vault o config creada por el propio DO), no
  «leerla del sistema a posteriori».

### 3) APIs de sistema / permisos privilegiados

- `WifiManager.getPrivilegedConfiguredNetworks()` (@SystemApi / oculto) puede
  devolver `preSharedKey` real.
- Requiere permisos de firma / privilegio de plataforma (p. ej.
  `READ_WIFI_CREDENTIAL` y app de sistema). **No** disponible para una app
  instalable normal.
- Encaja solo en builds de laboratorio firmadas como sistema (imagen AOSP /
  dispositivo de investigación), fuera del producto Play.

### 4) Root / acceso elevado

- Técnicamente se puede inspeccionar almacenes del framework/supplicant
  según versión y fabricante.
- **Fuera de alcance del producto** instalable; si algún día hubiera un
  adaptador experimental, sería módulo opcional no empaquetado en release
  Play, con fail-closed por defecto.

**Conclusión PSK:** en el producto estándar la fuente de verdad de la
credencial de prueba es **Vault / entrada manual / registro en alta**. La
lectura del almacén Wi‑Fi de Android queda documentada como limitación
técnica, no como requisito abandonado por «ser ofensivo».

## Análisis: autenticación real contra el AP (APIs normales)

### Camino público más cercano (Android 10+)

`WifiNetworkSpecifier` + `ConnectivityManager.requestNetwork`:

- Permite pedir una conexión local-only con SSID/BSSID +
  `setWpa2Passphrase` / `setWpa3Passphrase`.
- Éxito ≈ autenticación aceptada; fallo / `onUnavailable` ≈ rechazo o
  imposibilidad.
- Costes y límites reales:
  - A menudo **diálogo de sistema** / confirmación de usuario.
  - Puede **interrumpir** o competir con la conexión primaria.
  - Latencia de segundos por intento; **inviable** como verificador del
    `LabSearchEngine` de alto throughput.
  - Comportamiento heterogéneo entre OEMs y versiones.
  - No hay API pública de «EAPOL probe silencioso / sin UI» para apps normales.

### Implicación de diseño

`LAB_NETWORK_VALIDATION` se modela como:

```text
labModeEnabled + gate fail-closed
  → consentimiento de sesión
  → LabValidationSessionOrchestrator (F1: presupuesto, timeout, STOP, monitors)
       → NetworkValidationAdapter.validateOnce(AuthorizedValidationContext)
```

No se enchufa el adapter como `CandidateVerifier` del motor paralelo V2.

**F0 (Implemented):** foundation gate/registry/UI; adapter Unavailable.
**F1 (Implemented):** sesión tipada, orquestador, snapshot, monitors (red / Lab Mode /
registry), budget, timeout, cancel, concurrencia=1, evidence sanitizada, UI phases.
`AndroidValidationAdapter` sigue **Unavailable** (no se declara Available sin path
público demostrado). Sondeo activo = **Planned F2**.

## Modelo de autorización (app)

Una red queda autorizada para AP test solo si el usuario la marca
explícitamente (`AuthorizedLabNetworkStore`: SSID + familia, persistido en
prefs del proceso). El gate comprueba esa marca **y** la conexión actual.

Vault:

- CTA «Auditar» → siempre puede ser `LOCAL_AUDIT`; Vault **no** ofrece
  `LAB_NETWORK_VALIDATION` (`allowsAuthorizedApTest = false`).
- Vault **no** lanza LAB validation contra la SSID del registro si no
  coincide con la conexión actual.

## Fases de implementación

| Fase | Entrega | Estado |
| --- | --- | --- |
| **F0** | ADR + `VerificationMode` + `labModeEnabled` + gate + registry + `NetworkValidationAdapter` Unavailable + UI selector/consent/denials + tests + docs | **DONE** |
| **F1** | `LabValidationSession` + orchestrator + snapshot + monitors + budget/timeout/cancel + evidence + UI + tests; Android adapter sigue Unavailable | **DONE** |
| **F2** | Probe AP acotado si capability pública demostrada; marcado lab Nearby/Vault; hardening backup | Planned |
| **F3 (opcional, builds privilegio)** | Adaptador `READ_WIFI_CREDENTIAL` / imagen sistema — nunca en variante Play | Planned |

## Consecuencias

- El alcance A (local) + C (AP autorizado) queda **explícito** y no se
  reinterpreta como «solo simulación».
- Las limitaciones de Android se documentan sin cambiar el requisito.
- La frontera ADR-003 se mantiene: el motor sintético no gana WifiManager.
- Fallar cerrado es correcto: mejor «no disponible en esta plataforma» que
  un falso positivo de seguridad.
