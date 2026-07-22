package com.cybersentinel.aidiag.analysis

import com.cybersentinel.aidiag.data.PrivilegedGrant

data class HeatEpisode(val startTs: Long, val endTs: Long, val maxTempCelsius: Float, val sampleCount: Int)

data class OvernightSender(val packageName: String, val appLabel: String, val totalBytes: Long, val sampleCount: Int)

data class DiagnosticReport(
    val generatedAt: Long,
    val hasBatteryHistory: Boolean,
    val hasNetworkAccess: Boolean,
    val historyHours: Float,
    val screenOffHeatEpisodes: List<HeatEpisode>,
    val overnightSenders: List<OvernightSender>,
    val idleDrainPercentPerHour: Float?,
    val recentGrants: List<PrivilegedGrant>,
    val rootIndicators: List<String>,
    val adbEnabled: Boolean,
    val developerOptionsEnabled: Boolean,
    val crossReferencedFindings: List<String>
) {
    val hasAnyAnomaly: Boolean
        get() = screenOffHeatEpisodes.isNotEmpty() || overnightSenders.isNotEmpty() ||
                recentGrants.isNotEmpty() || rootIndicators.isNotEmpty() || adbEnabled
}
