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
| **AGP 9.4 / SDK 37, minSdk 26** | Compilación | Android 8.0+ cubre prácticamente todos los teléfonos actuales y trae PBKDF2-SHA256 y AES-GCM nativos. |
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

**Pruebas manuales en dos teléfonos** (checklist para la demostración):

| # | Prueba | Esperado |
|---|---|---|
| 1 | Login con contraseña incorrecta 3 veces | Bloqueo 30 s |
| 2 | Conectar Monitor con código de enlace distinto | Rechazado en ambos teléfonos |
| 3 | Conectar con el código correcto | Telemetría cada 1 s |
| 4 | Subir el nivel a ~500 | ALTO, válvula cerrada, en ambos teléfonos |
| 5 | Subir a ~800 | REBALSE, alarma en ambos teléfonos |
| 6 | Silenciar desde el Monitor con PIN | Alarma silenciada |
| 7 | PARO desde el Monitor con PIN incorrecto ×3 | Comandos bloqueados 60 s |
| 8 | Reanudar desde el Monitor | Imposible; solo con botón físico |
| 9 | Apagar el Wi-Fi del Estanque | Monitor avisa *enlace perdido* en 5 s |
| 10 | Usuario OPERADOR en Monitor | Botones de comandos deshabilitados |
| 11 | Historial | Lecturas y eventos guardados con fecha |

## 9. Cómo ejecutar

1. Abrir la carpeta `AquaAlertaApp` en **Android Studio** y esperar la sincronización de Gradle.
2. Instalar en **dos teléfonos** (▶ Run con depuración USB, o copiar `app-debug.apk` e instalarlo).
3. Conectar ambos a la **misma red Wi-Fi** o, mejor, que el teléfono A active su **hotspot** y el B se conecte a él.
4. En cada teléfono: crear el usuario administrador → iniciar sesión.
5. **Teléfono A → Modo Estanque**: escribir un código de enlace (ej. `aqua2026`) y un PIN (ej. `4827`) → *Iniciar equipo*. Anotar la IP que muestra.
6. **Teléfono B → Modo Monitor**: escribir esa IP, puerto `5050` y el mismo código de enlace → *Conectar*.
7. Mover la barra del sensor en A y ver la reacción en B; probar Silenciar / PARO con el PIN.

**Con emuladores** (si no hay dos teléfonos): iniciar dos emuladores, abrir el Estanque en `emulator-5554` y ejecutar
`adb -s emulator-5554 forward tcp:6000 tcp:5050`; en el Monitor del segundo emulador conectar a `10.0.2.2`, puerto `6000`.

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
