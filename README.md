# AquaAlerta App — Control y monitoreo IoT entre dos teléfonos Android

**Asignatura:** Aplicaciones Móviles para IoT (TI3042) · **Evaluación 4 – Unidad 2 (Sumativa)**
**Estudiante:** David Abraham Antonio Bustos Rebolledo · **Docente:** Andrés Antonio Rojas Ruiz
**Tecnología:** Android Studio · Kotlin · SQLite · Wi-Fi (TCP/IP) · AES-256-GCM

---

## 1. Problemática (continuación de la Unidad 1)

En la Unidad 1 construí **AquaAlerta**, un prototipo con Arduino que controla el llenado de un estanque:
un sensor de nivel HW-038 detecta cuándo el agua llega arriba, una válvula (servo SG90) se cierra y,
si el agua **sigue subiendo** (válvula trabada o fuga), se activa una **alarma de rebalse**.
El equipo solo avisaba con LEDs y buzzer **en el lugar**, y se comunicaba con un PC por cable USB.

**Problema que resuelve esta unidad:** la persona responsable no siempre está junto al estanque.
Necesita **ver el nivel y la alarma a distancia, en su teléfono**, y poder **silenciar o detener**
el sistema de forma segura, dejando registro de todo lo que pasó.

**Solución:** una aplicación Android que se instala en **dos teléfonos** que se comunican por **Wi-Fi**:

| Teléfono | Modo | Qué hace |
|---|---|---|
| A | **Estanque** (equipo IoT) | Simula el Arduino AquaAlerta: sensor de nivel, válvula, alarma y botón físico. Publica su estado. |
| B | **Monitor** (control remoto) | Se conecta al equipo, muestra el nivel en tiempo real, guarda el historial en SQLite y envía comandos protegidos. |

El modo Estanque usa **la misma lógica y el mismo protocolo de texto** que el sketch de Arduino
de la Unidad 1 (`TLM;t=..;ADC=..;ESTADO=..`, `S<clave>`, `P<clave>`), por lo que en la Unidad 3 el teléfono A
se puede reemplazar por el Arduino real con un módulo ESP8266/ESP32 sin cambiar la app Monitor.

## 2. Funcionalidades

1. **Login** con usuario y contraseña (SQLite local) y **registro** de usuarios.
2. **Roles**: `ADMIN` (monitorea, envía comandos y crea usuarios) y `OPERADOR` (solo monitorea).
3. **Modo Estanque**: simulación del sensor (0–1023), máquina de estados NORMAL → ALTO → REBALSE / PARO,
   válvula automática, alarma con tono y vibración, botón físico (toque = silenciar, mantener = PARO/reanudar).
4. **Modo Monitor**: conexión por IP, estado en colores, barra de nivel, alarma remota, bitácora en vivo.
5. **Comandos remotos**: consultar estado (`?`), silenciar (`S<PIN>`), paro de emergencia (`P<PIN>`).
6. **Historial**: consulta de lecturas y eventos guardados en SQLite, con resumen (máximo, promedio, n.º de rebalses).
7. **Seguridad** según ISO/IEC 27400 (detalle en la sección 7).

## 3. Herramientas de desarrollo y justificación

| Herramienta | Uso | Justificación |
|---|---|---|
| **Android Studio + Kotlin** | IDE y lenguaje | Lenguaje oficial recomendado por Google para Android; null-safety reduce errores. |
| **AGP 9.4 / SDK 37 (Android 17), minSdk 26** | Compilación | Android 8.0+ cubre prácticamente todos los teléfonos actuales y trae PBKDF2-SHA256 y AES-GCM nativos. |
| **AndroidX AppCompat + Material 3** | Interfaz | Estándar de diseño de Android (Material Design): tarjetas, campos con validación, colores por estado. |
| **ConstraintLayout / ScrollView** | Pantallas | Igual que el código del taller; se adapta a distintos tamaños de pantalla. |
| **SQLite (SQLiteOpenHelper)** | Usuarios, lecturas y eventos | Incluido en Android, sin costo ni Internet; funciona **offline**, igual que un equipo en terreno. |
| **java.net (Socket/ServerSocket)** | Comunicación Wi-Fi | Parte de Java, sin librerías externas; TCP garantiza orden y entrega de los mensajes. |
| **javax.crypto (AES-GCM, PBKDF2)** | Seguridad | Criptografía estándar incluida en Android, sin dependencias de terceros. |
| **JUnit 4** | Pruebas | 13 pruebas automáticas de la lógica, el protocolo y la seguridad. |

