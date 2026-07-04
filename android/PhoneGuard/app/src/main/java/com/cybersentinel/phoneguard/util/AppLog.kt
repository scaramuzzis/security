package com.cybersentinel.phoneguard.util

import android.content.Context
import com.cybersentinel.phoneguard.data.Prefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Categorie del registro attività: costanti invece di stringhe libere,
 * per evitare refusi silenziosi che spezzerebbero i filtri per categoria
 * (es. [AppLog.readCategory]) senza dare errore.
 */
object LogCategory {
    const val SERVIZIO = "SERVIZIO"
    const val AVVISO = "AVVISO"
    const val SCANSIONE = "SCANSIONE"
    const val PULIZIA = "PULIZIA"
    const val SISTEMA = "SISTEMA"
    const val RETE = "RETE"
    const val CRASH = "CRASH"
}

/**
 * Registro attività dell'app, attivabile dalle Impostazioni.
 *
 * Facciata su [LogStore] (SQLite). Le scritture sono affidate a un singolo
 * thread di background per non bloccare mai chi chiama (servizio o UI); le
 * letture usano query con LIMIT, quindi la memoria è indipendente dalla
 * dimensione del registro.
 *
 * Categorie usate: SERVIZIO, AVVISO, SCANSIONE, PULIZIA, SISTEMA, GENERALE.
 * Se il registro è disattivato, log() non fa nulla.
 */
object AppLog {

    private val writer = Executors.newSingleThreadExecutor { r ->
        Thread(r, "phoneguard-log").apply { isDaemon = true }
    }
    private val formatter = SimpleDateFormat("dd/MM HH:mm:ss", Locale.ITALY)

    fun log(context: Context, message: String) = log(context, "GENERALE", message)

    fun log(context: Context, category: String, message: String) {
        if (!Prefs.loggingEnabled(context)) return
        val appContext = context.applicationContext
        val timestamp = System.currentTimeMillis()
        writer.execute {
            runCatching { LogStore.get(appContext).insert(category, message, timestamp) }
        }
    }

    /**
     * Ultimi [maxLines] eventi formattati, dal più recente.
     * Esegue una query bloccante: chiamare da un thread di background.
     */
    fun read(context: Context, maxLines: Int = 200): String {
        val rows = runCatching { LogStore.get(context.applicationContext).recent(maxLines) }
            .getOrDefault(emptyList())
        return rows.joinToString("\n") { row ->
            "[${formatter.format(Date(row.timestamp))}] ${row.category}: ${row.message}"
        }
    }

    /** Come [read], ma filtrato su una sola categoria (es. "RETE"). */
    fun readCategory(context: Context, category: String, maxLines: Int = 100): String {
        val rows = runCatching {
            LogStore.get(context.applicationContext).recentByCategory(category, maxLines)
        }.getOrDefault(emptyList())
        return rows.joinToString("\n\n") { row ->
            "[${formatter.format(Date(row.timestamp))}] ${row.message}"
        }
    }

    /** Svuota il registro. Bloccante: chiamare da un thread di background. */
    fun clear(context: Context) {
        runCatching { LogStore.get(context.applicationContext).clear() }
    }

    /**
     * Come [log], ma scrive subito sul thread chiamante invece di accodare
     * al thread di background. Serve solo al gestore dei crash: il processo
     * sta per terminare, quindi una scrittura asincrona rischierebbe di non
     * essere mai completata.
     */
    fun logSync(context: Context, category: String, message: String) {
        if (!Prefs.loggingEnabled(context)) return
        runCatching {
            LogStore.get(context.applicationContext).insert(category, message, System.currentTimeMillis())
        }
    }
}
