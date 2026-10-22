package cl.inacap.aquaalerta

import java.security.MessageDigest

enum class Estado { NORMAL, ALTO, REBALSE, PARO }

/**
 * Logica del equipo AquaAlerta, traducida del sketch de Arduino de la Unidad 1 (AquaAlerta v2.2).
 * No depende de Android, por eso se prueba con pruebas unitarias en el PC.
 */
class EstanqueLogica(private val claveEquipo: String, private val inicioMs: Long) {

    companion object {
        const val UMBRAL_ALTO = 350        // el agua toca el sensor: estanque lleno
        const val UMBRAL_REBALSE = 700     // el agua sigue subiendo con la valvula cerrada
        const val HISTERESIS = 30          // evita oscilar cuando la lectura queda justo en el umbral
        const val CONFIRMACIONES = 3       // lecturas iguales seguidas para aceptar un cambio (olas)
        const val MAX_INTENTOS = 3         // claves erroneas antes de bloquear los comandos
        const val BLOQUEO_MS = 60_000L
        const val LARGO_MAX_CMD = 12
    }

    data class Respuesta(val texto: String, val eventos: List<String> = emptyList())

    var estado = Estado.NORMAL
        private set
    var valvulaAbierta = true
        private set
    var alarmaSilenciada = false
        private set
    var ultimaLectura = 0
        private set

    private var candidato = Estado.NORMAL
    private var conteoCandidato = 0
    private var intentosFallidos = 0
    private var bloqueadoHastaMs = 0L

    val alarmaSonando: Boolean
        get() = estado == Estado.REBALSE && !alarmaSilenciada

    fun clasificar(adc: Int): Estado = when {
        adc >= UMBRAL_REBALSE -> Estado.REBALSE
        estado == Estado.REBALSE && adc >= UMBRAL_REBALSE - HISTERESIS -> Estado.REBALSE
        adc >= UMBRAL_ALTO -> Estado.ALTO
        (estado == Estado.ALTO || estado == Estado.REBALSE) && adc >= UMBRAL_ALTO - HISTERESIS -> Estado.ALTO
        else -> Estado.NORMAL
    }

    /** Procesa una lectura del sensor (0..1023). Devuelve los eventos generados. */
    fun procesarLectura(adc: Int): List<String> {
        ultimaLectura = adc.coerceIn(0, 1023)
        if (estado == Estado.PARO) return emptyList()
        val nuevo = clasificar(ultimaLectura)
        if (nuevo == estado) {
            conteoCandidato = 0
            return emptyList()
        }
        if (nuevo == candidato) conteoCandidato++ else {
            candidato = nuevo
            conteoCandidato = 1
        }
        return if (conteoCandidato >= CONFIRMACIONES) cambiarEstado(nuevo) else emptyList()
    }

    fun silenciar(origen: String): List<String> {
        if (estado != Estado.REBALSE || alarmaSilenciada) return emptyList()
        alarmaSilenciada = true
        return listOf("ALARMA SILENCIADA POR $origen")
    }

    /** PARO: la valvula queda cerrada (estado seguro, como en la industria OT). */
    fun detener(origen: String): List<String> {
        if (estado == Estado.PARO) return emptyList()
        return listOf("PARO SOLICITADO POR $origen") + cambiarEstado(Estado.PARO)
    }

    /** Reanudar solo se permite desde el boton fisico del equipo, nunca a distancia. */
    fun reanudar(origen: String): List<String> {
        if (estado != Estado.PARO) return emptyList()
        return listOf("REANUDADO POR $origen") + cambiarEstado(clasificar(ultimaLectura))
    }

    fun telemetria(ahoraMs: Long): Telemetria =
        Telemetria((ahoraMs - inicioMs) / 1000, ultimaLectura, estado.name, valvulaAbierta, alarmaSilenciada)

    /** Comandos remotos: ? (consulta), S<clave> (silenciar), P<clave> (paro). */
    fun procesarComando(comandoRecibido: String, ahoraMs: Long): Respuesta {
        val cmd = comandoRecibido.trim().uppercase()
        if (cmd.isEmpty() || cmd.length > LARGO_MAX_CMD) return Respuesta("ERR;COMANDO NO VALIDO")
        when (cmd[0]) {
            '?' -> return Respuesta(telemetria(ahoraMs).aTrama())
            'R' -> return Respuesta("ERR;REANUDAR SOLO CON EL BOTON FISICO")
            'S', 'P' -> Unit
            else -> return Respuesta("ERR;COMANDO NO VALIDO (use ?, S<clave> o P<clave>)")
        }
        if (ahoraMs < bloqueadoHastaMs) return Respuesta("ERR;COMANDOS BLOQUEADOS TEMPORALMENTE")
        val claveRecibida = cmd.substring(1).toByteArray()
        if (!MessageDigest.isEqual(claveRecibida, claveEquipo.toByteArray())) {
            intentosFallidos++
            if (intentosFallidos >= MAX_INTENTOS) {
                intentosFallidos = 0
                bloqueadoHastaMs = ahoraMs + BLOQUEO_MS
                return Respuesta(
                    "ERR;CLAVE INCORRECTA $MAX_INTENTOS/$MAX_INTENTOS",
                    listOf("COMANDOS BLOQUEADOS POR ${BLOQUEO_MS / 1000} s")
                )
            }
            return Respuesta("ERR;CLAVE INCORRECTA $intentosFallidos/$MAX_INTENTOS")
        }
        intentosFallidos = 0
        val eventos = if (cmd[0] == 'S') silenciar("REMOTO") else detener("REMOTO")
        return Respuesta(if (eventos.isEmpty()) "OK;SIN CAMBIOS" else "OK", eventos)
    }

    private fun cambiarEstado(nuevo: Estado): List<String> {
        val eventos = mutableListOf("ESTADO ${estado.name}->${nuevo.name}")
        estado = nuevo
        conteoCandidato = 0
        if (nuevo != Estado.REBALSE) alarmaSilenciada = false
        val abrir = nuevo == Estado.NORMAL
        if (abrir != valvulaAbierta) {
            valvulaAbierta = abrir
            eventos += if (abrir) "VALVULA ABIERTA" else "VALVULA CERRADA"
        }
        return eventos
    }
}