**Interoperabilidad de los datos:** la telemetría es texto `clave=valor` separado por `;`, idéntico al del Arduino.
Es legible, fácil de validar y se puede llevar a otra base de datos o API (Firebase/MySQL) sin transformarlo.

## 4. Comunicación inalámbrica: ¿por qué Wi-Fi (TCP)?

| Criterio | Wi-Fi (TCP/IP) ✅ | Bluetooth clásico | MQTT (broker en la nube) |
|---|---|---|---|
| Distancia | 30–50 m en interiores (más con la red del lugar) | ~10 m | Ilimitada, pero depende de Internet |
| Consumo | Medio (los teléfonos ya están en Wi-Fi) | Bajo | Medio + Internet |
| Estabilidad | Alta; TCP reintenta y ordena | Requiere emparejar; se corta con obstáculos | Depende del broker externo |
| Infraestructura | Red local o **hotspot de un teléfono** | Ninguna | Broker + Internet |
| Futuro con Arduino | ESP8266/ESP32 hablan TCP | HC-05 | ESP32 + broker |

Se eligió **Wi-Fi con sockets TCP** porque da más alcance que Bluetooth (el estanque suele estar lejos),
no depende de Internet ni de un servidor externo (si se cae Internet la alarma igual llega) y es lo que usará el
ESP8266/ESP32 en la siguiente unidad. Si la red del lugar aísla a los clientes, un teléfono crea un **hotspot** y el otro se conecta a él.

**Supervisión del enlace:** el equipo envía una trama por segundo; si el Monitor no recibe nada en **5 s**,
avisa *"enlace perdido"*. Así una alarma nunca queda oculta por una desconexión silenciosa.

## 5. Autenticación: ¿por qué SQLite local?

| Opción | Ventaja | Desventaja |
|---|---|---|
| **SQLite local** ✅ | Funciona sin Internet; datos quedan en el teléfono; sin costo | Cada teléfono tiene sus propios usuarios |
| Firebase Authentication | Usuarios centralizados, login con Google | Requiere Internet y cuenta de Google Cloud |
| API + MySQL | Control total del backend | Hay que construir y mantener un servidor |

En un entorno OT (planta, bodega, campo) **el control no puede depender de Internet**. Por eso se eligió
SQLite: el login y el historial funcionan offline. La seguridad se reforzó con contraseñas en **hash PBKDF2 + sal**,
bloqueo por intentos fallidos y roles. Para la Unidad 4 se puede sincronizar el historial con Firebase o una API.

## 6. Diseño: arquitectura, pantallas y navegación

### Arquitectura

```mermaid
flowchart LR
    subgraph A["📱 Teléfono A · Modo Estanque"]
        S[Sensor simulado<br/>SeekBar 0-1023] --> L[EstanqueLogica<br/>máquina de estados]
        L --> V[Válvula · Alarma<br/>Botón físico]
        L --> SV[Servidor TCP :5050]
        L --> DA[(SQLite<br/>eventos)]
    end
    subgraph B["📱 Teléfono B · Modo Monitor"]
        CL[Cliente TCP] --> UI[Estado en vivo<br/>alarma · bitácora]
        CL --> DB[(SQLite<br/>lecturas + eventos)]
        UI --> CMD[Comandos ? · S · P]
        CMD --> CL
    end
    SV <-- "Wi-Fi · AES-256-GCM<br/>TLM / EVT cada 1 s<br/>comandos con PIN" --> CL
```

### Pantallas y navegación (mockup)

