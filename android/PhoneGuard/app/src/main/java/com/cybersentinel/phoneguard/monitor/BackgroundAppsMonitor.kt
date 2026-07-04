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
 * le altre. Usiamo invece gli eventi di utilizzo (`UsageEvents`) per l'unico
 * segnale davvero affidabile: un **Foreground Service** avviato e non ancora
 * fermato, cioè un'app che sta effettivamente lavorando anche a schermo
 * spento. Il semplice "uso recente" è stato scartato di proposito: non
 * indica un'attività in corso e avrebbe suggerito di fermare app innocue
 * appena chiuse dall'utente.
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

    /**
     * Elenca solo le app con un Foreground Service *genuinamente attivo*.
     *
     * Nota anti-falso-positivo: la versione iniziale includeva anche le app
     * "usate di recente" (ultimi 30 minuti). Ma l'uso recente non indica
     * un'attività in corso: un'app chiusa correttamente un minuto fa non sta
     * consumando nulla in background. Includerla con un pulsante "Ferma"
     * avrebbe indotto a chiudere app innocue (magari quella che si stava
     * usando un istante prima) senza alcun beneficio reale. L'unico segnale
     * affidabile di attività in background è il Foreground Service attivo.
     */
    fun runningApps(): List<RunningApp> {
        if (!hasUsageAccess()) return emptyList()

        val now = System.currentTimeMillis()
        val fgsPackages = activeForegroundServices(now - FGS_WINDOW_MS, now)

        val pm = context.packageManager
        return fgsPackages
            .asSequence()
            .filter { it != context.packageName }
            .mapNotNull { pkg ->
                val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
                    ?: return@mapNotNull null
                if ((info.flags and ApplicationInfo.FLAG_SYSTEM) != 0) return@mapNotNull null
                RunningApp(
                    packageName = pkg,
                    appLabel = pm.getApplicationLabel(info).toString(),
                    reason = "Servizio in background attivo",
                    lastActiveMillis = now,
                    hasForegroundService = true
                )
            }
            .sortedBy { it.appLabel.lowercase() }
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

    /**
     * Ferma tutte le app non di sistema con un Foreground Service attivo
     * (usato da "Ottimizza il cellulare" in Dashboard e Salute dispositivo).
     * Restituisce le app per cui è stato richiesto lo stop.
     */
    fun stopAll(): List<RunningApp> {
        val running = runningApps()
        running.forEach { stop(it.packageName) }
        return running
    }

    /**
     * Vero se [packageName] ha ancora un Foreground Service attivo, per
     * verificare l'esito reale di [stop]. `killBackgroundProcesses` non
     * termina i processi con un Foreground Service in corso — è protetto
     * dal sistema — quindi lo stop può risultare senza alcun effetto su
     * queste app: questo controllo lo rileva invece di darlo per scontato.
     */
    fun isForegroundServiceActive(packageName: String): Boolean =
        packageName in activePackages()

    /**
     * Pacchetti con un Foreground Service attivo in questo momento, da usare
     * per mostrare un badge "servizio attivo" per-app (es. Traffico di rete):
     * un badge persistente in lista, invece di un semplice Toast che sparisce,
     * resta visibile dopo un aggiornamento per confermare se lo stop ha
     * davvero funzionato.
     */
    fun activePackages(): Set<String> {
        val now = System.currentTimeMillis()
        return activeForegroundServices(now - FGS_WINDOW_MS, now)
    }

    companion object {
        private const val FGS_WINDOW_MS = 6L * 60 * 60 * 1000   // 6 ore
    }
}
