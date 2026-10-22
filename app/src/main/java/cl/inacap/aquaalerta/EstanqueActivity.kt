package cl.inacap.aquaalerta

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.security.GeneralSecurityException
import java.util.concurrent.Executors
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.thread
import kotlin.random.Random

/**
 * MODO ESTANQUE: el telefono actua como el equipo IoT AquaAlerta.
 *  - Un SeekBar simula el sensor de nivel (ADC 0..1023) y cada 250 ms se procesa la lectura.
 *  - Un servidor TCP (puerto 5050) espera al telefono Monitor y le envia telemetria cada 1 s.
 *  - Recibe comandos remotos (?, S<clave>, P<clave>) por el canal cifrado.
 */
class EstanqueActivity : AppCompatActivity() {

    private lateinit var db: BaseDatos
    private lateinit var alarma: Alarma
    private val manejador = Handler(Looper.getMainLooper())
    private val envios = Executors.newSingleThreadExecutor()   // la red nunca se usa en el hilo principal
    private val bitacora = ArrayDeque<String>()
    private var ciclos = 0

    private val pedirPermisoRed = registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) iniciarEquipo()
        else aviso("Sin el permiso de red local el Monitor no podrá conectarse")
    }

    @Volatile private var logica: EstanqueLogica? = null
    @Volatile private var servidor: ServerSocket? = null
    @Volatile private var canal: CanalSeguro? = null

    private lateinit var tvIp: TextView
    private lateinit var edtCodigo: EditText
    private lateinit var edtClave: EditText
    private lateinit var btnIniciar: Button
    private lateinit var tvConexion: TextView
    private lateinit var seekNivel: SeekBar
    private lateinit var tvAdc: TextView
    private lateinit var barNivel: ProgressBar
    private lateinit var cardEstado: MaterialCardView
    private lateinit var tvEstado: TextView
    private lateinit var tvValvula: TextView
    private lateinit var tvAlarma: TextView
    private lateinit var btnFisico: Button
    private lateinit var tvBitacora: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (exigirSesion()) return
        enableEdgeToEdge()
        setContentView(R.layout.activity_estanque)
        ajustarBordes(findViewById(R.id.main))
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        db = BaseDatos.obtener(this)
        alarma = Alarma(this)
        tvIp = findViewById(R.id.tvIp)
        edtCodigo = findViewById(R.id.edtCodigo)
        edtClave = findViewById(R.id.edtClave)
        btnIniciar = findViewById(R.id.btnIniciar)
        tvConexion = findViewById(R.id.tvConexion)
        seekNivel = findViewById(R.id.seekNivel)
        tvAdc = findViewById(R.id.tvAdc)
        barNivel = findViewById(R.id.barNivel)
        cardEstado = findViewById(R.id.cardEstado)
        tvEstado = findViewById(R.id.tvEstado)
        tvValvula = findViewById(R.id.tvValvula)
        tvAlarma = findViewById(R.id.tvAlarma)
        btnFisico = findViewById(R.id.btnFisico)
        tvBitacora = findViewById(R.id.tvBitacora)

        val ips = direccionesIpLocales()
        tvIp.text = (if (ips.isEmpty()) "Sin red: conecte Wi-Fi o active el hotspot" else
            "IP de este teléfono:\n" + ips.joinToString("\n")) + "\nPuerto: $PUERTO_EQUIPO"

        habilitarSimulacion(false)
        btnIniciar.setOnClickListener { if (logica == null) iniciarEquipo() else detenerEquipo() }

        // Boton fisico: toque corto = silenciar, mantener = PARO / reanudar (igual que el pulsador del Arduino)
        btnFisico.setOnClickListener {
            logica?.let { registrar(it.silenciar("BOTON")) }
            actualizarPantalla()
        }
        btnFisico.setOnLongClickListener {
            logica?.let { registrar(if (it.estado == Estado.PARO) it.reanudar("BOTON") else it.detener("BOTON")) }
            actualizarPantalla()
            true
        }
    }

    private fun iniciarEquipo() {
        val codigo = edtCodigo.text.toString()
        val clave = edtClave.text.toString()
        edtCodigo.mostrarError(null)
        edtClave.mostrarError(null)
        if (codigo.length < 6) {
            edtCodigo.mostrarError("Mínimo 6 caracteres")
            return
        }
        if (!clave.matches(Regex("\\d{4,6}"))) {
            edtClave.mostrarError("PIN de 4 a 6 dígitos")
            return
        }
        if (!tienePermisoRedLocal()) {
            pedirPermisoRed.launch(PERMISO_RED_LOCAL)
            return
        }
        logica = EstanqueLogica(clave, SystemClock.elapsedRealtime())
        edtCodigo.isEnabled = false
        edtClave.isEnabled = false
        btnIniciar.text = "Detener equipo"
        habilitarSimulacion(true)
        tvConexion.text = "Preparando canal cifrado..."
        registrar(listOf("EQUIPO INICIADO por ${Sesion.usuario}"))
        thread(name = "servidor-aquaalerta") { ejecutarServidor(Seguridad.derivarLlaveCanal(codigo)) }
        manejador.post(ciclo)
    }

    private fun detenerEquipo() {
        registrar(listOf("EQUIPO DETENIDO por ${Sesion.usuario}"))
        logica = null
        manejador.removeCallbacks(ciclo)
        cerrarRed()
        edtCodigo.isEnabled = true
        edtClave.isEnabled = true
        btnIniciar.text = "Iniciar equipo"
        habilitarSimulacion(false)
        tvConexion.text = "Equipo detenido"
        tvEstado.text = "DETENIDO"
        cardEstado.setCardBackgroundColor(colorDeEstado(this, "DETENIDO"))
    }

    private fun habilitarSimulacion(activa: Boolean) {
        seekNivel.isEnabled = activa
        btnFisico.isEnabled = activa
    }

    /** Ciclo del equipo cada 250 ms (como el loop() del Arduino). */
    private val ciclo = object : Runnable {
        override fun run() {
            val l = logica ?: return
            val ruido = Random.nextInt(-4, 5)   // pequeno ruido electrico del sensor
            registrar(l.procesarLectura(seekNivel.progress + ruido))
            ciclos++
            if (ciclos % 4 == 0) {                // 1 vez por segundo
                enviar(l.telemetria(SystemClock.elapsedRealtime()).aTrama())
                if (l.alarmaSonando) alarma.pulso()
            }
            actualizarPantalla()
            manejador.postDelayed(this, 250)
        }
    }

    // ---------------- Red: servidor TCP ----------------

    private fun ejecutarServidor(llave: SecretKeySpec) {
        try {
            val ss = ServerSocket()
            ss.reuseAddress = true
            ss.bind(InetSocketAddress(PUERTO_EQUIPO))
            servidor = ss
            runOnUiThread { tvConexion.text = "Esperando monitor en el puerto $PUERTO_EQUIPO..." }
            while (logica != null) {
                val socket = ss.accept()
                // Se atiende un monitor a la vez (un solo puesto de control)
                atenderMonitor(CanalSeguro(socket, llave), socket.inetAddress.hostAddress ?: "?")
            }
        } catch (e: IOException) {
            if (logica != null) runOnUiThread { tvConexion.text = "Servidor detenido: ${e.message}" }
        }
    }

    private fun atenderMonitor(c: CanalSeguro, ip: String) {
        try {
            // El primer mensaje debe ser el saludo; si el codigo de enlace no coincide, falla el descifrado
            val saludo = c.recibir()
            if (saludo == null || !saludo.startsWith("HOLA;")) throw SecurityException("saludo inválido")
            val usuarioRemoto = saludo.substringAfter("usuario=").substringBefore(';').take(20)
            canal = c
            runOnUiThread {
                tvConexion.text = "Monitor conectado: $usuarioRemoto desde $ip"
                registrar(listOf("MONITOR CONECTADO usuario=$usuarioRemoto ip=$ip"))
            }
            while (true) {
                val mensaje = c.recibir() ?: break
                runOnUiThread { procesarComando(mensaje) }
            }
        } catch (e: GeneralSecurityException) {
            runOnUiThread { registrar(listOf("CONEXION RECHAZADA ip=$ip: código de enlace incorrecto o mensaje alterado")) }
        } catch (e: SecurityException) {
            runOnUiThread { registrar(listOf("CONEXION RECHAZADA ip=$ip: ${e.message}")) }
        } catch (e: IOException) {
            // desconexion normal
        } catch (e: Exception) {
            runOnUiThread { registrar(listOf("CONEXION RECHAZADA ip=$ip: mensaje inválido")) }
        } finally {
            val estabaConectado = canal === c
            c.close()
            if (estabaConectado) canal = null
            runOnUiThread {
                if (logica != null) {
                    tvConexion.text = "Esperando monitor en el puerto $PUERTO_EQUIPO..."
                    if (estabaConectado) registrar(listOf("MONITOR DESCONECTADO ip=$ip"))
                }
            }
        }
    }

    private fun procesarComando(mensaje: String) {
        val l = logica ?: return
        val respuesta = l.procesarComando(mensaje, SystemClock.elapsedRealtime())
        enviar(respuesta.texto)
        if (respuesta.texto.startsWith("ERR;CLAVE") || respuesta.texto.startsWith("ERR;COMANDOS BLOQUEADOS")) {
            registrar(listOf("COMANDO RECHAZADO: ${respuesta.texto.removePrefix("ERR;")}"))
        }
        registrar(respuesta.eventos)
        actualizarPantalla()
    }

    private fun enviar(mensaje: String) {
        val c = canal ?: return
        if (envios.isShutdown) return
        envios.execute {
            try {
                c.enviar(mensaje)
            } catch (e: Exception) {
                c.close()
            }
        }
    }

    private fun cerrarRed() {
        val s = servidor
        val c = canal
        servidor = null
        canal = null
        thread {
            try { s?.close() } catch (_: IOException) { }
            c?.close()
        }
    }

    // ---------------- Bitacora y pantalla ----------------

    /** Guarda cada evento en SQLite, lo muestra y lo envia al monitor (EVT;t=..;descripcion). */
    private fun registrar(eventos: List<String>) {
        if (eventos.isEmpty()) return
        val segundos = logica?.telemetria(SystemClock.elapsedRealtime())?.segundos ?: 0
        for (evento in eventos) {
            bitacora.addFirst("${hora()}  $evento")
            if (bitacora.size > 30) bitacora.removeLast()
            db.guardarEvento(evento, "ESTANQUE", Sesion.usuario ?: "-")
            enviar("EVT;t=$segundos;$evento")
        }
        tvBitacora.text = bitacora.joinToString("\n")
    }

    private fun actualizarPantalla() {
        val l = logica ?: return
        tvAdc.text = "Lectura del sensor (ADC): ${l.ultimaLectura} / 1023"
        barNivel.progress = l.ultimaLectura
        tvEstado.text = l.estado.name
        cardEstado.setCardBackgroundColor(colorDeEstado(this, l.estado.name))
        tvValvula.text = "Válvula de llenado: " + if (l.valvulaAbierta) "ABIERTA (llenando)" else "CERRADA"
        tvAlarma.text = when {
            l.estado == Estado.PARO -> "Sistema detenido. Mantenga el botón físico para reanudar."
            l.alarmaSonando -> "¡ALARMA DE REBALSE ACTIVA!"
            l.estado == Estado.REBALSE -> "Alarma silenciada (el estado sigue en rebalse)"
            else -> "Sin alarmas"
        }
    }

    override fun onDestroy() {
        logica = null
        manejador.removeCallbacksAndMessages(null)
        cerrarRed()
        envios.shutdown()
        if (::alarma.isInitialized) alarma.liberar()
        super.onDestroy()
    }
}