```mermaid
flowchart TD
    LOGIN[Login<br/>usuario + contraseña] -->|primera vez| REG[Crear administrador]
    REG --> LOGIN
    LOGIN -->|credenciales OK| MENU[Menú<br/>saludo + rol]
    MENU --> EST[Modo Estanque<br/>IP · código de enlace · PIN<br/>nivel · estado · válvula · botón físico]
    MENU --> MON[Modo Monitor<br/>IP · código de enlace<br/>estado en vivo · comandos · bitácora]
    MENU --> HIS[Historial<br/>lecturas / eventos · resumen]
    MENU -->|solo ADMIN| USR[Crear usuario<br/>rol OPERADOR o ADMIN]
    MENU --> SALIR[Cerrar sesión]
```

| Pantalla | Elementos principales |
|---|---|
| **Login** | Fondo degradado azul, logo de gota, usuario, contraseña (con ojo para mostrar), botón Ingresar, mensaje de error. |
| **Menú** | Encabezado con nombre y rol; tarjetas *Modo Estanque*, *Modo Monitor*, *Historial*; crear usuario (ADMIN); cerrar sesión. |
| **Modo Estanque** | IP del teléfono, código de enlace, PIN; barra para simular el agua; tarjeta de estado de color (verde/naranjo/rojo/gris); válvula; botón físico; bitácora. |
| **Modo Monitor** | IP y puerto, código de enlace, Conectar; tarjeta de estado de color con barra de nivel; botones Consultar / Silenciar / PARO; bitácora. |
| **Historial** | Resumen (lecturas, eventos, ADC máx./prom., rebalses); pestañas Lecturas / Eventos; Actualizar; Borrar (ADMIN). |

**Colores de estado:** 🟢 NORMAL (llenando, válvula abierta) · 🟠 ALTO (lleno, válvula cerrada) · 🔴 REBALSE (alarma) · ⚫ PARO.

### Protocolo de mensajes (igual al Arduino de la Unidad 1)

| Dirección | Mensaje | Significado |
|---|---|---|
| Monitor → Equipo | `HOLA;usuario=david;rol=ADMIN` | Saludo inicial (si el código de enlace no coincide, el equipo lo rechaza) |
| Equipo → Monitor | `TLM;t=42;ADC=512;ESTADO=ALTO;VALVULA=CERRADA;SIL=0` | Telemetría, 1 vez por segundo |
| Equipo → Monitor | `EVT;t=43;ESTADO ALTO->REBALSE` | Evento (cambio de estado, alarma, paro…) |
| Monitor → Equipo | `?` | Consultar estado (no requiere PIN) |
| Monitor → Equipo | `S4827` / `P4827` | Silenciar / Paro, con PIN del equipo |
| Equipo → Monitor | `OK` / `ERR;CLAVE INCORRECTA 1/3` | Respuesta al comando |

Cada línea viaja **cifrada**: `Base64( IV de 12 bytes + AES-256-GCM( secuencia | mensaje ) )`.

### Base de datos SQLite (`aquaalerta.db`)

| Tabla | Campos |
|---|---|
| `usuarios` | id, usuario (único), hash, sal, rol, creado |
| `lecturas` | id, fecha, adc, estado, valvula, equipo |
| `eventos` | id, fecha, descripcion, origen, usuario |

## 7. Seguridad IoT según ISO/IEC 27400 (lineamientos OT)

