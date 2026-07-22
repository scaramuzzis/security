package com.cybersentinel.phoneguard.monitor

import android.content.Context
import android.content.Intent

/**
 * Determina quali app sono "nascoste": installate ma senza alcuna icona
 * nel launcher, quindi invisibili nel menu app del telefono. Usato sia
 * dal motore anti-spyware sia dalla lista del traffico di rete, per non
 * duplicare la stessa query di sistema in più posti.
 */
object AppVisibility {

    /** Pacchetti che hanno almeno un'attività visibile nel launcher. */
    fun launcherPackages(context: Context): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .toSet()
    }
}
