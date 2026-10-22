package cl.inacap.aquaalerta

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.GeneralSecurityException
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * MODO MONITOR: se conecta por Wi-Fi al telefono en Modo Estanque.
 *  - Muestra el nivel y el estado en tiempo real y guarda cada lectura en SQLite.
 *  - Envia comandos: consulta (?), silenciar (S<PIN>) y paro (P<PIN>). Los dos ultimos solo con rol ADMIN.
 *  - Si no llegan datos en 5 s considera el enlace perdido (supervision de la comunicacion).
 */
class MonitorActivity : AppCompatActivity() {

    companion object {
        private const val TIEMPO_CONEXION_MS = 5000
        private const val TIEMPO_SIN_DATOS_MS = 5000
    }

    private lateinit var db: BaseDatos
    private lateinit var alarma: Alarma
    private val envios = Executors.newSingleThreadExecutor()
    private val bitacora = ArrayDeque<String>()
    private var ipEquipo = ""

    @Volatile private var canal: CanalSeguro? = null
    @Volatile private var conectando = false
    @Volatile private var cierreManual = false

    private lateinit var edtIp: EditText
    private lateinit var edtPuerto: EditText
    private lateinit var edtCodigo: EditText
    private lateinit var btnConectar: Button
    private lateinit var tvConexion: TextView
    private lateinit var cardEstado: MaterialCardView
    private lateinit var tvEstado: TextView
    private lateinit var barNivel: ProgressBar
    private lateinit var tvDetalle: TextView
    private lateinit var tvAlarma: TextView
    private lateinit var tvActualizado: TextView
    private lateinit var btnConsultar: Button
    private lateinit var btnSilenciar: Button
    private lateinit var btnParo: Button
    private lateinit var tvBitacora: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (exigirSesion()) return
        enableEdgeToEdge()
        setContentView(R.layout.activity_monitor)
        ajustarBordes(findViewById(R.id.main))

        db = BaseDatos.obtener(this)
        alarma = Alarma(this)
        edtIp = findViewById(R.id.edtIp)
        edtPuerto = findViewById(R.id.edtPuerto)
        edtCodigo = findViewById(R.id.edtCodigo)
        btnConectar = findViewById(R.id.btnConectar)
        tvConexion = findViewById(R.id.tvConexion)
        cardEstado = findViewById(R.id.cardEstado)
        tvEstado = findViewById(R.id.tvEstado)
        barNivel = findViewById(R.id.barNivel)
        tvDetalle = findViewById(R.id.tvDetalle)
        tvAlarma = findViewById(R.id.tvAlarma)
        tvActualizado = findViewById(R.id.tvActualizado)
        btnConsultar = findViewById(R.id.btnConsultar)
        btnSilenciar = findViewById(R.id.btnSilenciar)
        btnParo = findViewById(R.id.btnParo)
        tvBitacora = findViewById(R.id.tvBitacora)

        findViewById<TextView>(R.id.tvUsuario).text = "Usuario: ${Sesion.usuario} · Rol: ${Sesion.rol}"
        findViewById<TextView>(R.id.tvPermisos).text = if (Sesion.esAdmin)
            "Rol ADMIN: puede enviar comandos. Cada comando crítico exige el PIN del equipo."
        else "Rol OPERADOR: solo monitoreo. Los comandos críticos requieren rol ADMIN."
        activarComandos(false)

