package com.cybersentinel.phoneguard.monitor

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.cybersentinel.phoneguard.R
import com.cybersentinel.phoneguard.data.AppThreat
import com.cybersentinel.phoneguard.data.ThreatSignals
import java.util.Locale

/**
 * Motore anti-spyware: analizza tutte le app utente installate e assegna a
 * ciascuna un punteggio di rischio combinando più segnali indipendenti.
 *
 * Nessun segnale da solo condanna un'app (molte app legittime non hanno
 * icona nel launcher, es. tastiere e plugin): è la combinazione di segnali
 * a far scattare la segnalazione, come farebbe un analista.
 *
 * Segnali considerati:
 *  - pacchetto riconducibile a stalkerware documentato pubblicamente;
 *  - app invisibile nel launcher (nessuna icona con cui aprirla);
 *  - servizio di accessibilità attivo (può leggere lo schermo e i tasti);
 *  - amministratore del dispositivo (può impedire la disinstallazione);
 *  - accesso alla lettura delle notifiche (vede messaggi e chat);
 *  - installata fuori dagli store ufficiali;
 *  - permessi di sorveglianza concessi (microfono, posizione, SMS, ...).
 */
class ThreatScanner(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    fun scan(): List<AppThreat> {
        val launcherPackages = launcherPackages()
        val adminPackages = SystemServices.deviceAdminPackages(context)
        val accessibilityPackages = SystemServices.enabledServicePackages(
            context, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        val listenerPackages = SystemServices.enabledServicePackages(
            context, SystemServices.NOTIFICATION_LISTENERS_SETTING
        )

        return pm.getInstalledApplications(0)
            .asSequence()
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .filter { it.packageName != context.packageName }
            .mapNotNull { app ->
                val signals = collectSignals(
                    app, launcherPackages, adminPackages,
                    accessibilityPackages, listenerPackages
                )
                val score = score(signals)
                if (score < REPORT_THRESHOLD) return@mapNotNull null
                AppThreat(
                    packageName = app.packageName,
                    label = pm.getApplicationLabel(app).toString(),
                    signals = signals,
                    score = score,
                    reasons = describe(signals)
                )
            }
            .sortedByDescending { it.score }
            .toList()
    }

    private fun collectSignals(
        app: ApplicationInfo,
        launcherPackages: Set<String>,
        adminPackages: Set<String>,
        accessibilityPackages: Set<String>,
        listenerPackages: Set<String>
    ): ThreatSignals {
        val pkg = app.packageName.lowercase(Locale.ROOT)
        return ThreatSignals(
            knownStalkerware = STALKERWARE_PREFIXES.any { pkg.startsWith(it) },
            suspiciousName = SPY_KEYWORDS.any { pkg.contains(it) },
            hiddenFromLauncher = app.packageName !in launcherPackages,
            accessibilityEnabled = app.packageName in accessibilityPackages,
            deviceAdmin = app.packageName in adminPackages,
            notificationListener = app.packageName in listenerPackages,
            sideloaded = isSideloaded(app.packageName),
            surveillancePermissions = grantedSurveillancePermissions(app.packageName)
        )
    }

    /** Pacchetti che hanno almeno un'attività visibile nel launcher. */
    private fun launcherPackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .toSet()
    }

    /**
     * Nota anti-falso-positivo: un installer `null` non conta come sideload.
     * È il caso normale di app installate via ADB, ripristinate da backup
     * al cambio telefono o preinstallate da alcuni OEM — tutti scenari
     * innocui e comunissimi. Contarli come "sideload" farebbe scattare
     * l'allarme anti-spyware su app del tutto legittime (es. una tastiera
     * ripristinata da backup, combinata col segnale "invisibile nel
     * launcher", supererebbe già da sola la soglia di segnalazione).
     */
    private fun isSideloaded(packageName: String): Boolean {
        val installer = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(packageName)
            }
        }.getOrNull()
        return installer != null && installer !in SystemAnalyzer.TRUSTED_INSTALLERS
    }

    /** Permessi di sorveglianza effettivamente concessi all'app. */
    private fun grantedSurveillancePermissions(packageName: String): List<String> {
        val info: PackageInfo = runCatching {
            pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        }.getOrNull() ?: return emptyList()

        val requested = info.requestedPermissions ?: return emptyList()
        val flags = info.requestedPermissionsFlags ?: return emptyList()

        return requested.indices
            .asSequence()
            .filter { (flags[it] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0 }
            .map { requested[it] }
            .filter { it in SURVEILLANCE_PERMISSIONS }
            .map { it.substringAfterLast('.') }
            .toList()
    }

    private fun describe(s: ThreatSignals): List<String> {
        val reasons = ArrayList<String>()
        if (s.knownStalkerware) reasons.add(context.getString(R.string.reason_stalkerware))
        if (s.hiddenFromLauncher) reasons.add(context.getString(R.string.reason_hidden))
        if (s.accessibilityEnabled) reasons.add(context.getString(R.string.reason_accessibility))
        if (s.deviceAdmin) reasons.add(context.getString(R.string.reason_device_admin))
        if (s.notificationListener) reasons.add(context.getString(R.string.reason_notification_listener))
        if (s.sideloaded) reasons.add(context.getString(R.string.reason_sideloaded))
        if (s.suspiciousName) reasons.add(context.getString(R.string.reason_suspicious_name))
        if (s.surveillancePermissions.isNotEmpty()) {
            reasons.add(
                context.getString(
                    R.string.reason_permissions,
                    s.surveillancePermissions.joinToString(", ")
                )
            )
        }
        return reasons
    }

    companion object {
        /** Punteggio minimo perché un'app venga mostrata all'utente. */
        const val REPORT_THRESHOLD = 40

        /** Punteggio da cui parte la notifica automatica del servizio. */
        const val ALERT_THRESHOLD = 60

        /**
         * Scoring puro e testabile: pesi calibrati perché nessun segnale
         * "innocuo" da solo superi la soglia di segnalazione.
         */
        fun score(s: ThreatSignals): Int {
            var value = 0
            if (s.knownStalkerware) value += 90
            if (s.hiddenFromLauncher) value += 25
            if (s.accessibilityEnabled) value += 30
            if (s.deviceAdmin) value += 25
            if (s.notificationListener) value += 20
            if (s.sideloaded) value += 15
            if (s.suspiciousName) value += 15
            value += minOf(s.surveillancePermissions.size * 4, 20)
            return minOf(value, 100)
        }

        /**
         * Prefissi di pacchetto di stalkerware commerciale documentato
         * pubblicamente (fonti: Coalition Against Stalkerware, ricerche
         * Echap/AV). Lista indicativa, non esaustiva.
         */
        val STALKERWARE_PREFIXES = setOf(
            "com.mspy", "com.flexispy", "com.spyera", "com.mobistealth",
            "com.thetruthspy", "net.thetruthspy", "com.hoverwatch",
            "com.cocospy", "com.spyzie", "com.ikeymonitor", "com.xnspy",
            "com.spyic", "com.minspy", "com.snoopza", "com.spyhuman",
            "com.letmespy", "com.copy9", "com.guestspy", "com.spyfone",
            "com.highstermobile", "com.spapp.monitoring", "com.mobiletracker",
            "com.cerberusapp"
        )

        /** Parole chiave sospette nel nome del pacchetto. */
        val SPY_KEYWORDS = setOf("spy", "stealth", "stalk", "keylog", "hiddencam")

        /** Permessi tipici della sorveglianza. */
        val SURVEILLANCE_PERMISSIONS = setOf(
            "android.permission.RECORD_AUDIO",
            "android.permission.CAMERA",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
            "android.permission.READ_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.READ_CALL_LOG",
            "android.permission.READ_CONTACTS",
            "android.permission.READ_PHONE_STATE"
        )
    }
}
