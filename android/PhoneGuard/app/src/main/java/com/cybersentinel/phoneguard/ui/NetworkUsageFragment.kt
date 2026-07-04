package com.cybersentinel.phoneguard.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cybersentinel.phoneguard.R
import com.cybersentinel.phoneguard.data.AppNetworkUsage
import com.cybersentinel.phoneguard.data.Prefs
import com.cybersentinel.phoneguard.monitor.AppVisibility
import com.cybersentinel.phoneguard.monitor.BackgroundAppsMonitor
import com.cybersentinel.phoneguard.monitor.MonitorService
import com.cybersentinel.phoneguard.monitor.NetworkMonitor
import com.cybersentinel.phoneguard.util.AppLog
import com.cybersentinel.phoneguard.util.LogCategory
import com.cybersentinel.phoneguard.util.SystemIntents
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pagina Traffico di rete: TUTTE le app che scambiano dati (comprese quelle
 * senza icona nel launcher), ordinate per byte inviati decrescenti, con
 * pulsante Ferma per app. Include anche un registro in linguaggio semplice
 * di cosa ogni app ha inviato/ricevuto e i limiti di ciò che l'app può
 * realmente vedere (nessuna ispezione dei contenuti).
 */
class NetworkUsageFragment : Fragment(R.layout.fragment_network) {

    private lateinit var usageAdapter: AppUsageAdapter
    private lateinit var permissionButton: MaterialButton
    private lateinit var networkLogText: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        permissionButton = view.findViewById(R.id.permissionButton)
        permissionButton.setOnClickListener { SystemIntents.openUsageAccessSettings(requireContext()) }
        networkLogText = view.findViewById(R.id.networkLogText)
        usageAdapter = AppUsageAdapter(onStop = ::stopApp)
        view.findViewById<RecyclerView>(R.id.appList).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = usageAdapter
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUsage()
        refreshNetworkLog()
    }

    private fun refreshUsage() {
        val context = requireContext()
        val networkMonitor = NetworkMonitor(context)
        val hasAccess = networkMonitor.hasUsageAccess()
        permissionButton.visibility = if (hasAccess) View.GONE else View.VISIBLE
        if (!hasAccess) {
            usageAdapter.submit(emptyList(), MonitorService.TX_ALERT_THRESHOLD_BYTES)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val now = System.currentTimeMillis()
            val (usage, hidden) = withContext(Dispatchers.IO) {
                val u = networkMonitor.queryUsage(now - 24L * 60 * 60 * 1000, now)
                val launcherPackages = AppVisibility.launcherPackages(context)
                val h = u.filterNot { it.isSystemApp }
                    .map { it.packageName }
                    .filterNot { it in launcherPackages }
                    .toSet()
                u to h
            }
            usageAdapter.submit(usage, MonitorService.TX_ALERT_THRESHOLD_BYTES, hidden)
        }
    }

    private fun stopApp(app: AppNetworkUsage) {
        val context = requireContext()
        BackgroundAppsMonitor(context).stop(app.packageName)
        AppLog.log(context, LogCategory.RETE, "Richiesta di stop per traffico dati: ${app.appLabel}")
        Toast.makeText(context, getString(R.string.stop_requested, app.appLabel), Toast.LENGTH_SHORT).show()
        view?.postDelayed({ if (isAdded) refreshUsage() }, 800)
    }

    private fun refreshNetworkLog() {
        val context = requireContext()
        if (!Prefs.loggingEnabled(context)) {
            networkLogText.setText(R.string.network_log_disabled)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val content = withContext(Dispatchers.IO) { AppLog.readCategory(context, LogCategory.RETE) }
            networkLogText.text = content.ifBlank { getString(R.string.network_log_empty) }
        }
    }
}