        btnConectar.setOnClickListener {
            when {
                canal != null -> desconectar()
                !conectando -> conectar()
            }
        }
        btnConsultar.setOnClickListener { enviarComando("?", "CONSULTA DE ESTADO") }
        btnSilenciar.setOnClickListener {
            pedirPin("Silenciar alarma") { pin -> enviarComando("S$pin", "SILENCIAR ALARMA") }
        }
        btnParo.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("¿Paro de emergencia?")
                .setMessage("La válvula quedará cerrada. Solo se puede reanudar con el botón físico del equipo.")
                .setPositiveButton("Continuar") { _, _ ->
                    pedirPin("Paro de emergencia") { pin -> enviarComando("P$pin", "PARO DE EMERGENCIA") }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    // ---------------- Conexion (cliente TCP) ----------------

    private fun conectar() {
        val ip = edtIp.text.toString().trim()
        val puerto = edtPuerto.text.toString().toIntOrNull()
        val codigo = edtCodigo.text.toString()
        if (!Patterns.IP_ADDRESS.matcher(ip).matches()) {
            edtIp.error = "IP inválida"
            return
        }
        if (puerto == null || puerto !in 1024..65535) {
            edtPuerto.error = "1024-65535"
            return
        }
        if (codigo.length < 6) {
            edtCodigo.error = "Mínimo 6 caracteres"
            return
        }

        conectando = true
        cierreManual = false
        btnConectar.isEnabled = false
        tvConexion.text = "Conectando a $ip:$puerto..."

        thread(name = "monitor-aquaalerta") {
            var motivo = "Desconectado"
            try {
                val llave = Seguridad.derivarLlaveCanal(codigo)
                val socket = Socket()
                socket.connect(InetSocketAddress(ip, puerto), TIEMPO_CONEXION_MS)
                socket.soTimeout = TIEMPO_SIN_DATOS_MS
                val c = CanalSeguro(socket, llave)
                canal = c
                c.enviar("HOLA;usuario=${Sesion.usuario};rol=${Sesion.rol}")
                runOnUiThread { alConectar(ip) }
                while (true) {
                    val mensaje = c.recibir()
                    if (mensaje == null) {
                        motivo = "El equipo cerró la conexión (¿código de enlace distinto?)"
                        break
                    }
                    manejarMensaje(mensaje)
                }
            } catch (e: SocketTimeoutException) {
                motivo = "Sin datos del equipo por ${TIEMPO_SIN_DATOS_MS / 1000} s: enlace perdido"
            } catch (e: GeneralSecurityException) {
                motivo = "Mensaje no autenticado: código de enlace incorrecto"
            } catch (e: SecurityException) {
                motivo = "Mensaje rechazado: ${e.message}"
            } catch (e: IOException) {
                motivo = "No se pudo conectar: ${e.message}"
            } catch (e: Exception) {
                motivo = "Error de comunicación: ${e.message}"
            } finally {
                canal?.close()
                canal = null
                conectando = false
                val texto = if (cierreManual) "Desconectado por el usuario" else motivo
                runOnUiThread { alDesconectar(texto) }
            }
        }
    }

    private fun desconectar() {
        cierreManual = true
        val c = canal
        thread { c?.close() }
    }

    /** Se ejecuta en el hilo de red: guarda en SQLite y actualiza la pantalla en el hilo principal. */
    private fun manejarMensaje(mensaje: String) {
        when {
            mensaje.startsWith("TLM;") -> {
                val t = Telemetria.desdeTrama(mensaje) ?: return
                db.guardarLectura(t, ipEquipo)
                runOnUiThread { mostrarTelemetria(t) }
            }
            mensaje.startsWith("EVT;") -> {
                val descripcion = mensaje.substringAfter(';').substringAfter(';').take(120)
                db.guardarEvento(descripcion, "EQUIPO $ipEquipo", Sesion.usuario ?: "-")
                runOnUiThread { agregarBitacora("Equipo: $descripcion") }
            }
            mensaje.startsWith("OK") -> runOnUiThread {
                agregarBitacora("Respuesta: $mensaje")
                aviso("Comando aceptado por el equipo")
            }
            mensaje.startsWith("ERR;") -> runOnUiThread {
                val error = mensaje.removePrefix("ERR;")
                agregarBitacora("Rechazado: $error")
                aviso(error)
            }
        }
    }

    private fun enviarComando(comando: String, descripcion: String) {
        val c = canal ?: return aviso("No hay conexión con el equipo")
        envios.execute {
            try {
                c.enviar(comando)
            } catch (e: Exception) {
                c.close()
            }
        }
        // Se registra la accion para auditoria, NUNCA el PIN
        agregarBitacora("Enviado: $descripcion")
        db.guardarEvento("COMANDO $descripcion ENVIADO", "MONITOR", Sesion.usuario ?: "-")
    }

    private fun pedirPin(titulo: String, accion: (String) -> Unit) {
        val campo = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "PIN del equipo"
            filters = arrayOf(InputFilter.LengthFilter(6))
        }
        val margen = (24 * resources.displayMetrics.density).toInt()
        val contenedor = FrameLayout(this).apply {
            setPadding(margen, margen / 2, margen, 0)
            addView(campo)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(titulo)
            .setMessage("Ingrese el PIN del equipo para autorizar el comando.")
            .setView(contenedor)
            .setPositiveButton("Enviar") { _, _ ->
                val pin = campo.text.toString()
                if (pin.matches(Regex("\\d{4,6}"))) accion(pin) else aviso("El PIN debe tener 4 a 6 dígitos")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ---------------- Pantalla ----------------

    private fun alConectar(ip: String) {
        ipEquipo = ip
        btnConectar.isEnabled = true
        btnConectar.text = "Desconectar"
        tvConexion.text = "Conectado a $ip · canal cifrado AES-256-GCM"
        edtIp.isEnabled = false
        edtPuerto.isEnabled = false
        edtCodigo.isEnabled = false
        activarComandos(true)
        agregarBitacora("Conectado a $ip")
        db.guardarEvento("CONEXION AL EQUIPO $ip", "MONITOR", Sesion.usuario ?: "-")
    }

    private fun alDesconectar(motivo: String) {
        btnConectar.isEnabled = true
        btnConectar.text = "Conectar"
        tvConexion.text = motivo
        edtIp.isEnabled = true
        edtPuerto.isEnabled = true
        edtCodigo.isEnabled = true
        activarComandos(false)
        tvEstado.text = "SIN DATOS"
        cardEstado.setCardBackgroundColor(colorDeEstado(this, "SIN DATOS"))
        agregarBitacora(motivo)
    }

    private fun activarComandos(activos: Boolean) {
        btnConsultar.isEnabled = activos
        btnSilenciar.isEnabled = activos && Sesion.esAdmin
        btnParo.isEnabled = activos && Sesion.esAdmin
    }

    private fun mostrarTelemetria(t: Telemetria) {
        tvEstado.text = t.estado
        cardEstado.setCardBackgroundColor(colorDeEstado(this, t.estado))
        barNivel.progress = t.adc
        tvDetalle.text = "Nivel (ADC): ${t.adc} / 1023\nVálvula: ${if (t.valvulaAbierta) "ABIERTA" else "CERRADA"}"
        tvAlarma.text = when {
            t.estado == "REBALSE" && !t.silenciada -> "¡ALARMA DE REBALSE!"
            t.estado == "REBALSE" -> "Alarma silenciada"
            t.estado == "PARO" -> "Equipo en PARO (reanudar en el equipo)"
            else -> "Sin alarmas"
        }
        tvActualizado.text = "Última trama: ${hora()} · equipo encendido hace ${t.segundos} s"
        if (t.estado == "REBALSE" && !t.silenciada) alarma.pulso()
    }

    private fun agregarBitacora(texto: String) {
        bitacora.addFirst("${hora()}  $texto")
        if (bitacora.size > 30) bitacora.removeLast()
        tvBitacora.text = bitacora.joinToString("\n")
    }

    override fun onDestroy() {
        cierreManual = true
        val c = canal
        thread { c?.close() }
        envios.shutdown()
        if (::alarma.isInitialized) alarma.liberar()
        super.onDestroy()
    }
}