| Riesgo | Medida implementada | Dónde |
|---|---|---|
| Robo de contraseñas desde el teléfono | Se guarda solo el **hash PBKDF2-HMAC-SHA256 (20 000 iteraciones) con sal aleatoria**; comparación en tiempo constante | `Seguridad.kt`, `BaseDatos.kt` |
| Contraseñas débiles o por defecto | **Sin usuarios por defecto**: el primer usuario se crea al instalar; mínimo 8 caracteres con letras y números | `RegistroActivity.kt` |
| Fuerza bruta en el login | **3 intentos fallidos → bloqueo de 30 s** (reloj de arranque, no se evita cambiando la hora); mensaje genérico que no revela si el usuario existe | `Sesion.kt`, `LoginActivity.kt` |
| Acceso no autorizado a funciones | **Roles** ADMIN / OPERADOR (mínimo privilegio); solo ADMIN crea usuarios, envía comandos y borra historial; cada pantalla exige sesión | `Sesion.kt`, `Utilidades.kt` |
| Espionaje de la red Wi-Fi | **Cifrado AES-256-GCM** de cada mensaje con llave derivada del código de enlace (PBKDF2) | `CanalSeguro.kt` |
| Mensajes falsos o modificados | GCM **autentica** cada mensaje: si se altera un bit o viene de otro código, se rechaza y se corta la conexión | `CanalSeguro.kt` |
| Repetición de comandos capturados (replay) | **Número de secuencia** cifrado; un mensaje repetido se rechaza | `CanalSeguro.kt` |
| Comandos críticos no autorizados | **PIN del equipo** para silenciar/paro; **3 PIN erróneos → bloqueo de 60 s** | `EstanqueLogica.kt` |
| Detener/arrancar el proceso a distancia por error | **Reanudar solo con el botón físico** (prioridad del control local, práctica OT); confirmación antes del PARO | `EstanqueLogica.kt`, `MonitorActivity.kt` |
| Falla del equipo o de la red | **Estado seguro**: en PARO la válvula queda cerrada; aviso de *enlace perdido* a los 5 s | `EstanqueLogica.kt`, `MonitorActivity.kt` |
| Datos de entrada maliciosos | Validación de largo y formato de comandos, tramas (ADC 0–1023, estados válidos), IP, PIN y usuario; consultas SQL **parametrizadas** | `Telemetria.kt`, `BaseDatos.kt` |
| Falta de trazabilidad | **Bitácora de auditoría**: logins, comandos, cambios de estado, borrado de historial (nunca se registra el PIN ni contraseñas) | `BaseDatos.kt` |
| Fuga de datos por respaldos | `allowBackup=false`; permisos mínimos (solo red y vibración) | `AndroidManifest.xml` |
| Acceso a la red local sin consentimiento | Permiso **`ACCESS_LOCAL_NETWORK`** (Android 17): la app lo **pide al usuario** antes de abrir el servidor o conectarse; si se niega, no hay conexión | `EstanqueActivity.kt`, `MonitorActivity.kt` |

> Sobre HTTPS/TLS: la guía menciona conexiones seguras (HTTPS, TLS). Como la comunicación es directa entre
> dos teléfonos en la red local (sin servidor web ni certificados), se aplicó el mismo principio que TLS
> —**confidencialidad + integridad + autenticación**— con AES-256-GCM sobre TCP.

## 8. Pruebas de funcionamiento

**Pruebas automáticas (JUnit, `./gradlew testDebugUnitTest`): 13 de 13 aprobadas.**

| Prueba | Resultado esperado | Resultado |
|---|---|---|
| Cambio de estado requiere 3 lecturas seguidas | No reacciona a olas | ✅ |
| Rebalse activa alarma y se puede silenciar | Silenciar no cambia el estado | ✅ |
| Histéresis en el umbral | No oscila entre NORMAL y ALTO | ✅ |
| PIN incorrecto 3 veces | Bloqueo de 60 s, incluso con el PIN correcto | ✅ |
| PARO cierra la válvula; `R` remoto rechazado | Solo se reanuda con el botón | ✅ |
| Comandos inválidos o muy largos | Rechazados | ✅ |
| Trama de telemetría ida y vuelta | Formato idéntico al Arduino | ✅ |
| Tramas manipuladas (ADC 5000, estado falso) | Descartadas | ✅ |
| Contraseña guardada como hash con sal | Nunca en texto plano | ✅ |
| Política de contraseñas | Rechaza `1234` y `solotexto` | ✅ |
| AES-GCM ida y vuelta | El PIN no aparece en lo transmitido | ✅ |
| Código de enlace distinto | No puede descifrar (AEADBadTagException) | ✅ |
| Canal seguro entre dos sockets TCP reales | Comando y respuesta llegan correctamente | ✅ |

**Pruebas de integración en dos dispositivos Android** (22/10/2026, dos emuladores Android 17 conectados
por TCP, uno en Modo Estanque y otro en Modo Monitor). Las capturas están en [`docs/capturas`](docs/capturas):

