package com.cybersentinel.aidiag.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.cybersentinel.aidiag.data.BatterySample
import com.cybersentinel.aidiag.data.GrantKind
import com.cybersentinel.aidiag.data.NetworkSample
import com.cybersentinel.aidiag.data.PrivilegedGrant

/**
 * Cronologia dei campionamenti periodici (batteria/schermo, traffico di
 * rete, permessi privilegiati concessi), in SQLite locale — mai inviata da
 * nessuna parte se non esplicitamente riassunta nel controllo IA.
 *
 * Rotazione a righe massime per intervallo, così la memoria usata resta
 * limitata indipendentemente da quanto a lungo il servizio resta attivo.
 */
class SignalStore private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE battery_sample (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, " +
                    "level INTEGER NOT NULL, temp REAL NOT NULL, " +
                    "charging INTEGER NOT NULL, screen_on INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX idx_battery_ts ON battery_sample(ts)")

        db.execSQL(
            "CREATE TABLE network_sample (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, " +
                    "package TEXT NOT NULL, label TEXT NOT NULL, " +
                    "tx INTEGER NOT NULL, rx INTEGER NOT NULL, screen_on INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX idx_network_ts ON network_sample(ts)")

        db.execSQL(
            "CREATE TABLE privileged_grant (" +
                    "package TEXT NOT NULL, kind TEXT NOT NULL, label TEXT NOT NULL, " +
                    "first_seen INTEGER NOT NULL, PRIMARY KEY(package, kind))"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS battery_sample")
        db.execSQL("DROP TABLE IF EXISTS network_sample")
        db.execSQL("DROP TABLE IF EXISTS privileged_grant")
        onCreate(db)
    }

    fun insertBatterySample(sample: BatterySample) {
        writableDatabase.insert(
            "battery_sample", null,
            ContentValues().apply {
                put("ts", sample.timestamp)
                put("level", sample.levelPercent)
                put("temp", sample.temperatureCelsius)
                put("charging", if (sample.isCharging) 1 else 0)
                put("screen_on", if (sample.screenOn) 1 else 0)
            }
        )
        writableDatabase.delete("battery_sample", "ts < ?", arrayOf((System.currentTimeMillis() - RETENTION_MS).toString()))
    }

    fun insertNetworkSamples(samples: List<NetworkSample>) {
        if (samples.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            samples.forEach { sample ->
                db.insert(
                    "network_sample", null,
                    ContentValues().apply {
                        put("ts", sample.timestamp)
                        put("package", sample.packageName)
                        put("label", sample.appLabel)
                        put("tx", sample.txBytes)
                        put("rx", sample.rxBytes)
                        put("screen_on", if (sample.screenOn) 1 else 0)
                    }
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        db.delete("network_sample", "ts < ?", arrayOf((System.currentTimeMillis() - RETENTION_MS).toString()))
    }

    /** Registra un permesso privilegiato osservato ORA; se già noto per quel pacchetto+tipo, non sovrascrive first_seen. */
    fun recordGrantIfNew(packageName: String, appLabel: String, kind: GrantKind, now: Long) {
        writableDatabase.execSQL(
            "INSERT OR IGNORE INTO privileged_grant (package, kind, label, first_seen) VALUES (?, ?, ?, ?)",
            arrayOf(packageName, kind.name, appLabel, now)
        )
    }

    /** Rimuove i permessi non più presenti nell'insieme attuale (revocati/disinstallati). */
    fun pruneGrants(kind: GrantKind, stillPresentPackages: Set<String>) {
        val db = writableDatabase
        if (stillPresentPackages.isEmpty()) {
            db.delete("privileged_grant", "kind = ?", arrayOf(kind.name))
            return
        }
        val placeholders = stillPresentPackages.joinToString(",") { "?" }
        db.delete(
            "privileged_grant", "kind = ? AND package NOT IN ($placeholders)",
            arrayOf(kind.name, *stillPresentPackages.toTypedArray())
        )
    }

    fun batterySamplesSince(since: Long): List<BatterySample> {
        val rows = ArrayList<BatterySample>()
        readableDatabase.rawQuery(
            "SELECT ts, level, temp, charging, screen_on FROM battery_sample WHERE ts >= ? ORDER BY ts",
            arrayOf(since.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows.add(
                    BatterySample(
                        timestamp = cursor.getLong(0),
                        levelPercent = cursor.getInt(1),
                        temperatureCelsius = cursor.getFloat(2),
                        isCharging = cursor.getInt(3) != 0,
                        screenOn = cursor.getInt(4) != 0
                    )
                )
            }
        }
        return rows
    }

    fun networkSamplesSince(since: Long): List<NetworkSample> {
        val rows = ArrayList<NetworkSample>()
        readableDatabase.rawQuery(
            "SELECT ts, package, label, tx, rx, screen_on FROM network_sample WHERE ts >= ? ORDER BY ts",
            arrayOf(since.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows.add(
                    NetworkSample(
                        timestamp = cursor.getLong(0),
                        packageName = cursor.getString(1),
                        appLabel = cursor.getString(2),
                        txBytes = cursor.getLong(3),
                        rxBytes = cursor.getLong(4),
                        screenOn = cursor.getInt(5) != 0
                    )
                )
            }
        }
        return rows
    }

    fun allGrants(): List<PrivilegedGrant> {
        val rows = ArrayList<PrivilegedGrant>()
        readableDatabase.rawQuery(
            "SELECT package, kind, label, first_seen FROM privileged_grant ORDER BY first_seen DESC", null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows.add(
                    PrivilegedGrant(
                        packageName = cursor.getString(0),
                        kind = GrantKind.valueOf(cursor.getString(1)),
                        appLabel = cursor.getString(2),
                        firstSeen = cursor.getLong(3)
                    )
                )
            }
        }
        return rows
    }

    companion object {
        private const val DB_NAME = "aidiag_signals.db"
        private const val DB_VERSION = 1

        /** Tiene al massimo 7 giorni di campionamenti: oltre non serve per questo scopo. */
        private const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000

        @Volatile
        private var instance: SignalStore? = null

        fun get(context: Context): SignalStore =
            instance ?: synchronized(this) {
                instance ?: SignalStore(context).also { instance = it }
            }
    }
}
