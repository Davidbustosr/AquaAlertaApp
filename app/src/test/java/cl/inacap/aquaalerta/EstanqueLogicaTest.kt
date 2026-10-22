package cl.inacap.aquaalerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas de la logica del equipo (mismo comportamiento que el Arduino de la Unidad 1). */
class EstanqueLogicaTest {

    private fun leer(l: EstanqueLogica, adc: Int, veces: Int) = repeat(veces) { l.procesarLectura(adc) }

    @Test
    fun cambioDeEstadoRequiereTresLecturasSeguidas() {
        val l = EstanqueLogica("4827", 0)
        leer(l, 500, 2)
        assertEquals(Estado.NORMAL, l.estado)       // 2 lecturas: aun no confirma (olas)
        l.procesarLectura(500)
        assertEquals(Estado.ALTO, l.estado)         // 3a lectura: cambia
        assertFalse(l.valvulaAbierta)               // estanque lleno: valvula cerrada
    }

    @Test
    fun rebalseActivaAlarmaYSePuedeSilenciar() {
        val l = EstanqueLogica("4827", 0)
        leer(l, 800, 3)
        assertEquals(Estado.REBALSE, l.estado)
        assertTrue(l.alarmaSonando)
        l.silenciar("BOTON")
        assertFalse(l.alarmaSonando)
        assertEquals(Estado.REBALSE, l.estado)      // silenciar no cambia el estado
    }

    @Test
    fun histeresisEvitaOscilarEnElUmbral() {
        val l = EstanqueLogica("4827", 0)
        leer(l, 360, 3)
        assertEquals(Estado.ALTO, l.estado)
        leer(l, 340, 5)                             // bajo 350 pero sobre 320: sigue ALTO
        assertEquals(Estado.ALTO, l.estado)
        leer(l, 300, 3)
        assertEquals(Estado.NORMAL, l.estado)
        assertTrue(l.valvulaAbierta)
    }

    @Test
    fun comandoConClaveIncorrectaSeRechazaYBloqueaAlTercerIntento() {
        val l = EstanqueLogica("4827", 0)
        assertEquals("ERR;CLAVE INCORRECTA 1/3", l.procesarComando("P1111", 1000).texto)
        assertEquals("ERR;CLAVE INCORRECTA 2/3", l.procesarComando("P2222", 2000).texto)
        val tercero = l.procesarComando("P3333", 3000)
        assertTrue(tercero.eventos.first().startsWith("COMANDOS BLOQUEADOS"))
        // Bloqueado: ni siquiera la clave correcta funciona durante 60 s
        assertEquals("ERR;COMANDOS BLOQUEADOS TEMPORALMENTE", l.procesarComando("P4827", 4000).texto)
        assertEquals(Estado.NORMAL, l.estado)
        // Pasado el bloqueo, la clave correcta si funciona
        assertEquals("OK", l.procesarComando("P4827", 3000 + EstanqueLogica.BLOQUEO_MS).texto)
        assertEquals(Estado.PARO, l.estado)
    }

    @Test
    fun paroCierraValvulaYNoSePuedeReanudarADistancia() {
        val l = EstanqueLogica("4827", 0)
        assertTrue(l.valvulaAbierta)
        l.procesarComando("P4827", 0)
        assertEquals(Estado.PARO, l.estado)
        assertFalse(l.valvulaAbierta)
        assertEquals("ERR;REANUDAR SOLO CON EL BOTON FISICO", l.procesarComando("R", 0).texto)
        l.reanudar("BOTON")
        assertEquals(Estado.NORMAL, l.estado)
        assertTrue(l.valvulaAbierta)
    }

    @Test
    fun comandosInvalidosOMuyLargosSeRechazan() {
        val l = EstanqueLogica("4827", 0)
        assertTrue(l.procesarComando("X", 0).texto.startsWith("ERR;COMANDO NO VALIDO"))
        assertTrue(l.procesarComando("P" + "1".repeat(50), 0).texto.startsWith("ERR;COMANDO NO VALIDO"))
    }

    @Test
    fun tramaDeTelemetriaIdaYVuelta() {
        val l = EstanqueLogica("4827", 0)
        leer(l, 512, 3)
        val trama = l.telemetria(42_000).aTrama()
        assertEquals("TLM;t=42;ADC=512;ESTADO=ALTO;VALVULA=CERRADA;SIL=0", trama)
        val t = Telemetria.desdeTrama(trama)
        assertNotNull(t)
        assertEquals(512, t!!.adc)
        assertEquals("ALTO", t.estado)
    }

    @Test
    fun tramasManipuladasSeDescartan() {
        assertNull(Telemetria.desdeTrama("TLM;t=1;ADC=5000;ESTADO=ALTO;VALVULA=CERRADA;SIL=0"))
        assertNull(Telemetria.desdeTrama("TLM;t=1;ADC=10;ESTADO=HACKEADO;VALVULA=CERRADA;SIL=0"))
        assertNull(Telemetria.desdeTrama("hola"))
    }
}
