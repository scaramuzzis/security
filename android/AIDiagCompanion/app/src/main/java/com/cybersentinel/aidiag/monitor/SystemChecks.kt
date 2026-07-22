package com.cybersentinel.aidiag.monitor

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.io.File

/** Controlli puntuali di sistema (root, debug USB, opzioni sviluppatore). */
class SystemChecks(private val context: Context) {

    /** Indizi indiretti di root: nessuna app senza privilegi può leggere /system con certezza. */
    fun rootIndicators(): List<String> {
        val signs = ArrayList<String>()
        if (Build.TAGS?.contains("test-keys") == true) signs.add("build firmata test-keys")

        val suPaths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/system/sd/xbin/su", "/data/local/bin/su",
            "/data/local/xbin/su", "/data/local/su", "/su/bin/su"
        )
        if (suPaths.any { runCatching { File(it).exists() }.getOrDefault(false) }) {
            signs.add("binario su presente")
        }
        return signs
    }

    fun isAdbEnabled(): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1

    fun isDeveloperOptionsEnabled(): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
}
