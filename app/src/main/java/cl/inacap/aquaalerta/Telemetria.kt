package cl.inacap.aquaalerta

/**
 * Trama de telemetria. Usa EXACTAMENTE el mismo formato que el Arduino de la Unidad 1:
 *   TLM;t=42;ADC=312;ESTADO=ALTO;VALVULA=CERRADA;SIL=0
 * Asi el Monitor podria leer tambien al Arduino real si se le agrega un modulo Wi-Fi/Bluetooth.
 */
data class Telemetria(
    val segundos: Long,
    val adc: Int,
    val estado: String,
    val valvulaAbierta: Boolean,
    val silenciada: Boolean
) {
    fun aTrama(): String =
        "TLM;t=$segundos;ADC=$adc;ESTADO=$estado;VALVULA=${if (valvulaAbierta) "ABIERTA" else "CERRADA"};SIL=${if (silenciada) 1 else 0}"

    companion object {
        private val ESTADOS_VALIDOS = Estado.values().map { it.name }.toSet()

        /** Devuelve null si la trama no es valida (validacion de datos recibidos). */
        fun desdeTrama(trama: String): Telemetria? {
            if (!trama.startsWith("TLM;")) return null
            val campos = trama.split(';').drop(1).mapNotNull {
                val partes = it.split('=', limit = 2)
                if (partes.size == 2) partes[0] to partes[1] else null
            }.toMap()
            val segundos = campos["t"]?.toLongOrNull() ?: return null
            val adc = campos["ADC"]?.toIntOrNull()?.takeIf { it in 0..1023 } ?: return null
            val estado = campos["ESTADO"]?.takeIf { it in ESTADOS_VALIDOS } ?: return null
            val valvula = campos["VALVULA"]?.takeIf { it == "ABIERTA" || it == "CERRADA" } ?: return null
            return Telemetria(segundos, adc, estado, valvula == "ABIERTA", campos["SIL"] == "1")
        }
    }
}
