package com.cybersentinel.phoneguard.util

import android.content.Context
import kotlin.system.exitProcess

/**
 * Registra le eccezioni non gestite nel registro locale (se l'utente lo ha
 * attivato) prima di lasciare che il sistema gestisca il crash come sempre.
 * Nessun dato lascia il dispositivo: PhoneGuard non ha il permesso INTERNET.
 */
object CrashHandler {

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                AppLog.logSync(
                    appContext,
                    LogCategory.CRASH,
                    "Chiusura inattesa (thread '${thread.name}'): ${throwable.stackTraceToString().take(4000)}"
                )
            }
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable)
            } else {
                exitProcess(1)
            }
        }
    }
}
