package com.cybersentinel.phoneguard.monitor

import android.app.KeyguardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.provider.Settings
import com.cybersentinel.phoneguard.data.SecurityCheck
import java.io.File

/**
 * Analisi dello stato di sicurezza del sistema:
 *
 *  - segni di root (binari su, build test-keys, percorsi Magisk);
 *  - debug USB (ADB) attivo;
 *  - opzioni sviluppatore attive;
 *  - blocco schermo impostato;
 *  - app installate fuori dagli store ufficiali (sideload).
 */
class SystemAnalyzer(private val context: Context) {

    fun analyze(): List<SecurityCheck> {
        val checks = ArrayList<SecurityCheck>()

        val rootSigns = rootIndicators()
        checks.add(
            if (rootSigns.isEmpty()) SecurityCheck(
                "Root", true, "Nessun segno di root rilevato"
            ) else SecurityCheck(
                "Root", false,
                "Possibile root: ${rootSigns.joinToString(", ")}. " +
                        "Un dispositivo rootato è più esposto ai malware."
            )
        )

        val adbEnabled = Settings.Global.getInt(
            context.contentResolver, Settings.Global.ADB_ENABLED, 0
        ) == 1
        checks.add(
            SecurityCheck(
                "Debug USB", !adbEnabled,
                if (adbEnabled)
                    "Il debug USB è attivo: chi accede fisicamente al telefono può controllarlo. Disattivalo se non ti serve."
                else "Debug USB disattivato"
            )
        )

        val devEnabled = Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0
        ) == 1
        checks.add(
            SecurityCheck(
                "Opzioni sviluppatore", !devEnabled,
                if (devEnabled) "Le opzioni sviluppatore sono attive"
                else "Opzioni sviluppatore disattivate"
            )
        )

        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val secure = keyguard.isDeviceSecure
        checks.add(
            SecurityCheck(
                "Blocco schermo", secure,
                if (secure) "PIN/sequenza/impronta impostati"
                else "Nessun blocco schermo: chiunque può accedere al telefono"
            )
        )

        val sideloaded = sideloadedApps()
        checks.add(
            if (sideloaded.isEmpty()) SecurityCheck(
                "App fuori store", true, "Tutte le app provengono da store ufficiali"
            ) else SecurityCheck(
                "App fuori store", false,
                "${sideloaded.size} app installate fuori dagli store ufficiali: " +
                        sideloaded.take(5).joinToString(", ") +
                        if (sideloaded.size > 5) "…" else ""
            )
        )

        return checks
    }

    /** Indizi di root presenti sul dispositivo. */
    private fun rootIndicators(): List<String> {
        val signs = ArrayList<String>()

        if (Build.TAGS?.contains("test-keys") == true) {
            signs.add("build firmata test-keys")
        }

        val suPaths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/system/sd/xbin/su", "/data/local/bin/su",
            "/data/local/xbin/su", "/data/local/su", "/su/bin/su"
        )
        if (suPaths.any { runCatching { File(it).exists() }.getOrDefault(false) }) {
            signs.add("binario su presente")
        }

        val magiskPaths = listOf("/sbin/.magisk", "/cache/.disable_magisk", "/data/adb/magisk")
        if (magiskPaths.any { runCatching { File(it).exists() }.getOrDefault(false) }) {
            signs.add("tracce di Magisk")
        }

        return signs
    }

    /** Nomi delle app utente non installate da uno store riconosciuto. */
    private fun sideloadedApps(): List<String> {
        val pm = context.packageManager
        return pm.getInstalledApplications(0)
            .asSequence()
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .filter { it.packageName != context.packageName }
            .filter { app ->
                val installer = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        pm.getInstallSourceInfo(app.packageName).installingPackageName
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getInstallerPackageName(app.packageName)
                    }
                }.getOrNull()
                installer == null || installer !in TRUSTED_INSTALLERS
            }
            .map { pm.getApplicationLabel(it).toString() }
            .sorted()
            .toList()
    }

    companion object {
        /** Store ufficiali riconosciuti come origine affidabile. */
        val TRUSTED_INSTALLERS = setOf(
            "com.android.vending",              // Google Play
            "com.google.android.feedback",
            "com.sec.android.app.samsungapps",  // Galaxy Store
            "com.huawei.appmarket",             // Huawei AppGallery
            "com.xiaomi.mipicks",               // Xiaomi GetApps
            "com.amazon.venezia",               // Amazon Appstore
            "com.oppo.market",
            "com.heytap.market"
        )
    }
}
