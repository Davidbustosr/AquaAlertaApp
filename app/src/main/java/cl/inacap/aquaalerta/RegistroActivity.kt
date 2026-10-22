package cl.inacap.aquaalerta

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

/**
 * Crea cuentas. La primera cuenta del telefono es ADMIN (configuracion inicial, sin claves por defecto).
 * Despues, solo un ADMIN con sesion iniciada puede crear usuarios (control de acceso).
 */
class RegistroActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = BaseDatos.obtener(this)
        val primeraCuenta = db.cantidadUsuarios() == 0L
        if (!primeraCuenta && !Sesion.esAdmin) {
            aviso("Solo un administrador puede crear usuarios")
            finish()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_registro)
        ajustarBordes(findViewById(R.id.main))

        val tvTitulo: TextView = findViewById(R.id.tvTitulo)
        val tvInfo: TextView = findViewById(R.id.tvInfo)
        val edtUsuario: EditText = findViewById(R.id.edtUsuario)
        val edtContrasena: EditText = findViewById(R.id.edtContrasena)
        val edtConfirmar: EditText = findViewById(R.id.edtConfirmar)
        val tvRol: TextView = findViewById(R.id.tvRol)
        val grupoRol: RadioGroup = findViewById(R.id.grupoRol)
        val rbAdmin: RadioButton = findViewById(R.id.rbAdmin)
        val btnCrear: Button = findViewById(R.id.btnCrear)
        val btnVolver: Button = findViewById(R.id.btnVolver)

        if (primeraCuenta) {
            tvTitulo.text = "Crear administrador"
            tvInfo.text = "Configuración inicial: esta primera cuenta tendrá rol ADMIN. La app no trae usuarios ni contraseñas por defecto."
            tvRol.visibility = View.GONE
            grupoRol.visibility = View.GONE
        } else {
            tvTitulo.text = "Crear usuario"
            tvInfo.text = "Creado por ${Sesion.usuario}. Elija el rol según el principio de mínimo privilegio."
        }

        btnCrear.setOnClickListener {
            val usuario = edtUsuario.text.toString().trim().lowercase()
            val contrasena = edtContrasena.text.toString()
            val confirmacion = edtConfirmar.text.toString()
            listOf(edtUsuario, edtContrasena, edtConfirmar).forEach { it.mostrarError(null) }

            when {
                !usuario.matches(Regex("[a-z0-9._]{3,20}")) ->
                    edtUsuario.mostrarError("3 a 20 caracteres: letras, números, punto o guion bajo")
                !Seguridad.contrasenaSegura(contrasena) ->
                    edtContrasena.mostrarError("Mínimo 8 caracteres, con letras y números")
                contrasena != confirmacion ->
                    edtConfirmar.mostrarError("Las contraseñas no coinciden")
                else -> {
                    val rol = if (primeraCuenta || rbAdmin.isChecked) Sesion.ROL_ADMIN else Sesion.ROL_OPERADOR
                    if (db.registrarUsuario(usuario, contrasena, rol)) {
                        db.guardarEvento("USUARIO CREADO $usuario ($rol)", "APP", Sesion.usuario ?: usuario)
                        aviso("Usuario $usuario creado con rol $rol")
                        finish()
                    } else {
                        edtUsuario.mostrarError("Ese usuario ya existe")
                    }
                }
            }
        }

        btnVolver.setOnClickListener { finish() }
    }
}
