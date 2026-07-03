package com.cybersentinel.phoneguard.monitor

import com.cybersentinel.phoneguard.data.AnalystReport
import com.cybersentinel.phoneguard.data.AppThreat
import com.cybersentinel.phoneguard.data.RiskLevel
import com.cybersentinel.phoneguard.data.SecurityCheck

/**
 * Analista on-device: al termine della scansione legge TUTTI i risultati
 * (minacce, controlli di sistema, file sospetti, spazio recuperabile) e
 * produce un referto in linguaggio naturale con verdetto e raccomandazioni
 * ordinate per priorità.
 *
 * È un motore di ragionamento locale, non una AI in cloud: l'app non ha il
 * permesso INTERNET, quindi nessun dato lascia il telefono. La logica è
 * pura (nessuna dipendenza Android) per essere interamente testabile.
 */
object SecurityAnalyst {

    fun analyze(
        threats: List<AppThreat>,
        systemChecks: List<SecurityCheck>,
        suspiciousFiles: Int,
        reclaimableBytes: Long
    ): AnalystReport {
        val criticalThreats = threats.count { it.riskLevel == RiskLevel.CRITICO }
        val highThreats = threats.count { it.riskLevel == RiskLevel.ALTO }
        val failedChecks = systemChecks.filter { !it.ok }

        val recommendations = ArrayList<String>()

        // Priorità 1: minacce critiche (possibile spyware attivo)
        threats.filter { it.riskLevel == RiskLevel.CRITICO }.forEach {
            recommendations.add(
                "🚨 Disinstalla subito \"${it.label}\": ${it.reasons.firstOrNull() ?: "rischio critico"}."
            )
        }
        // Priorità 2: controlli di sistema falliti
        failedChecks.forEach {
            recommendations.add("⚠️ ${it.title}: ${it.detail}")
        }
        // Priorità 3: minacce ad alto rischio
        if (highThreats > 0) {
            recommendations.add(
                "🔎 $highThreats app ad alto rischio da verificare nella sezione Sicurezza."
            )
        }
        // Priorità 4: file sospetti
        if (suspiciousFiles > 0) {
            recommendations.add(
                "🗂️ $suspiciousFiles file sospetti in memoria: controllali nella sezione Sicurezza."
            )
        }
        // Priorità 5: spazio recuperabile
        if (reclaimableBytes > CLEAN_SUGGEST_THRESHOLD) {
            recommendations.add(
                "🧹 Puoi liberare ${formatSize(reclaimableBytes)} di file inutili e cache dalla sezione Energia."
            )
        }

        val verdict = when {
            criticalThreats > 0 -> RiskLevel.CRITICO
            highThreats > 0 || failedChecks.size >= 3 -> RiskLevel.ALTO
            failedChecks.isNotEmpty() || suspiciousFiles > 0 -> RiskLevel.SOSPETTO
            else -> RiskLevel.SICURO
        }

        val headline = when (verdict) {
            RiskLevel.CRITICO -> "Rilevata una minaccia critica"
            RiskLevel.ALTO -> "Il telefono richiede attenzione"
            RiskLevel.SOSPETTO -> "Alcuni punti da verificare"
            RiskLevel.SICURO -> "Telefono protetto e ottimizzato"
        }

        val summary = buildSummary(
            verdict, threats.size, criticalThreats, highThreats,
            failedChecks.size, suspiciousFiles, reclaimableBytes
        )

        if (recommendations.isEmpty()) {
            recommendations.add("✅ Nessuna azione necessaria: continua a eseguire l'Analisi Globale con regolarità.")
        }

        return AnalystReport(verdict, headline, summary, recommendations)
    }

    private fun buildSummary(
        verdict: RiskLevel,
        totalThreats: Int,
        critical: Int,
        high: Int,
        failedChecks: Int,
        suspiciousFiles: Int,
        reclaimableBytes: Long
    ): String {
        val parts = ArrayList<String>()
        parts.add(
            when (verdict) {
                RiskLevel.CRITICO ->
                    "L'analisi ha individuato $critical app ad altissimo rischio che si comportano come software spia."
                RiskLevel.ALTO ->
                    "Ho rilevato configurazioni o app che aumentano l'esposizione del telefono."
                RiskLevel.SOSPETTO ->
                    "Il quadro generale è buono, ma restano alcuni dettagli da sistemare."
                RiskLevel.SICURO ->
                    "Non ho trovato minacce né anomalie: la configurazione di sicurezza è solida."
            }
        )
        val facts = ArrayList<String>()
        if (totalThreats > 0) facts.add("$totalThreats app segnalate")
        if (failedChecks > 0) facts.add("$failedChecks controlli di sistema non superati")
        if (suspiciousFiles > 0) facts.add("$suspiciousFiles file sospetti")
        if (reclaimableBytes > 0) facts.add("${formatSize(reclaimableBytes)} recuperabili")
        if (facts.isNotEmpty()) parts.add("In sintesi: " + facts.joinToString(", ") + ".")
        return parts.joinToString(" ")
    }

    private const val CLEAN_SUGGEST_THRESHOLD = 50L * 1024 * 1024 // 50 MB

    fun formatSize(bytes: Long): String {
        val kb = 1024.0
        return when {
            bytes >= kb * kb * kb -> String.format("%.2f GB", bytes / (kb * kb * kb))
            bytes >= kb * kb -> String.format("%.1f MB", bytes / (kb * kb))
            bytes >= kb -> String.format("%.0f KB", bytes / kb)
            else -> "$bytes B"
        }
    }
}
