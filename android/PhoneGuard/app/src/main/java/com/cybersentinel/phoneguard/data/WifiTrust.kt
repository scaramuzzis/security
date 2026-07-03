package com.cybersentinel.phoneguard.data

/**
 * Valutazione di affidabilità della rete Wi-Fi a cui si è connessi.
 */
data class WifiTrust(
    val connected: Boolean,
    val ssid: String,
    val securityLabel: String,
    val isOpenNetwork: Boolean,
    val validatedInternet: Boolean,
    val captivePortal: Boolean,
    val vpnActive: Boolean,
    val proxyConfigured: Boolean,
    val privateDns: Boolean,
    val signalPercent: Int,
    val trust: RiskLevel,
    val reasons: List<String>
)
