package com.cybersentinel.phoneguard.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.cybersentinel.phoneguard.MainActivity
import com.cybersentinel.phoneguard.R
import com.cybersentinel.phoneguard.data.AppNetworkUsage
import com.cybersentinel.phoneguard.data.Prefs
import com.cybersentinel.phoneguard.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Servizio in foreground che, a intervalli regolari:
 *  1. controlla quali app hanno inviato dati nell'ultimo intervallo
 *     e avvisa con una notifica se un'app supera la soglia di upload;
 *  2. controlla lo stato della batteria e avvisa in caso di
 *     surriscaldamento o scarica anomala.
 */
class MonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var batteryMonitor: BatteryMonitor
    private lateinit var fileScanner: FileScanner
    private lateinit var threatScanner: ThreatScanner

    private var lastBatteryLevel = -1
    private var lastBatteryCheckTime = 0L

    override fun onCreate() {
        super.onCreate()
        networkMonitor = NetworkMonitor(this)
        batteryMonitor = BatteryMonitor(this)
        fileScanner = FileScanner(this)
        threatScanner = ThreatScanner(this)
        createChannels()
        AppLog.log(this, "SERVIZIO", "Servizio di monitoraggio avviato")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        scope.launch { monitorLoop() }
        scope.launch { appStartLoop() }
        return START_STICKY
    }

    override fun onDestroy() {
        AppLog.log(this, "SERVIZIO", "Servizio di monitoraggio fermato")
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun monitorLoop() {
        while (scope.isActive) {
            runCatching { checkNetwork() }
            runCatching { checkBattery() }
            runCatching { checkFiles() }
            runCatching { checkThreats() }
            delay(CHECK_INTERVAL_MS)
        }
    }

    private fun checkNetwork() {
        if (!networkMonitor.hasUsageAccess()) return

        val now = System.currentTimeMillis()
        val usage = networkMonitor.queryUsage(now - CHECK_INTERVAL_MS, now)

        if (Prefs.networkAlertsEnabled(this)) {
            val suspicious = usage.filter {
                !it.isSystemApp && it.isSuspicious(TX_ALERT_THRESHOLD_BYTES)
            }
            suspicious.forEach { notifySuspiciousApp(it) }
        }

        logNetworkSummary(usage)
    }

    /**
     * Registra nel log, in linguaggio semplice, quali app hanno inviato
     * dati in rete in quest'ultimo intervallo — indipendentemente dagli
     * avvisi (quelli scattano solo sopra soglia). Se il registro attività
     * è disattivato, AppLog.log() non scrive nulla: nessun costo extra.
     */
    private fun logNetworkSummary(usage: List<AppNetworkUsage>) {
        explainNetworkLogOnce()

        val senders = usage.filter { !it.isSystemApp && it.txBytes > 0 }
            .sortedByDescending { it.txBytes }
            .take(5)
        if (senders.isEmpty()) return

        val lines = senders.joinToString("; ") { app ->
            "${app.appLabel}: ↑${SecurityAnalyst.formatSize(app.txBytes)} inviati, " +
                    "↓${SecurityAnalyst.formatSize(app.rxBytes)} ricevuti"
        }
        AppLog.log(
            this, "RETE",
            "Negli ultimi 15 minuti le app che hanno inviato più dati sono: $lines."
        )
    }

    /** Spiegazione dei limiti del log di rete, registrata una sola volta. */
    private fun explainNetworkLogOnce() {
        if (Prefs.networkLogExplained(this)) return
        Prefs.setNetworkLogExplained(this, true)
        AppLog.log(
            this, "RETE",
            "Cosa mostra questo registro: PhoneGuard vede quanti byte ogni app invia e riceve " +
                    "in totale (upload/download), ma NON può vedere il contenuto dei dati, " +
                    "verso quale sito o server vengono inviati, né in che formato — Android non lo " +
                    "permette a un'app senza privilegi speciali. Se un'app invia molti più dati di " +
                    "quanti dovrebbe (es. una torcia che invia MB di dati), è un segnale sospetto " +
                    "anche senza sapere cosa contengono esattamente quei dati."
        )
    }

    private fun checkBattery() {
        if (!Prefs.batteryAlertsEnabled(this)) return
        val snap = batteryMonitor.snapshot()
        val now = System.currentTimeMillis()

        if (snap.temperatureCelsius >= TEMPERATURE_ALERT_CELSIUS) {
            notifyAlert(
                NOTIF_ID_BATTERY,
                getString(R.string.alert_battery_title),
                getString(R.string.alert_overheat, snap.temperatureCelsius)
            )
        }

        if (lastBatteryLevel >= 0 && !snap.isCharging && lastBatteryCheckTime > 0) {
            val elapsedMinutes = (now - lastBatteryCheckTime) / 60_000f
            val drop = lastBatteryLevel - snap.levelPercent
            if (elapsedMinutes > 0) {
                val dropPerHour = drop / elapsedMinutes * 60f
                if (dropPerHour >= DRAIN_ALERT_PERCENT_PER_HOUR) {
                    notifyAlert(
                        NOTIF_ID_BATTERY,
                        getString(R.string.alert_battery_title),
                        getString(R.string.alert_drain, dropPerHour)
                    )
                }
            }
        }

        lastBatteryLevel = snap.levelPercent
        lastBatteryCheckTime = now
    }

    /**
     * Segnala i file sospetti comparsi (creati o modificati)
     * nell'ultimo intervallo di controllo.
     */
    private fun checkFiles() {
        if (!Prefs.autoFileScanEnabled(this)) return
        if (!fileScanner.hasStorageAccess()) return

        val cutoff = System.currentTimeMillis() - CHECK_INTERVAL_MS
        val recent = fileScanner.scan()
            .filter { it.lastModified >= cutoff }
            .take(3) // al massimo 3 notifiche per intervallo

        recent.forEachIndexed { index, file ->
            notifyAlert(
                NOTIF_ID_FILE_BASE + index,
                getString(R.string.alert_file_title),
                getString(R.string.alert_file, file.path, file.reason)
            )
        }
    }

    /**
     * Loop veloce (1 minuto): rileva le app che si avviano da sole.
     *
     * Euristica: un Foreground Service partito senza che l'utente abbia
     * aperto quell'app negli ultimi 10 minuti = avvio autonomo in
     * background. Notifica heads-up (popup) tramite il canale ad alta
     * priorità, con de-duplica di 1 ora per app.
     */
    private suspend fun appStartLoop() {
        while (scope.isActive) {
            runCatching { checkAppStarts() }
            delay(APP_START_INTERVAL_MS)
        }
    }

    private val notifiedAppStarts = HashMap<String, Long>()

    private fun checkAppStarts() {
        if (!Prefs.appStartAlertsEnabled(this)) return
        if (!networkMonitor.hasUsageAccess()) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        val now = System.currentTimeMillis()
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(now - USER_LAUNCH_WINDOW_MS, now)

        val userLaunched = HashSet<String>()
        val backgroundStarts = HashSet<String>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                // L'utente ha portato l'app in primo piano: avvio legittimo
                UsageEvents.Event.ACTIVITY_RESUMED ->
                    userLaunched.add(event.packageName)
                // Servizio partito: se recente e non preceduto da un avvio
                // dell'utente, è un avvio autonomo
                UsageEvents.Event.FOREGROUND_SERVICE_START ->
                    if (event.timeStamp >= now - APP_START_INTERVAL_MS - 5_000) {
                        backgroundStarts.add(event.packageName)
                    }
            }
        }

        backgroundStarts
            .asSequence()
            .filter { it != packageName && it !in userLaunched }
            .filter { pkg ->
                val last = notifiedAppStarts[pkg]
                last == null || now - last > APP_START_DEDUPE_MS
            }
            .forEach { pkg ->
                val info = runCatching {
                    packageManager.getApplicationInfo(pkg, 0)
                }.getOrNull() ?: return@forEach
                if ((info.flags and ApplicationInfo.FLAG_SYSTEM) != 0) return@forEach

                notifiedAppStarts[pkg] = now
                val label = packageManager.getApplicationLabel(info).toString()
                notifyAlert(
                    NOTIF_ID_APPSTART_BASE + (pkg.hashCode() and 0xFF),
                    getString(R.string.alert_appstart_title),
                    getString(R.string.alert_appstart, label)
                )
            }
    }

    /**
     * Scansione anti-spyware: notifica le app ad alto rischio una sola
     * volta per pacchetto (l'elenco dei già segnalati è persistito).
     */
    private fun checkThreats() {
        if (!Prefs.threatScanEnabled(this)) return
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val alreadyNotified =
            prefs.getStringSet(KEY_NOTIFIED_THREATS, emptySet()).orEmpty()

        val highRisk = threatScanner.scan()
            .filter { it.score >= ThreatScanner.ALERT_THRESHOLD }

        val newThreats = highRisk.filter { it.packageName !in alreadyNotified }
        newThreats.forEachIndexed { index, threat ->
            notifyAlert(
                NOTIF_ID_THREAT_BASE + index,
                getString(R.string.alert_threat_title),
                getString(
                    R.string.alert_threat, threat.label, threat.score,
                    threat.reasons.joinToString("; ")
                )
            )
        }

        // Persiste solo i pacchetti ancora installati: se un'app segnalata
        // viene rimossa e reinstallata, torna a generare l'avviso.
        prefs.edit()
            .putStringSet(
                KEY_NOTIFIED_THREATS,
                highRisk.map { it.packageName }.toSet()
            )
            .apply()
    }

    private fun notifySuspiciousApp(app: AppNetworkUsage) {
        val mb = app.txBytes / (1024.0 * 1024.0)
        notifyAlert(
            NOTIF_ID_NETWORK_BASE + app.uid,
            getString(R.string.alert_network_title),
            getString(R.string.alert_upload, app.appLabel, mb)
        )
    }

    private fun startInForeground() {
        val notification = baseNotification(
            getString(R.string.service_running_title),
            getString(R.string.service_running_text),
            CHANNEL_STATUS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID_FOREGROUND,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIF_ID_FOREGROUND, notification)
        }
    }

    private fun notifyAlert(id: Int, title: String, text: String) {
        AppLog.log(this, "AVVISO", "$title — $text")
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(id, baseNotification(title, text, CHANNEL_ALERTS))
    }

    private fun baseNotification(title: String, text: String, channel: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createChannels() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATUS,
                getString(R.string.channel_status),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                getString(R.string.channel_alerts),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    companion object {

        fun start(context: Context) {
            val intent = Intent(context, MonitorService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
        }

        private const val CHANNEL_STATUS = "phoneguard_status"
        private const val CHANNEL_ALERTS = "phoneguard_alerts"

        private const val NOTIF_ID_FOREGROUND = 1
        private const val NOTIF_ID_BATTERY = 2
        private const val NOTIF_ID_NETWORK_BASE = 1000
        private const val NOTIF_ID_FILE_BASE = 5000
        private const val NOTIF_ID_THREAT_BASE = 8000
        private const val NOTIF_ID_APPSTART_BASE = 12000

        /** Controllo avvii in background: ogni minuto. */
        private const val APP_START_INTERVAL_MS = 60_000L

        /** Un avvio è "dell'utente" se ha aperto l'app negli ultimi 10 min. */
        private const val USER_LAUNCH_WINDOW_MS = 10L * 60 * 1000

        /** Non ri-notificare la stessa app per un'ora. */
        private const val APP_START_DEDUPE_MS = 60L * 60 * 1000

        private const val PREFS_NAME = "phoneguard"
        private const val KEY_NOTIFIED_THREATS = "notified_threats"

        /** Ogni quanto eseguire i controlli (15 minuti). */
        const val CHECK_INTERVAL_MS = 15L * 60 * 1000

        /** Upload oltre 50 MB in un intervallo => app segnalata. */
        const val TX_ALERT_THRESHOLD_BYTES = 50L * 1024 * 1024

        /** Temperatura batteria oltre 45 °C => avviso. */
        const val TEMPERATURE_ALERT_CELSIUS = 45f

        /** Scarica oltre il 20%/ora (senza caricabatterie) => avviso. */
        const val DRAIN_ALERT_PERCENT_PER_HOUR = 20f
    }
}
