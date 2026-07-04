package com.cybersentinel.phoneguard.monitor

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import com.cybersentinel.phoneguard.data.SecurityCheck
import java.io.File

/**
 * Analisi dello stato di sicurezza del sistema, con attenzione ai segnali
 * di controllo del telefono dall'esterno:
 *
 *  - segni di root (binari su, build test-keys, percorsi Magisk);
 *  - gestione remota del dispositivo (MDM / device owner / work profile);
 *  - app con privilegi di amministratore del dispositivo;
 *  - servizi di accessibilità attivi (possono leggere schermo e tasti);
 *  - app con accesso alla lettura delle notifiche;
 *  - VPN attiva (può instradare tutto il traffico verso terzi);
 *  - debug USB (ADB) e opzioni sviluppatore attive;
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

        checks.add(remoteManagementCheck())
        checks.add(deviceAdminCheck())
        checks.add(accessibilityCheck())
        checks.add(notificationListenerCheck())
        checks.add(vpnCheck())

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

    /**
     * Il telefono è gestito da un'organizzazione (MDM)? Un device owner o
     * un profilo di lavoro può controllare app, rete e policy da remoto.
     */
    private fun remoteManagementCheck(): SecurityCheck {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val pm = context.packageManager

        val managers = pm.getInstalledApplications(0)
            .asSequence()
            .map { it.packageName }
            .filter {
                runCatching { dpm.isDeviceOwnerApp(it) }.getOrDefault(false) ||
                        runCatching { dpm.isProfileOwnerApp(it) }.getOrDefault(false)
            }
            .map { appLabel(it) }
            .toList()

        return if (managers.isEmpty()) SecurityCheck(
            "Gestione remota (MDM)", true,
            "Nessuna app gestisce il dispositivo da remoto"
        ) else SecurityCheck(
            "Gestione remota (MDM)", false,
            "Il dispositivo è gestito da: ${managers.joinToString(", ")}. " +
                    "Chi lo gestisce può controllare app e traffico da remoto."
        )
    }

    /** App con privilegi di amministratore del dispositivo. */
    private fun deviceAdminCheck(): SecurityCheck {
        val admins = SystemServices.deviceAdminPackages(context)
            .filterNot { it in BENIGN_ADMIN_PACKAGES }
            .map { appLabel(it) }

        return if (admins.isEmpty()) SecurityCheck(
            "Amministratori dispositivo", true,
            "Nessuna app con privilegi di amministratore"
        ) else SecurityCheck(
            "Amministratori dispositivo", false,
            "App amministratore: ${admins.joinToString(", ")}. " +
                    "Possono bloccare il telefono e impedire la propria disinstallazione."
        )
    }

    /**
     * Servizi di accessibilità attivi: possono leggere schermo e digitazione.
     *
     * Filtriamo i servizi di sistema (TalkBack, Seleziona per parlare,
     * Accesso vocale, ecc.): sono funzioni di accessibilità del produttore,
     * non app di terze parti, e segnalarle spaventerebbe inutilmente
     * chi le usa per necessità (utenti con disabilità visive/motorie).
     */
    private fun accessibilityCheck(): SecurityCheck {
        val services = nonSystemPackages(
            SystemServices.enabledServicePackages(
                context, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
        ).map { appLabel(it) }

        return if (services.isEmpty()) SecurityCheck(
            "Servizi di accessibilità", true,
            "Nessun servizio di accessibilità attivo"
        ) else SecurityCheck(
            "Servizi di accessibilità", false,
            "Attivi per: ${services.joinToString(", ")}. " +
                    "Un servizio di accessibilità può leggere tutto ciò che appare sullo schermo: verifica che siano app di cui ti fidi."
        )
    }

    /** App autorizzate a leggere tutte le notifiche (messaggi, chat, OTP). */
    private fun notificationListenerCheck(): SecurityCheck {
        val listeners = nonSystemPackages(
            SystemServices.enabledServicePackages(
                context, SystemServices.NOTIFICATION_LISTENERS_SETTING
            )
        ).map { appLabel(it) }

        return if (listeners.isEmpty()) SecurityCheck(
            "Lettura notifiche", true,
            "Nessuna app legge le tue notifiche"
        ) else SecurityCheck(
            "Lettura notifiche", false,
            "Leggono le notifiche: ${listeners.joinToString(", ")}. " +
                    "Vedono messaggi e codici OTP: verifica che siano app di cui ti fidi."
        )
    }

    /**
     * VPN attiva: informativo, non un'anomalia. Molti utenti attenti alla
     * privacy (il pubblico naturale di quest'app) tengono una VPN sempre
     * attiva di proposito; segnalarlo come "attenzione richiesta" sarebbe un
     * falso allarme costante per loro. Restiamo neutrali: mostriamo lo stato
     * senza farlo contare come controllo fallito.
     */
    private fun vpnCheck(): SecurityCheck {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = cm.activeNetwork?.let { network ->
            cm.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        } ?: false

        return if (active) SecurityCheck(
            "VPN", true,
            "VPN attiva: se l'hai attivata tu (es. per privacy o lavoro) va tutto bene. Se non la riconosci, verifica quale app la gestisce da Impostazioni > Rete > VPN."
        ) else SecurityCheck(
            "VPN", true, "Nessuna VPN attiva"
        )
    }

    /** Filtra via i pacchetti marcati come app di sistema (FLAG_SYSTEM). */
    private fun nonSystemPackages(packages: Set<String>): Set<String> {
        val pm = context.packageManager
        return packages.filterNot { pkg ->
            runCatching {
                (pm.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_SYSTEM) != 0
            }.getOrDefault(false)
        }.toSet()
    }

    private fun appLabel(packageName: String): String = runCatching {
        context.packageManager.getApplicationLabel(
            context.packageManager.getApplicationInfo(packageName, 0)
        ).toString()
    }.getOrDefault(packageName)

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

    /**
     * Nomi delle app utente installate da uno store di terze parti
     * *riconosciuto ma non ufficiale* (es. store alternativi non elencati).
     *
     * Nota anti-falso-positivo: un installer `null` NON viene considerato
     * sideload. Succede in casi larghissimamente comuni e innocui: app
     * installate tramite ADB (sviluppatori), ripristinate durante il
     * trasferimento a un telefono nuovo (Smart Switch, Google backup) o
     * preinstallate da alcuni OEM. Trattarlo come "fuori store" genererebbe
     * un allarme su quasi ogni telefono con più di qualche app installata
     * manualmente. Segnaliamo solo un installer *presente ma non
     * riconosciuto*, il segnale realmente informativo.
     */
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
                installer != null && installer !in TRUSTED_INSTALLERS
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
            "com.heytap.market",
            "org.fdroid.fdroid",         // F-Droid: store open-source legittimo
            "com.aurora.store"           // Aurora Store: client Play Store senza account Google
        )

        /** Admin di sistema noti e innocui (es. Trova il mio dispositivo). */
        val BENIGN_ADMIN_PACKAGES = setOf(
            "com.google.android.gms",
            "com.google.android.apps.adm"
        )
    }
}
