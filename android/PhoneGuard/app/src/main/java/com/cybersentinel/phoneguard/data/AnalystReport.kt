package com.cybersentinel.phoneguard.data

/**
 * Referto prodotto dall'analista on-device al termine della scansione:
 * un verdetto complessivo e una lista di raccomandazioni ordinate per
 * priorità.
 */
data class AnalystReport(
    val verdict: RiskLevel,
    val headline: String,
    val summary: String,
    val recommendations: List<String>
)
