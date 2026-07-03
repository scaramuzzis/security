package com.cybersentinel.phoneguard.data

/**
 * Segnali di rischio raccolti su una singola app installata.
 * Struttura pura (senza dipendenze Android) per poter testare lo scoring.
 */
data class ThreatSignals(
    val knownStalkerware: Boolean = false,
    val suspiciousName: Boolean = false,
    val hiddenFromLauncher: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val deviceAdmin: Boolean = false,
    val notificationListener: Boolean = false,
    val sideloaded: Boolean = false,
    val surveillancePermissions: List<String> = emptyList()
)

enum class RiskLevel { SOSPETTO, ALTO, CRITICO }

/**
 * App segnalata dalla scansione anti-spyware, con punteggio di rischio
 * e l'elenco leggibile dei motivi della segnalazione.
 */
data class AppThreat(
    val packageName: String,
    val label: String,
    val signals: ThreatSignals,
    val score: Int,
    val reasons: List<String>
) {
    val riskLevel: RiskLevel
        get() = when {
            score >= 80 -> RiskLevel.CRITICO
            score >= 60 -> RiskLevel.ALTO
            else -> RiskLevel.SOSPETTO
        }
}
