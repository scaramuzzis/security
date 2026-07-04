package com.cybersentinel.phoneguard.ui

import android.Manifest
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.cybersentinel.phoneguard.R
import com.cybersentinel.phoneguard.data.RiskLevel
import com.cybersentinel.phoneguard.data.WifiTrust
import com.cybersentinel.phoneguard.monitor.WifiAnalyzer
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pagina Affidabilità Wi-Fi: cifratura, proxy, captive portal, DNS, VPN. */
class WifiFragment : Fragment(R.layout.fragment_wifi) {

    private lateinit var wifiVerdict: TextView
    private lateinit var wifiText: TextView
    private lateinit var wifiLocationButton: MaterialButton

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        wifiVerdict = view.findViewById(R.id.wifiVerdict)
        wifiText = view.findViewById(R.id.wifiText)
        wifiLocationButton = view.findViewById(R.id.wifiLocationButton)
        wifiLocationButton.setOnClickListener {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val context = requireContext()
        val analyzer = WifiAnalyzer(context)
        wifiLocationButton.visibility = if (analyzer.hasLocationPermission()) View.GONE else View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            val trust = withContext(Dispatchers.Default) { analyzer.analyze() }
            renderWifi(trust)
        }
    }

    private fun renderWifi(t: WifiTrust) {
        if (!t.connected) {
            wifiVerdict.text = getString(R.string.wifi_not_connected)
            wifiVerdict.setTextColor(ContextCompat.getColor(requireContext(), R.color.cyber_on_surface_dim))
            wifiText.text = getString(R.string.wifi_not_connected_detail)
            return
        }
        val (label, colorRes) = when (t.trust) {
            RiskLevel.ALTO, RiskLevel.CRITICO -> getString(R.string.wifi_untrusted) to R.color.status_danger
            RiskLevel.SOSPETTO -> getString(R.string.wifi_caution) to R.color.status_warn
            RiskLevel.SICURO -> getString(R.string.wifi_trusted) to R.color.status_ok
        }
        wifiVerdict.text = label
        wifiVerdict.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
        wifiText.text = getString(R.string.wifi_detail, t.ssid, t.securityLabel, t.signalPercent) +
                "\n\n" + t.reasons.joinToString("\n") { "• $it" }
    }
}
