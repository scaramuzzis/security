package com.cybersentinel.phoneguard.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.cybersentinel.phoneguard.R
import com.cybersentinel.phoneguard.data.RiskLevel
import com.cybersentinel.phoneguard.monitor.CellNetworkMonitor
import com.cybersentinel.phoneguard.monitor.SystemAnalyzer
import com.cybersentinel.phoneguard.monitor.ThreatScanner
import com.cybersentinel.phoneguard.monitor.WifiAnalyzer
import com.cybersentinel.phoneguard.util.SystemIntents
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pagina "Verifica intercettazione": aggrega in un unico verdetto tutti i
 * segnali già raccolti dagli altri motori (app spia, controllo remoto,
 * rete cellulare, Wi-Fi) e offre le scorciatoie ai codici USSD ufficiali
 * per controllare la deviazione delle chiamate — l'unico modo in cui
 * un'app senza privilegi di operatore può verificarla, perché a risponderti
 * è direttamente la rete del tuo operatore, non una stima dell'app.
 *
 * Limite onesto, dichiarato anche in pagina: nessuna app di terze parti può
 * rilevare con certezza un'intercettazione a livello di rete/baseband
 * (IMSI-catcher, intercettazione dell'operatore o di un'autorità). Questi
 * sono indizi euristici concreti, non una prova definitiva.
 */
class InterceptionFragment : Fragment(R.layout.fragment_interception) {

    private lateinit var verdict: TextView
    private lateinit var checksText: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        verdict = view.findViewById(R.id.interceptionVerdict)
        checksText = view.findViewById(R.id.interceptionChecks)

        view.findViewById<MaterialButton>(R.id.ussdForwardAll).setOnClickListener {
            SystemIntents.dialUssd(requireContext(), "*#21#")
        }
        view.findViewById<MaterialButton>(R.id.ussdForwardBusy).setOnClickListener {
            SystemIntents.dialUssd(requireContext(), "*#67#")
        }
        view.findViewById<MaterialButton>(R.id.ussdForwardUnreachable).setOnClickListener {
            SystemIntents.dialUssd(requireContext(), "*#62#")
        }
        view.findViewById<MaterialButton>(R.id.ussdDisableAll).setOnClickListener {
            SystemIntents.dialUssd(requireContext(), "##002#")
        }
        view.findViewById<MaterialButton>(R.id.ussdImei).setOnClickListener {
            SystemIntents.dialUssd(requireContext(), "*#06#")
        }
    }

    override fun onResume() {
        super.onResume()
        val context = requireContext()

        viewLifecycleOwner.lifecycleScope.launch {
            val threats = withContext(Dispatchers.Default) { ThreatScanner(context).scan() }
            val systemChecks = withContext(Dispatchers.Default) { SystemAnalyzer(context).analyze() }
            val cell = withContext(Dispatchers.Default) { CellNetworkMonitor(context).analyze() }
            val wifi = withContext(Dispatchers.Default) { WifiAnalyzer(context).analyze() }

            val criticalThreats = threats.count { it.riskLevel == RiskLevel.CRITICO }
            val relevantTitles = setOf(
                "Amministratori dispositivo", "Servizi di accessibilità",
                "Lettura notifiche", "Gestione remota (MDM)", "Root"
            )
            val relevantChecks = systemChecks.filter { it.title in relevantTitles }
            val failedRelevant = relevantChecks.count { !it.ok }

            val lines = ArrayList<String>()
            lines.add(
                if (criticalThreats > 0)
                    "🚨 $criticalThreats app rilevate con comportamento da spyware (dettagli nella pagina \"App sospette e nascoste\")."
                else "✅ Nessuna app spia rilevata."
            )
            relevantChecks.forEach { check ->
                lines.add("${if (check.ok) "✅" else "⚠️"} ${check.title}: ${check.detail}")
            }
            if (cell.hasReadPermission) {
                lines.add("${if (cell.isInsecure2g) "⚠️" else "✅"} ${cell.detail}")
            } else {
                lines.add("ℹ️ Concedi il permesso telefono per controllare anche la rete cellulare (2G/4G/5G).")
            }
            if (wifi.connected) {
                val wifiIcon = if (wifi.trust == RiskLevel.SICURO) "✅" else "⚠️"
                lines.add("$wifiIcon Wi-Fi \"${wifi.ssid}\": ${wifi.reasons.firstOrNull() ?: "verificato"} (dettagli in \"Affidabilità Wi-Fi\").")
            }
            checksText.text = lines.joinToString("\n\n")

            val critical = criticalThreats > 0
            val warning = failedRelevant > 0 || cell.isInsecure2g ||
                    (wifi.connected && wifi.trust != RiskLevel.SICURO)
            val (icon, textRes, colorRes) = when {
                critical -> Triple("🚨", R.string.interception_critical, R.color.status_danger)
                warning -> Triple("⚠️", R.string.interception_warning, R.color.status_warn)
                else -> Triple("🛡️", R.string.interception_safe, R.color.status_ok)
            }
            verdict.text = "$icon ${getString(textRes)}"
            verdict.setTextColor(ContextCompat.getColor(context, colorRes))
        }
    }
}
