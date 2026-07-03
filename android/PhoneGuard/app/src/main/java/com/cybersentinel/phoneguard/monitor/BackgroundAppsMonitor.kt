package com.cybersentinel.phoneguard.monitor

import android.app.ActivityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import com.cybersentinel.phoneguard.data.RunningApp

/**
 * Individua le app attive in background e permette di fermarle.
 *
 * Nota di piattaforma: da Android 5.1 `getRunningAppProcesses()` restituisce
 * solo il processo della propria app, quindi non è utilizzabile per elencare
 * le altre. Usiamo invece gli eventi di utilizzo (`UsageEvents`) per due
 * segnali affidabili di attività in background:
 *  - un **servizio in background** (Foreground Service) avviato e non ancora
 *    fermato: l'app sta lavorando anche a schermo spento;
 *  - un **uso molto recente** in primo piano (ultimi minuti).
 *
 * Lo stop usa `ActivityManager.killBackgroundProcesses` (permesso
 * KILL_BACKGROUND_PROCESSES): chiede al sistema di terminare i processi in
 * background dell'app. È "best-effort" — il sistema può riavviarli — perciò
 * per una chiusura definitiva si offre anche il deep-link all'Arresto forzato.
 */
class BackgroundAppsMonitor(private val context: Context) {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    fun hasUsageAccess(): Boolean = NetworkMonitor(context).hasUsageAccess()

    fun runningApps(): List<RunningApp> {
        if (!hasUsageAccess()) return emptyList()

        val now = System.currentTimeMillis()
        val fgsPackages = activeForegroundServices(now - FGS_WINDOW_MS, now)

        // Ultimo uso in primo piano per pacchetto.
        val lastUsed = HashMap<String, Long>()
        usageStatsManager
            .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - RECENT_WINDOW_MS, now)
            .orEmpty()
            .forEach { lastUsed.merge(it.packageName, it.lastTimeUsed, ::maxOf) }

        val pm = context.packageManager
        val candidates = (fgsPackages + lastUsed.keys.filter {
            (lastUsed[it] ?: 0) >= now - RECENT_WINDOW_MS
        }).toSet()

        return candidates
            .asSequence()
            .filter { it != context.packageName }
            .mapNotNull { pkg ->
                val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
                    ?: return@mapNotNull null
                if ((info.flags and ApplicationInfo.FLAG_SYSTEM) != 0) return@mapNotNull null
                val fgs = pkg in fgsPackages
                RunningApp(
                    packageName = pkg,
                    appLabel = pm.getApplicationLabel(info).toString(),
                    reason = if (fgs) "Servizio in background attivo"
                    else "Usata di recente",
                    lastActiveMillis = lastUsed[pkg] ?: now,
                    hasForegroundService = fgs
                )
            }
            .sortedWith(
                compareByDescending<RunningApp> { it.hasForegroundService }
                    .thenByDescending { it.lastActiveMillis }
            )
            .toList()
    }

    /** Pacchetti con un Foreground Service avviato e non ancora fermato. */
    private fun activeForegroundServices(start: Long, end: Long): Set<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptySet()
        val balance = HashMap<String, Int>()
        val events: UsageEvents = usageStatsManager.queryEvents(start, end)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.FOREGROUND_SERVICE_START ->
                    balance.merge(event.packageName, 1, Int::plus)
                UsageEvents.Event.FOREGROUND_SERVICE_STOP ->
                    balance.merge(event.packageName, -1, Int::plus)
            }
        }
        return balance.filterValues { it > 0 }.keys
    }

    /** Chiede al sistema di terminare i processi in background dell'app. */
    fun stop(packageName: String) {
        runCatching { activityManager.killBackgroundProcesses(packageName) }
    }

    companion object {
        private const val FGS_WINDOW_MS = 6L * 60 * 60 * 1000   // 6 ore
        private const val RECENT_WINDOW_MS = 30L * 60 * 1000    // 30 minuti
    }
}
