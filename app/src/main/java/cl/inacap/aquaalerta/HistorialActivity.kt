package cl.inacap.aquaalerta

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Consulta de los datos guardados en SQLite: lecturas del sensor y bitacora de eventos. */
class HistorialActivity : AppCompatActivity() {

    private lateinit var db: BaseDatos
    private lateinit var lista: ListView
    private lateinit var tvResumen: TextView
    private var verLecturas = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (exigirSesion()) return
        enableEdgeToEdge()
        setContentView(R.layout.activity_historial)
        ajustarBordes(findViewById(R.id.main))

        db = BaseDatos.obtener(this)
        lista = findViewById(R.id.lista)
        tvResumen = findViewById(R.id.tvResumen)
        val grupoVista: MaterialButtonToggleGroup = findViewById(R.id.grupoVista)
        val btnActualizar: Button = findViewById(R.id.btnActualizar)
        val btnBorrar: Button = findViewById(R.id.btnBorrar)

        grupoVista.addOnButtonCheckedListener { _, id, marcado ->
            if (marcado) {
                verLecturas = id == R.id.btnLecturas
                cargar()
            }
        }
        btnActualizar.setOnClickListener { cargar() }

        btnBorrar.isEnabled = Sesion.esAdmin
        btnBorrar.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("¿Borrar historial?")
                .setMessage("Se eliminarán todas las lecturas y eventos de este teléfono.")
                .setPositiveButton("Borrar") { _, _ ->
                    db.borrarHistorial()
                    // Queda registro de quien borro (trazabilidad)
                    db.guardarEvento("HISTORIAL BORRADO", "APP", Sesion.usuario ?: "-")
                    cargar()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        cargar()
    }

    private fun cargar() {
        val datos = if (verLecturas) db.ultimasLecturas() else db.ultimosEventos()
        lista.adapter = ArrayAdapter(this, R.layout.item_registro, datos.ifEmpty { listOf("Sin registros todavía") })
        tvResumen.text = db.resumen()
    }
}
