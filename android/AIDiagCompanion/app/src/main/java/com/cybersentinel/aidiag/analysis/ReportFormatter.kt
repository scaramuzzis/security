package com.cybersentinel.aidiag.analysis

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Formatta il report in linguaggio semplice, SENZA alcuna IA: deve restare
 * comprensibile e utile da solo. La sintesi IA (se configurata) si aggiunge
 * sopra questo report, non lo sostituisce — così l'attendibilità dell'app
 * non dipende dal servizio esterno.
 */
object ReportFormatter {

    private val formatter = SimpleDateFormat("dd/MM HH:mm", Locale.ITALY)

    fun format(report: DiagnosticReport): String {
        val lines = ArrayList<String>()

        lines.add("📊 Storico raccolto: ${"%.1f".format(report.historyHours)} ore.")
        if (report.historyHours < 6f) {
            lines.add("⏳ Poco storico ancora raccolto: i risultati diventano più affidabili dopo qualche ora/giorno di funzionamento in background.")
        }
        if (!report.hasNetworkAccess) {
            lines.add("❌ Accesso ai dati di utilizzo non concesso: il traffico di rete non è stato analizzato.")
        }

        lines.add("")
        lines.add(
            if (report.screenOffHeatEpisodes.isEmpty()) "✅ Nessun surriscaldamento a schermo spento rilevato."
            else "⚠️ Surriscaldamento a schermo spento: ${report.screenOffHeatEpisodes.size} episodi rilevati."
        )
        report.screenOffHeatEpisodes.take(5).forEach {
            lines.add("   • ${formatter.format(Date(it.startTs))}–${formatter.format(Date(it.endTs))}: punta ${it.maxTempCelsius}°C (${it.sampleCount} rilevazioni)")
        }

        lines.add("")
        lines.add(
            report.idleDrainPercentPerHour?.let { "🔋 Scarica media a schermo spento: ${"%.1f".format(it)}%/ora." }
                ?: "🔋 Scarica a schermo spento: dati insufficienti per stimarla."
        )

        lines.add("")
        lines.add(
            if (report.overnightSenders.isEmpty()) "✅ Nessuna app invia dati con regolarità a schermo spento."
            else "⚠️ App che inviano dati con regolarità a schermo spento:"
        )
        report.overnightSenders.take(10).forEach {
            lines.add("   • ${it.appLabel}: ${it.sampleCount} rilevazioni, ${it.totalBytes} byte totali")
        }

        lines.add("")
        lines.add(
            if (report.recentGrants.isEmpty()) "✅ Nessun permesso privilegiato concesso negli ultimi 14 giorni."
            else "⚠️ Permessi privilegiati concessi di recente:"
        )
        report.recentGrants.forEach {
            lines.add("   • ${it.appLabel}: ${it.kind} (dal ${formatter.format(Date(it.firstSeen))})")
        }

        lines.add("")
        lines.add(if (report.rootIndicators.isEmpty()) "✅ Nessun indizio di root." else "⚠️ Indizi di root: ${report.rootIndicators.joinToString(", ")}")
        lines.add(if (report.adbEnabled) "⚠️ Debug USB (ADB) attivo." else "✅ Debug USB disattivato.")
        lines.add(if (report.developerOptionsEnabled) "ℹ️ Opzioni sviluppatore attive." else "✅ Opzioni sviluppatore disattivate.")

        if (report.crossReferencedFindings.isNotEmpty()) {
            lines.add("")
            lines.add("🔗 Correlazioni fra più segnali:")
            report.crossReferencedFindings.forEach { lines.add("   • $it") }
        }

        return lines.joinToString("\n")
    }
}
