package cl.inacap.aquaalerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import javax.crypto.AEADBadTagException
import kotlin.concurrent.thread

class SeguridadTest {

    @Test
    fun contrasenaSeGuardaComoHashConSal() {
        val sal = Seguridad.generarSal()
        val hash = Seguridad.hashContrasena("Agua2026", sal)
        assertNotEquals("Agua2026", hash)
        assertTrue(Seguridad.verificarContrasena("Agua2026", sal, hash))
        assertFalse(Seguridad.verificarContrasena("agua2026", sal, hash))
        // Misma contrasena con otra sal produce otro hash
        assertNotEquals(hash, Seguridad.hashContrasena("Agua2026", Seguridad.generarSal()))
    }

    @Test
    fun politicaDeContrasena() {
        assertFalse(Seguridad.contrasenaSegura("1234"))
        assertFalse(Seguridad.contrasenaSegura("solotexto"))
        assertTrue(Seguridad.contrasenaSegura("Agua2026"))
    }

    @Test
    fun cifradoAesGcmIdaYVuelta() {
        val llave = Seguridad.derivarLlaveCanal("enlace123")
        val cifrado = Seguridad.cifrar(llave, "P4827")
        assertFalse(cifrado.contains("4827"))                 // el PIN no viaja en texto plano
        assertEquals("P4827", Seguridad.descifrar(llave, cifrado))
    }

    @Test(expected = AEADBadTagException::class)
    fun codigoDeEnlaceDistintoNoPuedeDescifrar() {
        val cifrado = Seguridad.cifrar(Seguridad.derivarLlaveCanal("enlace123"), "P4827")
        Seguridad.descifrar(Seguridad.derivarLlaveCanal("otroCodigo"), cifrado)
    }

    @Test
    fun canalSeguroEntreDosSocketsReales() {
        val llave = Seguridad.derivarLlaveCanal("enlace123")
        ServerSocket(0).use { servidor ->
            var recibido: String? = null
            val hilo = thread {
                CanalSeguro(servidor.accept(), llave).use { equipo ->
                    recibido = equipo.recibir()
                    equipo.enviar("OK")
                }
            }
            CanalSeguro(Socket("127.0.0.1", servidor.localPort), llave).use { monitor ->
                monitor.enviar("S4827")
                assertEquals("OK", monitor.recibir())
            }
            hilo.join(5000)
            assertEquals("S4827", recibido)
        }
    }
}
