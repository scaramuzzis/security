package com.cybersentinel.phoneguard.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.cybersentinel.phoneguard.MainActivity
import com.cybersentinel.phoneguard.R
import com.cybersentinel.phoneguard.data.AppNetworkUsage
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

    private var lastBatteryLevel = -1
    private var lastBatteryCheckTime = 0L

    override fun onCreate() {
        super.onCreate()
        networkMonitor = NetworkMonitor(this)
        batteryMonitor = BatteryMonitor(this)
        fileScanner = FileScanner(this)
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        scope.launch { monitorLoop() }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun monitorLoop() {
        while (scope.isActive) {
            runCatching { checkNetwork() }
            runCatching { checkBattery() }
            runCatching { checkFiles() }
            delay(CHECK_INTERVAL_MS)
        }
    }

    private fun checkNetwork() {
        if (!networkMonitor.hasUsageAccess()) return

        val now = System.currentTimeMillis()
        val usage = networkMonitor.queryUsage(now - CHECK_INTERVAL_MS, now)

        val suspicious = usage.filter {
            !it.isSystemApp && it.isSuspicious(TX_ALERT_THRESHOLD_BYTES)
        }
        suspicious.forEach { notifySuspiciousApp(it) }
    }

    private fun checkBattery() {
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
        private const val CHANNEL_STATUS = "phoneguard_status"
        private const val CHANNEL_ALERTS = "phoneguard_alerts"

        private const val NOTIF_ID_FOREGROUND = 1
        private const val NOTIF_ID_BATTERY = 2
        private const val NOTIF_ID_NETWORK_BASE = 1000
        private const val NOTIF_ID_FILE_BASE = 5000

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
