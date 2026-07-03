package com.cybersentinel.phoneguard.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cybersentinel.phoneguard.R
import com.cybersentinel.phoneguard.data.AppEnergyUsage
import com.cybersentinel.phoneguard.monitor.EnergyMonitor
import com.cybersentinel.phoneguard.monitor.NetworkMonitor
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sezione Energia: classifica delle app per impatto energetico stimato
 * (tempo in primo piano nelle ultime 24 ore) con badge per chi tiene
 * attivi Foreground Service in background.
 *
 * Il pulsante "Gestisci" apre la scheda di sistema dell'app: è lì che
 * Android espone "Arresto forzato" e la restrizione batteria — azioni
 * che il sistema riserva alla conferma esplicita dell'utente.
 */
class EnergyFragment : Fragment(R.layout.fragment_energy) {

    private lateinit var energyAdapter: EnergyAdapter
    private lateinit var usagePermissionButton: MaterialButton

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        usagePermissionButton = view.findViewById(R.id.usagePermissionButton)
        usagePermissionButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        energyAdapter = EnergyAdapter(onManage = ::openAppDetails)
        view.findViewById<RecyclerView>(R.id.energyList).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = energyAdapter
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val context = requireContext()
        val hasAccess = NetworkMonitor(context).hasUsageAccess()
        usagePermissionButton.visibility = if (hasAccess) View.GONE else View.VISIBLE
        if (!hasAccess) {
            energyAdapter.submitList(emptyList())
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val consumers = withContext(Dispatchers.Default) {
                EnergyMonitor(context).topConsumers()
            }
            energyAdapter.submitList(consumers)
        }
    }

    private fun openAppDetails(item: AppEnergyUsage) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${item.packageName}")
        )
        runCatching { startActivity(intent) }
    }
}
