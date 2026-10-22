package cl.inacap.aquaalerta

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var db: BaseDatos
    private lateinit var tvMensaje: TextView
    private lateinit var tvIrRegistro: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)
        ajustarBordes(findViewById(R.id.main))

        db = BaseDatos.obtener(this)
        val edtUsuario: EditText = findViewById(R.id.edtUsuario)
        val edtContrasena: EditText = findViewById(R.id.edtContrasena)
        val btnIngresar: Button = findViewById(R.id.btnIngresar)
        tvMensaje = findViewById(R.id.tvMensaje)
        tvIrRegistro = findViewById(R.id.tvIrRegistro)

        btnIngresar.setOnClickListener {
            val usuario = edtUsuario.text.toString().trim().lowercase()
            val contrasena = edtContrasena.text.toString()
            val ahora = SystemClock.elapsedRealtime()

            val espera = Sesion.segundosDeBloqueo(ahora)
            if (espera > 0) {
                tvMensaje.text = "Demasiados intentos fallidos. Espere $espera s."
                return@setOnClickListener
            }
            if (usuario.isEmpty() || contrasena.isEmpty()) {
                tvMensaje.text = "Ingrese usuario y contraseña"
                return@setOnClickListener
            }

            val registro = db.buscarUsuario(usuario)
            val valido = registro != null &&
                Seguridad.verificarContrasena(contrasena, registro.sal, registro.hash)
            edtContrasena.text.clear()

            if (!valido || registro == null) {
                // Mensaje generico: no revela si el usuario existe o no
                Sesion.registrarFallo(ahora)
                db.guardarEvento("LOGIN FALLIDO", "APP", usuario)
                tvMensaje.text = "Usuario o contraseña incorrectos"
                return@setOnClickListener
            }

            Sesion.iniciar(registro.usuario, registro.rol)
            db.guardarEvento("LOGIN OK (${registro.rol})", "APP", registro.usuario)
            tvMensaje.text = ""
            startActivity(Intent(this, MenuActivity::class.java))
            finish()
        }

        tvIrRegistro.setOnClickListener {
            startActivity(Intent(this, RegistroActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // El registro libre solo existe para crear el primer administrador
        val sinUsuarios = db.cantidadUsuarios() == 0L
        tvIrRegistro.visibility = if (sinUsuarios) View.VISIBLE else View.GONE
        if (sinUsuarios) tvMensaje.text = "No hay usuarios: cree la cuenta de administrador."
        else if (tvMensaje.text.startsWith("No hay usuarios")) tvMensaje.text = ""
    }
}
