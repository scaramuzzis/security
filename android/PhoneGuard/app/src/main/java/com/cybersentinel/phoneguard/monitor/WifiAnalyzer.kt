package com.cybersentinel.phoneguard.monitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.cybersentinel.phoneguard.data.RiskLevel
import com.cybersentinel.phoneguard.data.WifiTrust

/**
 * Analizza la rete Wi-Fi corrente e ne stima l'affidabilità combinando
 * più segnali: tipo di cifratura (una rete aperta è a rischio), presenza
 * di un proxy HTTP (possibile intercettazione), portale captive non ancora
 * validato, DNS privato e VPN.
 *
 * Per leggere il nome della rete (SSID) Android richiede il permesso di
 * posizione; senza, l'analisi funziona comunque ma l'SSID resta nascosto.
 */
class WifiAnalyzer(private val context: Context) {

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    fun analyze(): WifiTrust {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }

        val onWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        if (!onWifi) {
            return WifiTrust(
                connected = false, ssid = "", securityLabel = "",
                isOpenNetwork = false, validatedInternet = false,
                captivePortal = false, vpnActive = false, proxyConfigured = false,
                privateDns = false, signalPercent = 0,
                trust = RiskLevel.SICURO, reasons = emptyList()
            )
        }

        val wifiInfo = currentWifiInfo(caps)
        val ssid = readableSsid(wifiInfo)
        val (securityLabel, isOpen) = security(wifiInfo)

        val validated = caps?.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_VALIDATED
        ) == true
        val captive = caps?.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL
        ) == true
        val vpn = cm.activeNetwork?.let {
            cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } ?: false || anyVpnTransport(cm)

        val proxy = proxyConfigured()
        val privateDns = privateDnsActive()
        val signal = wifiInfo?.let { signalPercent(it) } ?: 0

        val reasons = ArrayList<String>()
        var score = 0
        if (isOpen) { reasons.add("Rete aperta, senza password: il traffico può essere intercettato"); score += 60 }
        if (proxy) { reasons.add("Proxy HTTP configurato: il traffico web passa da un intermediario"); score += 60 }
        if (securityLabel == "WEP") { reasons.add("Cifratura WEP obsoleta e violabile"); score += 50 }
        if (captive || !validated) { reasons.add("Connessione non ancora verificata (possibile portale captive)"); score += 20 }
        if (privateDns) reasons.add("DNS privato attivo (protegge le richieste DNS)")
        if (vpn) reasons.add("VPN attiva: il traffico è incapsulato")

        val trust = when {
            score >= 60 -> RiskLevel.ALTO
            score >= 20 -> RiskLevel.SOSPETTO
            else -> RiskLevel.SICURO
        }
        if (trust == RiskLevel.SICURO && reasons.isEmpty()) {
            reasons.add("Rete cifrata e connessione verificata: nessun segnale di rischio")
        }

        return WifiTrust(
            connected = true, ssid = ssid, securityLabel = securityLabel,
            isOpenNetwork = isOpen, validatedInternet = validated,
            captivePortal = captive, vpnActive = vpn, proxyConfigured = proxy,
            privateDns = privateDns, signalPercent = signal,
            trust = trust, reasons = reasons
        )
    }

    private fun currentWifiInfo(caps: NetworkCapabilities?): WifiInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (caps?.transportInfo as? WifiInfo)?.let { return it }
        }
        @Suppress("DEPRECATION")
        val wm = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        return wm.connectionInfo
    }

    private fun readableSsid(info: WifiInfo?): String {
        if (!hasLocationPermission()) return "(nome nascosto: concedi la posizione)"
        val ssid = info?.ssid?.removeSurrounding("\"") ?: return "(sconosciuto)"
        return if (ssid.isBlank() || ssid == "<unknown ssid>") "(sconosciuto)" else ssid
    }

    /** Ritorna etichetta cifratura + se è una rete aperta. */
    private fun security(info: WifiInfo?): Pair<String, Boolean> {
        if (info == null) return "sconosciuta" to false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return when (info.currentSecurityType) {
                WifiInfo.SECURITY_TYPE_OPEN -> "Aperta" to true
                WifiInfo.SECURITY_TYPE_WEP -> "WEP" to false
                WifiInfo.SECURITY_TYPE_PSK -> "WPA/WPA2" to false
                WifiInfo.SECURITY_TYPE_SAE -> "WPA3" to false
                WifiInfo.SECURITY_TYPE_EAP,
                WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE -> "Enterprise" to false
                WifiInfo.SECURITY_TYPE_UNKNOWN -> "sconosciuta" to false
                else -> "cifrata" to false
            }
        }
        // Pre-Android 12: il tipo di cifratura corrente non è esposto
        return "cifrata (tipo non rilevabile su questa versione)" to false
    }

    /**
     * Percentuale di segnale da -100dBm (assente) a -50dBm (ottimo).
     * `WifiManager.calculateSignalLevel(rssi, numLevels)` è deprecato senza
     * un sostituto che restituisca una percentuale (la versione a 1
     * argomento restituisce solo un livello 0-4): calcoliamo la percentuale
     * direttamente dal RSSI invece di dipendere da un'API deprecata.
     */
    private fun signalPercent(info: WifiInfo): Int {
        val rssi = info.rssi
        return ((rssi + 100) * 100 / 50).coerceIn(0, 100)
    }

    /**
     * `getAllNetworks()` è deprecato in favore di `NetworkCallback` per il
     * monitoraggio continuo, ma qui serve solo un controllo sincrono
     * una-tantum (nessuna registrazione/deregistrazione da gestire), quindi
     * resta la scelta più semplice e corretta per questo caso d'uso.
     */
    @Suppress("DEPRECATION")
    private fun anyVpnTransport(cm: ConnectivityManager): Boolean =
        cm.allNetworks.any { n ->
            cm.getNetworkCapabilities(n)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }

    private fun proxyConfigured(): Boolean {
        val proxy = System.getProperty("http.proxyHost")
        if (!proxy.isNullOrBlank()) return true
        val global = Settings.Global.getString(
            context.contentResolver, Settings.Global.HTTP_PROXY
        )
        return !global.isNullOrBlank() && global != ":0"
    }

    private fun privateDnsActive(): Boolean {
        val mode = Settings.Global.getString(context.contentResolver, "private_dns_mode")
        return mode != null && mode != "off"
    }
}
