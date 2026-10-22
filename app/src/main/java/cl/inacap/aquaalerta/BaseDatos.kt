package cl.inacap.aquaalerta

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Base de datos local SQLite con tres tablas:
 *  - usuarios: credenciales (hash + sal, nunca la contrasena) y rol.
 *  - lecturas: telemetria recibida del equipo (nivel, estado, valvula).
 *  - eventos: bitacora de auditoria (logins, comandos, alarmas, cambios de estado).
 * Todas las consultas usan parametros (?) para evitar inyeccion SQL.
 */
class BaseDatos private constructor(context: Context) :
    SQLiteOpenHelper(context, "aquaalerta.db", null, 1) {

    companion object {
        @Volatile
        private var instancia: BaseDatos? = null

        fun obtener(context: Context): BaseDatos =
            instancia ?: synchronized(this) {
                instancia ?: BaseDatos(context.applicationContext).also { instancia = it }
            }
    }

    data class Usuario(val usuario: String, val hash: String, val sal: String, val rol: String)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE usuarios (id INTEGER PRIMARY KEY AUTOINCREMENT, usuario TEXT NOT NULL UNIQUE, " +
                "hash TEXT NOT NULL, sal TEXT NOT NULL, rol TEXT NOT NULL, creado INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE lecturas (id INTEGER PRIMARY KEY AUTOINCREMENT, fecha INTEGER NOT NULL, " +
                "adc INTEGER NOT NULL, estado TEXT NOT NULL, valvula TEXT NOT NULL, equipo TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE eventos (id INTEGER PRIMARY KEY AUTOINCREMENT, fecha INTEGER NOT NULL, " +
                "descripcion TEXT NOT NULL, origen TEXT NOT NULL, usuario TEXT NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, versionAnterior: Int, versionNueva: Int) {
        db.execSQL("DROP TABLE IF EXISTS usuarios")
        db.execSQL("DROP TABLE IF EXISTS lecturas")
        db.execSQL("DROP TABLE IF EXISTS eventos")
        onCreate(db)
    }

    // ---------------- Usuarios ----------------

    fun cantidadUsuarios(): Long = DatabaseUtils.queryNumEntries(readableDatabase, "usuarios")

    fun buscarUsuario(usuario: String): Usuario? =
        readableDatabase.rawQuery(
            "SELECT usuario, hash, sal, rol FROM usuarios WHERE usuario = ?", arrayOf(usuario)
        ).use { c ->
            if (c.moveToFirst()) Usuario(c.getString(0), c.getString(1), c.getString(2), c.getString(3)) else null
        }

    /** Devuelve false si el usuario ya existe. */
    fun registrarUsuario(usuario: String, contrasena: String, rol: String): Boolean {
        val sal = Seguridad.generarSal()
        val valores = ContentValues().apply {
            put("usuario", usuario)
            put("hash", Seguridad.hashContrasena(contrasena, sal))
            put("sal", sal)
            put("rol", rol)
            put("creado", System.currentTimeMillis())
        }
        return writableDatabase.insert("usuarios", null, valores) != -1L
    }

    // ---------------- Lecturas y eventos ----------------

    fun guardarLectura(t: Telemetria, equipo: String) {
        val valores = ContentValues().apply {
            put("fecha", System.currentTimeMillis())
            put("adc", t.adc)
            put("estado", t.estado)
            put("valvula", if (t.valvulaAbierta) "ABIERTA" else "CERRADA")
            put("equipo", equipo)
        }
        writableDatabase.insert("lecturas", null, valores)
    }

    fun guardarEvento(descripcion: String, origen: String, usuario: String) {
        val valores = ContentValues().apply {
            put("fecha", System.currentTimeMillis())
            put("descripcion", descripcion)
            put("origen", origen)
            put("usuario", usuario)
        }
        writableDatabase.insert("eventos", null, valores)
    }

    fun ultimasLecturas(limite: Int = 300): List<String> =
        readableDatabase.rawQuery(
            "SELECT fecha, adc, estado, valvula, equipo FROM lecturas ORDER BY id DESC LIMIT ?",
            arrayOf(limite.toString())
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add("${fechaHora(c.getLong(0))}  ADC=${c.getInt(1).toString().padStart(4)}  ${c.getString(2).padEnd(7)}  válvula ${c.getString(3)}  [${c.getString(4)}]")
                }
            }
        }

    fun ultimosEventos(limite: Int = 300): List<String> =
        readableDatabase.rawQuery(
            "SELECT fecha, descripcion, origen, usuario FROM eventos ORDER BY id DESC LIMIT ?",
            arrayOf(limite.toString())
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add("${fechaHora(c.getLong(0))}  ${c.getString(1)}  [${c.getString(2)} · ${c.getString(3)}]")
                }
            }
        }

    fun resumen(): String {
        val db = readableDatabase
        val lecturas = DatabaseUtils.queryNumEntries(db, "lecturas")
        val eventos = DatabaseUtils.queryNumEntries(db, "eventos")
        val maximo = DatabaseUtils.longForQuery(db, "SELECT IFNULL(MAX(adc), 0) FROM lecturas", null)
        val promedio = DatabaseUtils.longForQuery(db, "SELECT IFNULL(CAST(AVG(adc) AS INTEGER), 0) FROM lecturas", null)
        val rebalses = DatabaseUtils.queryNumEntries(db, "eventos", "descripcion LIKE ?", arrayOf("%->REBALSE%"))
        return "Lecturas: $lecturas · Eventos: $eventos\nADC máximo: $maximo · ADC promedio: $promedio · Rebalses: $rebalses"
    }

    fun borrarHistorial() {
        writableDatabase.delete("lecturas", null, null)
        writableDatabase.delete("eventos", null, null)
    }
}