| # | Prueba | Resultado obtenido | Evidencia |
|---|---|---|---|
| 1 | Registro con contraseña débil (`1234`) | Rechazada: "Mínimo 8 caracteres, con letras y números" ✅ | 02 |
| 2 | Login con contraseña incorrecta | "Usuario o contraseña incorrectos" (mensaje genérico) ✅ | 03 |
| 3 | 3 intentos fallidos | "Demasiados intentos fallidos. Espere 28 s" (aun con la clave correcta) ✅ | 04 |
| 4 | Permiso de red local (Android 17) | La app lo solicita; al concederlo el equipo inicia ✅ | 06, 07 |
| 5 | Monitor con código de enlace distinto | Monitor: "El equipo cerró la conexión"; Estanque: "CONEXION RECHAZADA … código de enlace incorrecto o mensaje alterado" ✅ | 08, 09 |
| 6 | Conectar con el código correcto | "Conectado · canal cifrado AES-256-GCM", telemetría cada 1 s ✅ | 10 |
| 7 | Nivel ~500 | ALTO y válvula CERRADA en ambos dispositivos ✅ | 11, 12 |
| 8 | Nivel ~900 | REBALSE con alarma en ambos dispositivos ✅ | 13, 14 |
| 9 | Silenciar con PIN incorrecto | "CLAVE INCORRECTA 1/3", registrado en ambas bitácoras ✅ | 15, 16 |
| 10 | Silenciar con PIN correcto | "Respuesta: OK" / "ALARMA SILENCIADA POR REMOTO" ✅ | 17, 18 |
| 11 | PARO de emergencia (confirmación + PIN) | Equipo en PARO, válvula CERRADA ✅ | 19, 20, 21 |
| 12 | Reanudar | Solo con el botón físico (mantener): PARO→NORMAL, válvula ABIERTA ✅ | 22, 23 |
| 13 | Historial SQLite | 246 lecturas y 20 eventos con fecha, usuario y origen ✅ | 24, 25 |
| 14 | ADMIN crea usuario OPERADOR | Cuenta creada con rol OPERADOR ✅ | 26, 27 |
| 15 | OPERADOR en Monitor | Ve el estado en vivo; Silenciar y PARO deshabilitados ✅ | 28 |
| 16 | Detener el equipo con el Monitor conectado | "El equipo cerró la conexión (equipo detenido)", SIN DATOS ✅ | 29, 30 |

### Evidencia (capturas de pantalla)

| | | |
|:-:|:-:|:-:|
| <img src="docs/capturas/01_login.png" width="230"><br>01 Login | <img src="docs/capturas/02_registro_politica_contrasena.png" width="230"><br>02 Política de contraseñas | <img src="docs/capturas/04_login_bloqueado_3_intentos.png" width="230"><br>04 Bloqueo por intentos |
| <img src="docs/capturas/05_menu_admin.png" width="230"><br>05 Menú ADMIN | <img src="docs/capturas/06_permiso_red_local.png" width="230"><br>06 Permiso red local | <img src="docs/capturas/07_estanque_iniciado.png" width="230"><br>07 Estanque iniciado |
| <img src="docs/capturas/08_monitor_codigo_incorrecto.png" width="230"><br>08 Código incorrecto (Monitor) | <img src="docs/capturas/09_estanque_rechaza_codigo.png" width="230"><br>09 Rechazo (Estanque) | <img src="docs/capturas/10_monitor_conectado.png" width="230"><br>10 Conectado y cifrado |
| <img src="docs/capturas/11_estanque_alto.png" width="230"><br>11 ALTO (Estanque) | <img src="docs/capturas/12_monitor_alto.png" width="230"><br>12 ALTO (Monitor) | <img src="docs/capturas/13_estanque_rebalse_alarma.png" width="230"><br>13 REBALSE (Estanque) |
| <img src="docs/capturas/14_monitor_rebalse_alarma.png" width="230"><br>14 REBALSE (Monitor) | <img src="docs/capturas/15_monitor_pide_pin.png" width="230"><br>15 Pide PIN | <img src="docs/capturas/16_monitor_pin_incorrecto.png" width="230"><br>16 PIN incorrecto 1/3 |
| <img src="docs/capturas/17_monitor_alarma_silenciada.png" width="230"><br>17 Silenciada (Monitor) | <img src="docs/capturas/18_estanque_alarma_silenciada.png" width="230"><br>18 Silenciada (Estanque) | <img src="docs/capturas/19_monitor_confirmar_paro.png" width="230"><br>19 Confirmar PARO |
| <img src="docs/capturas/20_monitor_paro.png" width="230"><br>20 PARO (Monitor) | <img src="docs/capturas/21_estanque_paro.png" width="230"><br>21 PARO (Estanque) | <img src="docs/capturas/22_estanque_reanudado_boton.png" width="230"><br>22 Reanudado con botón |
| <img src="docs/capturas/24_historial_lecturas.png" width="230"><br>24 Historial: lecturas | <img src="docs/capturas/25_historial_eventos.png" width="230"><br>25 Historial: eventos | <img src="docs/capturas/26_admin_crea_operador.png" width="230"><br>26 ADMIN crea OPERADOR |
| <img src="docs/capturas/27_menu_operador.png" width="230"><br>27 Menú OPERADOR | <img src="docs/capturas/28_monitor_operador_sin_comandos.png" width="230"><br>28 OPERADOR sin comandos | <img src="docs/capturas/30_monitor_equipo_desconectado.png" width="230"><br>30 Equipo detenido |

