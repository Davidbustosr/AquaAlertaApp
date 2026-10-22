package cl.inacap.aquaalerta

/**
 * Usuario conectado y control de intentos de login.
 * Tras 3 contrasenas erroneas el login se bloquea 30 s (defensa contra fuerza bruta).
 * El tiempo se mide con el reloj de arranque del equipo, asi no se evita cambiando la hora.
 */
object Sesion {
    const val ROL_ADMIN = "ADMIN"
    const val ROL_OPERADOR = "OPERADOR"
    private const val MAX_INTENTOS = 3
    private const val BLOQUEO_MS = 30_000L

    var usuario: String? = null
        private set
    var rol: String? = null
        private set
    val esAdmin: Boolean
        get() = rol == ROL_ADMIN

    private var intentosFallidos = 0
    private var bloqueadoHastaMs = 0L

    fun iniciar(usuario: String, rol: String) {
        this.usuario = usuario
        this.rol = rol
        intentosFallidos = 0
    }

    fun cerrar() {
        usuario = null
        rol = null
    }

    fun segundosDeBloqueo(ahoraMs: Long): Long =
        if (ahoraMs >= bloqueadoHastaMs) 0 else (bloqueadoHastaMs - ahoraMs + 999) / 1000

    fun registrarFallo(ahoraMs: Long) {
        intentosFallidos++
        if (intentosFallidos >= MAX_INTENTOS) {
            intentosFallidos = 0
            bloqueadoHastaMs = ahoraMs + BLOQUEO_MS
        }
    }
}
