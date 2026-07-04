package com.cybersentinel.aidiag.analysis

import android.content.Context
import com.cybersentinel.aidiag.db.SignalStore
import com.cybersentinel.aidiag.monitor.NetworkReader
import com.cybersentinel.aidiag.monitor.SystemChecks

/** Assembla il [DiagnosticReport] leggendo la cronologia salvata + i controlli puntuali di sistema. */
class DiagnosticEngine(private val context: Context) {

    fun buildReport(): DiagnosticReport {
        val store = SignalStore.get(context)
        val now = System.currentTimeMillis()
        val since = now - HISTORY_WINDOW_MS

        val batterySamples = store.batterySamplesSince(since)
        val networkSamples = store.networkSamplesSince(since)
        val grants = store.allGrants()

        val heatEpisodes = DiagnosticAnalyzer.findScreenOffHeatEpisodes(batterySamples)
        val overnightSenders = DiagnosticAnalyzer.findOvernightSenders(networkSamples)
        val idleDrain = DiagnosticAnalyzer.estimateIdleDrainPerHour(batterySamples)
        val recentGrants = DiagnosticAnalyzer.recentGrants(grants, now)
        val crossReferenced = DiagnosticAnalyzer.crossReference(overnightSenders, recentGrants, heatEpisodes)

        val systemChecks = SystemChecks(context)
        val historyHours = if (batterySamples.isEmpty()) 0f
        else (now - batterySamples.minOf { it.timestamp }) / 3_600_000f

        return DiagnosticReport(
            generatedAt = now,
            hasBatteryHistory = batterySamples.isNotEmpty(),
            hasNetworkAccess = NetworkReader(context).hasUsageAccess(),
            historyHours = historyHours,
            screenOffHeatEpisodes = heatEpisodes,
            overnightSenders = overnightSenders,
            idleDrainPercentPerHour = idleDrain,
            recentGrants = recentGrants,
            rootIndicators = systemChecks.rootIndicators(),
            adbEnabled = systemChecks.isAdbEnabled(),
            developerOptionsEnabled = systemChecks.isDeveloperOptionsEnabled(),
            crossReferencedFindings = crossReferenced
        )
    }

    companion object {
        /** Analizza fino a 7 giorni indietro (limite di ritenzione di SignalStore). */
        private const val HISTORY_WINDOW_MS = 7L * 24 * 60 * 60 * 1000
    }
}