## 9. Cómo ejecutar

1. Abrir la carpeta `AquaAlertaApp` en **Android Studio** y esperar la sincronización de Gradle.
2. Instalar en **dos teléfonos** (▶ Run con depuración USB, o copiar `app-debug.apk` e instalarlo).
3. Conectar ambos a la **misma red Wi-Fi** o, mejor, que el teléfono A active su **hotspot** y el B se conecte a él.
4. En cada teléfono: crear el usuario administrador → iniciar sesión.
5. **Teléfono A → Modo Estanque**: escribir un código de enlace (ej. `aqua2026`) y un PIN (ej. `4827`) → *Iniciar equipo*. Anotar la IP que muestra.
6. **Teléfono B → Modo Monitor**: escribir esa IP, puerto `5050` y el mismo código de enlace → *Conectar*.
7. Mover la barra del sensor en A y ver la reacción en B; probar Silenciar / PARO con el PIN.

En Android 17 la app pide el permiso **"dispositivos cercanos / red local"** al iniciar el equipo o al conectar: hay que **Permitir**.

**Con emuladores** (si no hay dos teléfonos):
1. Iniciar dos emuladores (`emulator-5554` = Estanque, `emulator-5556` = Monitor) e instalar la app en ambos.
2. Redirigir el puerto del Estanque al Mac: `adb -s emulator-5554 forward tcp:6000 tcp:5050`.
3. En el emulador Monitor, apagar su Wi-Fi virtual para que salga por la red `eth0`: `adb -s emulator-5556 shell svc wifi disable`.
4. En el Monitor conectar a la IP `10.0.2.2` (el Mac visto desde el emulador), puerto `6000`.

## 10. Estructura del código

```
app/src/main/java/cl/inacap/aquaalerta/
├── LoginActivity.kt       Login con bloqueo por intentos
├── RegistroActivity.kt    Crear administrador / usuarios con rol
├── MenuActivity.kt        Selección de modo según el rol
├── EstanqueActivity.kt    Equipo IoT simulado + servidor TCP
├── MonitorActivity.kt     Cliente TCP, estado en vivo y comandos
├── HistorialActivity.kt   Consulta de SQLite
├── EstanqueLogica.kt      Máquina de estados (traducida del Arduino)
├── Telemetria.kt          Formato y validación de tramas TLM
├── CanalSeguro.kt         TCP + AES-GCM + anti-replay
├── Seguridad.kt           PBKDF2, AES-GCM, política de contraseñas
├── BaseDatos.kt           SQLite: usuarios, lecturas, eventos
├── Sesion.kt              Usuario conectado, rol, bloqueo de login
└── Utilidades.kt          IP local, colores, alarma, márgenes
app/src/test/…             13 pruebas JUnit
```

## 11. Proyección (Unidades 3 y 4)

- Reemplazar el teléfono A por el **Arduino real + ESP8266/ESP32**, que envía las mismas tramas `TLM`.
- Sincronizar el historial con **Firebase** o una **API REST + MySQL** para consultarlo desde cualquier lugar.
- Notificaciones push cuando ocurra un rebalse con la app en segundo plano.
