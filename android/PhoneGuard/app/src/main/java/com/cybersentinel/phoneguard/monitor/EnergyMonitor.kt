package com.cybersentinel.phoneguard.monitor

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import com.cybersentinel.phoneguard.data.AppEnergyUsage

/**
 * Classifica le app per impatto energetico stimato.
 *
 * Il consumo reale in mAh per-app (BatteryStatsManager) richiede il permesso
 * di sistema BATTERY_STATS, non disponibile alle app normali: la stima usa
 * il tempo in primo piano da UsageStatsManager come proxy dell'impatto,
 * arricchito dal rilevamento dei Foreground Service (i processi che
 * continuano a lavorare — e consumare — anche a schermo spento).
 *
 * Richiede lo stesso permesso "Accesso ai dati di utilizzo" del monitor
 * di rete.
 */
class EnergyMonitor(private val context: Context) {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    /**
     * App più energivore nell'intervallo, ordinate per impatto decrescente.
     */
    fun topConsumers(windowMillis: Long = DEFAULT_WINDOW_MS): List<AppEnergyUsage> {
        val end = System.currentTimeMillis()
        val start = end - windowMillis

        // queryUsageStats può restituire più bucket per pacchetto: aggrego.
        val foregroundByPackage = HashMap<String, Long>()
        usageStatsManager
            .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            .orEmpty()
            .forEach { stat ->
                if (stat.totalTimeInForeground > 0) {
                    foregroundByPackage.merge(
                        stat.packageName, stat.totalTimeInForeground, Long::plus
                    )
                }
            }

        val totalForeground = foregroundByPackage.values.sum().coerceAtLeast(1)
        val fgsPackages = foregroundServicePackages(start, end)
        val pm = context.packageManager

        return foregroundByPackage.entries
            .asSequence()
            .filter { it.value >= MIN_FOREGROUND_MS || it.key in fgsPackages }
            .mapNotNull { (packageName, foregroundMs) ->
                val info = runCatching {
                    pm.getApplicationInfo(packageName, 0)
                }.getOrNull() ?: return@mapNotNull null
                AppEnergyUsage(
                    packageName = packageName,
                    appLabel = pm.getApplicationLabel(info).toString(),
                    foregroundMillis = foregroundMs,
                    usedForegroundService = packageName in fgsPackages,
                    impactPercent = (foregroundMs * 100 / totalForeground).toInt(),
                    isSystemApp = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
            .sortedByDescending { it.foregroundMillis }
            .take(MAX_RESULTS)
            .toList()
    }

    /**
     * Pacchetti che hanno avviato un Foreground Service nell'intervallo
     * (lavoro in background prolungato: musica, tracking, sync, spyware...).
     * Gli eventi FGS sono esposti da UsageEvents a partire da Android 10.
     */
    private fun foregroundServicePackages(start: Long, end: Long): Set<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptySet()

        val packages = HashSet<String>()
        val events: UsageEvents = usageStatsManager.queryEvents(start, end)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.FOREGROUND_SERVICE_START) {
                packages.add(event.packageName)
            }
        }
        return packages
    }

    companion object {
        /** Finestra di osservazione predefinita: 24 ore. */
        const val DEFAULT_WINDOW_MS = 24L * 60 * 60 * 1000

        /** Sotto il minuto di primo piano l'impatto è trascurabile. */
        private const val MIN_FOREGROUND_MS = 60_000L

        private const val MAX_RESULTS = 30
    }
}
