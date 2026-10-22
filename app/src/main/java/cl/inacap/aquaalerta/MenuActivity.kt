package cl.inacap.aquaalerta

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class MenuActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (exigirSesion()) return
        enableEdgeToEdge()
        setContentView(R.layout.activity_menu)
        ajustarBordes(findViewById(R.id.main))

        val tvBienvenida: TextView = findViewById(R.id.tvBienvenida)
        val tvRol: TextView = findViewById(R.id.tvRol)
        val btnUsuarios: Button = findViewById(R.id.btnUsuarios)
        val btnCerrarSesion: Button = findViewById(R.id.btnCerrarSesion)

        tvBienvenida.text = "Hola, ${Sesion.usuario}"
        tvRol.text = if (Sesion.esAdmin) "Rol ADMIN: monitoreo, comandos y gestión de usuarios"
        else "Rol OPERADOR: solo monitoreo (sin comandos críticos)"
        btnUsuarios.visibility = if (Sesion.esAdmin) View.VISIBLE else View.GONE

        findViewById<View>(R.id.cardEstanque).setOnClickListener {
            startActivity(Intent(this, EstanqueActivity::class.java))
        }
        findViewById<View>(R.id.cardMonitor).setOnClickListener {
            startActivity(Intent(this, MonitorActivity::class.java))
        }
        findViewById<View>(R.id.cardHistorial).setOnClickListener {
            startActivity(Intent(this, HistorialActivity::class.java))
        }
        btnUsuarios.setOnClickListener {
            startActivity(Intent(this, RegistroActivity::class.java))
        }
        btnCerrarSesion.setOnClickListener {
            BaseDatos.obtener(this).guardarEvento("LOGOUT", "APP", Sesion.usuario ?: "-")
            Sesion.cerrar()
            exigirSesion()
        }
    }

    override fun onResume() {
        super.onResume()
        exigirSesion()
    }
}
