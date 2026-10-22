package cl.inacap.aquaalerta

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val PUERTO_EQUIPO = 5050

/** Deja espacio para barra de estado, navegacion y teclado (Android 15+ dibuja de borde a borde). */
fun ajustarBordes(raiz: View) {
    ViewCompat.setOnApplyWindowInsetsListener(raiz) { v, insets ->
        val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
        v.setPadding(b.left, b.top, b.right, b.bottom)
        insets
    }
}

/** Control de acceso: si no hay sesion (p. ej. Android cerro la app) vuelve al login. */
fun Activity.exigirSesion(): Boolean {
    if (Sesion.usuario != null) return false
    startActivity(
        Intent(this, LoginActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    )
    finish()
    return true
}

fun Activity.aviso(texto: String) = Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()

/** IPs del telefono en la red Wi-Fi o hotspot, para que el Monitor sepa a donde conectarse. */
fun direccionesIpLocales(): List<String> = try {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { red ->
            red.inetAddresses.toList().filterIsInstance<Inet4Address>().map { "${it.hostAddress}  (${red.name})" }
        }
} catch (e: Exception) {
    emptyList()
}

fun colorDeEstado(context: Context, estado: String): Int = ContextCompat.getColor(
    context,
    when (estado) {
        "NORMAL" -> R.color.estado_normal
        "ALTO" -> R.color.estado_alto
        "REBALSE" -> R.color.estado_rebalse
        else -> R.color.estado_paro
    }
)

fun hora(ms: Long = System.currentTimeMillis()): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ms))

fun fechaHora(ms: Long): String =
    SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date(ms))

/** Alarma local: tono + vibracion (equivalente al buzzer del Arduino). */
class Alarma(context: Context) {
    private val tono: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_ALARM, 80)
    } catch (e: RuntimeException) {
        null
    }

    @Suppress("DEPRECATION")
    private val vibrador = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    fun pulso() {
        tono?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 250)
        vibrador?.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    fun liberar() {
        tono?.release()
    }
}
