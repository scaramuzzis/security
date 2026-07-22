package com.cybersentinel.aidiag.monitor

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
import com.cybersentinel.aidiag.MainActivity
import com.cybersentinel.aidiag.R
import com.cybersentinel.aidiag.data.GrantKind
import com.cybersentinel.aidiag.db.SignalStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Servizio in foreground che campiona periodicamente batteria/schermo,
 * traffico di rete e permessi privilegiati, per costruire nel tempo la
 * cronologia su cui si basa il controllo IA — un singolo controllo puntuale
 * non può accorgersi che "il telefono scalda a schermo spento" o che "un
 * servizio di accessibilità è comparso ieri notte": serve una storia.
 */
class SamplingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        scope.launch { loop() }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun loop() {
        val batteryReader = BatteryReader(this)
        val networkReader = NetworkReader(this)
        val privilegedScanner = PrivilegedAppsScanner(this)
        val store = SignalStore.get(this)
        var lastNetworkCheck = System.currentTimeMillis()

        while (scope.isActive) {
            runCatching { store.insertBatterySample(batteryReader.sample()) }

            runCatching {
                val now = System.currentTimeMillis()
                val samples = networkReader.sampleSince(lastNetworkCheck, now)
                store.insertNetworkSamples(samples)
                lastNetworkCheck = now
            }

            runCatching {
                val now = System.currentTimeMillis()
                recordGrants(store, privilegedScanner, GrantKind.DEVICE_ADMIN, privilegedScanner.deviceAdmins(), now)
                recordGrants(store, privilegedScanner, GrantKind.ACCESSIBILITY, privilegedScanner.accessibilityServices(), now)
                recordGrants(store, privilegedScanner, GrantKind.NOTIFICATION_LISTENER, privilegedScanner.notificationListeners(), now)
            }

            delay(SAMPLE_INTERVAL_MS)
        }
    }

    private fun recordGrants(
        store: SignalStore,
        scanner: PrivilegedAppsScanner,
        kind: GrantKind,
        currentPackages: Set<String>,
        now: Long
    ) {
        currentPackages.forEach { pkg -> store.recordGrantIfNew(pkg, scanner.appLabel(pkg), kind, now) }
        store.pruneGrants(kind, currentPackages)
    }

    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_diag)
            .setContentTitle(getString(R.string.sampling_notification_title))
            .setContentText(getString(R.string.sampling_notification_text))
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setOngoing(true)
            .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        private const val CHANNEL_ID = "aidiag_status"
        private const val NOTIF_ID = 1

        /** Ogni quanto campionare (15 minuti, stesso intervallo di PhoneGuard). */
        const val SAMPLE_INTERVAL_MS = 15L * 60 * 1000

        fun start(context: Context) {
            androidx.core.content.ContextCompat.startForegroundService(
                context, Intent(context, SamplingService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SamplingService::class.java))
        }
    }
}
