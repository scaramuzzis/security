package com.cybersentinel.phoneguard.util

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Riga di log letta dal database. */
data class LogRow(val timestamp: Long, val category: String, val message: String)

/**
 * Archivio del registro attività su SQLite, progettato per un uso di memoria
 * ridotto e costante.
 *
 * Schema normalizzato su due tabelle:
 *  - `category(id, name)`  — ogni categoria è memorizzata UNA sola volta
 *    (es. "AVVISO", "SCANSIONE"): gli eventi la referenziano per id invece
 *    di ripeterne il testo, risparmiando spazio su disco;
 *  - `event(id, ts, category_id, message)` — un record per evento, con
 *    indice sul timestamp per query ordinate veloci.
 *
 * Vantaggi rispetto al file di testo precedente:
 *  - le letture usano `LIMIT`: si carica in memoria solo la finestra mostrata,
 *    mai l'intero registro;
 *  - la rotazione è a costo costante (una DELETE mirata), non riscrive il file;
 *  - le scritture sono transazionali e non caricano nulla in RAM.
 */
class LogStore private constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext, DB_NAME, null, DB_VERSION
) {

    init {
        // Rimuove il vecchio registro su file di testo (migrazione una tantum).
        runCatching { context.applicationContext.deleteFile(LEGACY_FILE) }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE category (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT NOT NULL UNIQUE)"
        )
        db.execSQL(
            "CREATE TABLE event (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "ts INTEGER NOT NULL, " +
                    "category_id INTEGER NOT NULL REFERENCES category(id), " +
                    "message TEXT NOT NULL)"
        )
        db.execSQL("CREATE INDEX idx_event_ts ON event(ts)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS event")
        db.execSQL("DROP TABLE IF EXISTS category")
        onCreate(db)
    }

    /** Inserisce un evento e applica la rotazione (max [MAX_ROWS] righe). */
    fun insert(category: String, message: String, timestamp: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val categoryId = categoryId(db, category)
            db.insert("event", null, ContentValues().apply {
                put("ts", timestamp)
                put("category_id", categoryId)
                put("message", message)
            })
            // Rotazione a costo costante: elimina gli eventi più vecchi
            // che eccedono il limite (id è monotòno crescente).
            db.execSQL(
                "DELETE FROM event WHERE id <= (SELECT MAX(id) FROM event) - ?",
                arrayOf<Any>(MAX_ROWS)
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** id della categoria, creandola se non esiste (get-or-insert). */
    private fun categoryId(db: SQLiteDatabase, name: String): Long {
        db.rawQuery("SELECT id FROM category WHERE name = ?", arrayOf(name)).use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }
        return db.insert("category", null, ContentValues().apply { put("name", name) })
    }

    /** Ultimi [limit] eventi, dal più recente. Memoria limitata dal LIMIT. */
    fun recent(limit: Int): List<LogRow> {
        val rows = ArrayList<LogRow>(limit)
        readableDatabase.rawQuery(
            "SELECT e.ts, c.name, e.message FROM event e " +
                    "JOIN category c ON e.category_id = c.id " +
                    "ORDER BY e.id DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                rows.add(LogRow(c.getLong(0), c.getString(1), c.getString(2)))
            }
        }
        return rows
    }

    fun count(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM event", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun clear() {
        writableDatabase.execSQL("DELETE FROM event")
    }

    companion object {
        private const val DB_NAME = "phoneguard_log.db"
        private const val DB_VERSION = 1
        private const val LEGACY_FILE = "phoneguard.log"

        /** Numero massimo di eventi conservati (rotazione). */
        private const val MAX_ROWS = 1000

        @Volatile
        private var instance: LogStore? = null

        fun get(context: Context): LogStore =
            instance ?: synchronized(this) {
                instance ?: LogStore(context).also { instance = it }
            }
    }
}
