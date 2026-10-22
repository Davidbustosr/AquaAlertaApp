package cl.inacap.aquaalerta

import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Socket
import javax.crypto.spec.SecretKeySpec

/**
 * Conexion TCP por Wi-Fi entre los dos telefonos. Cada linea viaja cifrada y autenticada (AES-GCM).
 * Dentro del mensaje cifrado va un numero de secuencia: si alguien reenvia un mensaje capturado
 * (ataque de repeticion) se rechaza porque su secuencia no es mayor que la ultima recibida.
 */
class CanalSeguro(private val socket: Socket, private val llave: SecretKeySpec) : Closeable {

    companion object {
        private const val LARGO_MAX_LINEA = 512   // validacion de entrada: lineas mas largas se rechazan
    }

    private val lector = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
    private val escritor = PrintWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8), true)
    private var secuenciaEnvio = 0L
    private var ultimaSecuenciaRecibida = 0L

    @Synchronized
    fun enviar(mensaje: String) {
        secuenciaEnvio++
        escritor.println(Seguridad.cifrar(llave, "$secuenciaEnvio|$mensaje"))
        if (escritor.checkError()) throw IOException("Conexion cerrada")
    }

    /** Bloquea hasta recibir un mensaje. Devuelve null si el otro extremo cerro la conexion. */
    fun recibir(): String? {
        val linea = lector.readLine() ?: return null
        if (linea.length > LARGO_MAX_LINEA) throw SecurityException("Mensaje demasiado largo")
        val texto = Seguridad.descifrar(llave, linea)
        val separador = texto.indexOf('|')
        if (separador <= 0) throw SecurityException("Formato de mensaje invalido")
        val secuencia = texto.substring(0, separador).toLongOrNull()
            ?: throw SecurityException("Secuencia invalida")
        if (secuencia <= ultimaSecuenciaRecibida) throw SecurityException("Mensaje repetido (posible replay)")
        ultimaSecuenciaRecibida = secuencia
        return texto.substring(separador + 1)
    }

    override fun close() {
        try {
            socket.close()
        } catch (_: IOException) {
        }
    }
}
