package com.cybersentinel.phoneguard.data

/**
 * Impatto energetico stimato di un'app nell'intervallo osservato.
 *
 * Nota tecnica: il consumo reale in mAh per-app (BatteryStats) è riservato
 * alle app di sistema; la stima usa il tempo in primo piano da
 * UsageStatsManager come proxy documentato dell'impatto.
 */
data class AppEnergyUsage(
    val packageName: String,
    val appLabel: String,
    val foregroundMillis: Long,
    val usedForegroundService: Boolean,
    val impactPercent: Int,
    val isSystemApp: Boolean
)
