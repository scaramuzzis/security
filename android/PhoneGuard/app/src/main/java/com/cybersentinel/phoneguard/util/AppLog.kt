package com.cybersentinel.phoneguard.util

import android.content.Context
import com.cybersentinel.phoneguard.data.Prefs
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro attività dell'app, attivabile dalle Impostazioni.
 *
 * Scrive su file privato (filesDir) con rotazione automatica: quando il
 * file supera la dimensione massima vengono conservate solo le ultime
 * [MAX_LINES] righe. Se il registro è disattivato, log() non fa nulla.
 */
object AppLog {

    private const val FILE_NAME = "phoneguard.log"
    private const val MAX_BYTES = 256 * 1024L
    private const val MAX_LINES = 500

    private val formatter = SimpleDateFormat("dd/MM HH:mm:ss", Locale.ITALY)

    fun log(context: Context, message: String) {
        if (!Prefs.loggingEnabled(context)) return
        runCatching {
            synchronized(this) {
                val file = File(context.filesDir, FILE_NAME)
                file.appendText("[${formatter.format(Date())}] $message\n")
                if (file.length() > MAX_BYTES) {
                    val lines = file.readLines().takeLast(MAX_LINES)
                    file.writeText(lines.joinToString("\n") + "\n")
                }
            }
        }
    }

    /** Ultime [maxLines] righe, dalla più recente alla più vecchia. */
    fun read(context: Context, maxLines: Int = 200): String = runCatching {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return ""
        file.readLines().takeLast(maxLines).reversed().joinToString("\n")
    }.getOrDefault("")

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
    }
}
