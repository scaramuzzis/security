package com.cybersentinel.aidiag.ai

import com.cybersentinel.aidiag.analysis.DiagnosticReport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Costruisce il prompt per l'IA a partire dal report strutturato: solo dati
 * già raccolti localmente (nessuna telemetria aggiuntiva), con istruzioni
 * esplicite a non essere allarmista e a citare spiegazioni innocue quando
 * plausibili — stesso principio "indizio, non prova" di PhoneGuard.
 */
object PromptBuilder {

    private val formatter = SimpleDateFormat("dd/MM HH:mm", Locale.ITALY)

    fun build(report: DiagnosticReport): String {
        val sb = StringBuilder()
        sb.append(
            "Sei un analista di sicurezza mobile. Ricevi dati grezzi raccolti localmente da un " +
                    "telefono Android (nessun contenuto di messaggi o file, solo metadati di sistema: " +
                    "batteria, traffico dati per app, permessi privilegiati). Il tuo compito è scrivere " +
                    "una sintesi in italiano, chiara e onesta, per una persona non tecnica preoccupata " +
                    "di essere sorvegliata.\n\n" +
                    "Regole importanti:\n" +
                    "- Non affermare mai con certezza che il telefono è compromesso o sicuro: questi dati " +
                    "sono indizi, non prove. Un'app che invia dati di notte è spesso solo una sincronizzazione " +
                    "o un backup schedulato, non spyware.\n" +
                    "- Per ogni segnale, indica ESPLICITAMENTE se esiste una spiegazione innocua plausibile.\n" +
                    "- Dai un livello di preoccupazione complessivo: Basso, Medio o Alto, con la motivazione.\n" +
                    "- Se i dati raccolti sono pochi (poche ore di storico), dillo chiaramente e invita a " +
                    "ripetere il controllo dopo che l'app ha raccolto più storico.\n" +
                    "- Rispondi in italiano, in un massimo di 300 parole, senza markdown.\n\n" +
                    "--- DATI RACCOLTI ---\n"
        )

        sb.append("Periodo analizzato: ${"%.1f".format(report.historyHours)} ore di storico raccolto.\n")
        sb.append("Accesso ai dati di utilizzo di rete: ${if (report.hasNetworkAccess) "concesso" else "NON concesso — traffico dati non analizzato"}.\n\n")

        sb.append("Surriscaldamento a schermo spento: ")
        if (report.screenOffHeatEpisodes.isEmpty()) sb.append("nessun episodio rilevato.\n")
        else {
            sb.append("${report.screenOffHeatEpisodes.size} episodi. ")
            report.screenOffHeatEpisodes.take(5).forEach {
                sb.append("[${formatter.format(Date(it.startTs))}-${formatter.format(Date(it.endTs))}, punta ${it.maxTempCelsius}°C, ${it.sampleCount} rilevazioni] ")
            }
            sb.append("\n")
        }

        sb.append("Scarica batteria media a schermo spento (non in carica): ")
        sb.append(report.idleDrainPercentPerHour?.let { "${"%.1f".format(it)}%/ora.\n" } ?: "dati insufficienti.\n")

        sb.append("App che inviano dati con lo schermo spento, con regolarità: ")
        if (report.overnightSenders.isEmpty()) sb.append("nessuna.\n")
        else {
            sb.append("\n")
            report.overnightSenders.take(10).forEach {
                sb.append("- ${it.appLabel} (${it.packageName}): ${it.totalBytes} byte in ${it.sampleCount} rilevazioni notturne\n")
            }
        }

        sb.append("\nPermessi privilegiati concessi negli ultimi 14 giorni: ")
        if (report.recentGrants.isEmpty()) sb.append("nessuno.\n")
        else {
            sb.append("\n")
            report.recentGrants.forEach {
                sb.append("- ${it.appLabel}: ${it.kind} (da ${formatter.format(Date(it.firstSeen))})\n")
            }
        }

        sb.append("\nIndizi di root: ${if (report.rootIndicators.isEmpty()) "nessuno" else report.rootIndicators.joinToString(", ")}\n")
        sb.append("Debug USB (ADB) attivo: ${if (report.adbEnabled) "sì" else "no"}\n")
        sb.append("Opzioni sviluppatore attive: ${if (report.developerOptionsEnabled) "sì" else "no"}\n")

        if (report.crossReferencedFindings.isNotEmpty()) {
            sb.append("\nCorrelazioni già individuate dal sistema:\n")
            report.crossReferencedFindings.forEach { sb.append("- $it\n") }
        }

        return sb.toString()
    }
}
