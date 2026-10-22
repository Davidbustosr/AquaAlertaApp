package cl.inacap.aquaalerta

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Funciones criptograficas de la app (ISO/IEC 27400: proteccion de credenciales y datos en transito).
 *  - Contrasenas: PBKDF2-HMAC-SHA256 con sal aleatoria. Nunca se guarda la contrasena en texto plano.
 *  - Canal entre telefonos: AES-256-GCM (cifra y autentica cada mensaje) con una llave derivada
 *    del "codigo de enlace" que ambos equipos conocen.
 */
object Seguridad {
    private const val ITERACIONES = 20_000
    private const val BITS_LLAVE = 256
    private const val LARGO_IV = 12          // bytes, recomendado para GCM
    private const val BITS_ETIQUETA = 128    // etiqueta de autenticacion GCM
    private val SAL_CANAL = "AquaAlerta-canal-v1".toByteArray()
    private val aleatorio = SecureRandom()

    fun generarSal(): String {
        val sal = ByteArray(16)
        aleatorio.nextBytes(sal)
        return b64(sal)
    }

    fun hashContrasena(contrasena: String, salB64: String): String =
        b64(pbkdf2(contrasena, Base64.getDecoder().decode(salB64)))

    /** Compara en tiempo constante para no filtrar informacion por el tiempo de respuesta. */
    fun verificarContrasena(contrasena: String, salB64: String, hashGuardado: String): Boolean {
        val calculado = Base64.getDecoder().decode(hashContrasena(contrasena, salB64))
        return MessageDigest.isEqual(calculado, Base64.getDecoder().decode(hashGuardado))
    }

    /** Politica minima: 8 caracteres, al menos una letra y un numero. */
    fun contrasenaSegura(contrasena: String): Boolean =
        contrasena.length >= 8 && contrasena.any { it.isLetter() } && contrasena.any { it.isDigit() }

    fun derivarLlaveCanal(codigoEnlace: String): SecretKeySpec =
        SecretKeySpec(pbkdf2(codigoEnlace, SAL_CANAL), "AES")

    /** Devuelve Base64(IV + texto cifrado + etiqueta). Cada mensaje usa un IV aleatorio nuevo. */
    fun cifrar(llave: SecretKeySpec, texto: String): String {
        val iv = ByteArray(LARGO_IV)
        aleatorio.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, llave, GCMParameterSpec(BITS_ETIQUETA, iv))
        return b64(iv + cipher.doFinal(texto.toByteArray(Charsets.UTF_8)))
    }

    /** Lanza AEADBadTagException si el mensaje fue alterado o se cifro con otro codigo de enlace. */
    fun descifrar(llave: SecretKeySpec, mensajeB64: String): String {
        val datos = Base64.getDecoder().decode(mensajeB64)
        require(datos.size > LARGO_IV + BITS_ETIQUETA / 8) { "Mensaje demasiado corto" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, llave, GCMParameterSpec(BITS_ETIQUETA, datos, 0, LARGO_IV))
        return String(cipher.doFinal(datos, LARGO_IV, datos.size - LARGO_IV), Charsets.UTF_8)
    }

    private fun pbkdf2(secreto: String, sal: ByteArray): ByteArray {
        val spec = PBEKeySpec(secreto.toCharArray(), sal, ITERACIONES, BITS_LLAVE)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
}
