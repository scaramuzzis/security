package com.cybersentinel.phoneguard.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cybersentinel.phoneguard.R
import com.cybersentinel.phoneguard.data.AppNetworkUsage
import com.cybersentinel.phoneguard.data.ConstantSender
import com.cybersentinel.phoneguard.data.Prefs
import com.cybersentinel.phoneguard.monitor.AppVisibility
import com.cybersentinel.phoneguard.monitor.BackgroundAppsMonitor
import com.cybersentinel.phoneguard.monitor.MonitorService
import com.cybersentinel.phoneguard.monitor.NetworkMonitor
import com.cybersentinel.phoneguard.monitor.SecurityAnalyst
import com.cybersentinel.phoneguard.ui.base.RefreshableFragment
import com.cybersentinel.phoneguard.util.AppLog
import com.cybersentinel.phoneguard.util.LogCategory
import com.cybersentinel.phoneguard.util.SystemIntents
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pagina Traffico di rete: TUTTE le app che scambiano dati (comprese quelle
 * senza icona nel launcher), ordinate per byte inviati decrescenti, con
 * pulsante Ferma per app. Include anche un registro in linguaggio semplice
 * di cosa ogni app ha inviato/ricevuto e i limiti di ciò che l'app può
 * realmente vedere (nessuna ispezione dei contenuti).
 */
class NetworkUsageFragment : RefreshableFragment(R.layout.fragment_network) {

    private lateinit var usageAdapter: AppUsageAdapter
    private lateinit var constantSenderAdapter: ConstantSenderAdapter
    private lateinit var permissionButton: MaterialButton
    private lateinit var networkLogText: TextView
    private lateinit var dataGapText: TextView
    private lateinit var constantSendersTitle: TextView
    private lateinit var constantSendersIntro: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        permissionButton = view.findViewById(R.id.permissionButton)
        permissionButton.setOnClickListener { SystemIntents.openUsageAccessSettings(requireContext()) }
        networkLogText = view.findViewById(R.id.networkLogText)
        dataGapText = view.findViewById(R.id.dataGapText)
        constantSendersTitle = view.findViewById(R.id.constantSendersTitle)
        constantSendersIntro = view.findViewById(R.id.constantSendersIntro)

        usageAdapter = AppUsageAdapter(onStop = { stopAndVerify(it.packageName, it.appLabel) })
        view.findViewById<RecyclerView>(R.id.appList).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = usageAdapter
        }
        constantSenderAdapter = ConstantSenderAdapter(onStop = { stopAndVerify(it.packageName, it.appLabel) })
        view.findViewById<RecyclerView>(R.id.constantSendersList).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = constantSenderAdapter
        }
        swipeRefresh.setOnRefreshListener {
            refreshUsage()
            refreshNetworkLog()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUsage()
        refreshNetworkLog()
    }

    private fun refreshUsage() {
        swipeRefresh.isRefreshing = true
        val context = requireContext()
        val networkMonitor = NetworkMonitor(context)
        val hasAccess = networkMonitor.hasUsageAccess()
        permissionButton.visibility = if (hasAccess) View.GONE else View.VISIBLE
        if (!hasAccess) {
            usageAdapter.submit(emptyList(), MonitorService.TX_ALERT_THRESHOLD_BYTES)
            endRefresh()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val now = System.currentTimeMillis()
            val dayAgo = now - 24L * 60 * 60 * 1000
            val (usage, hidden, active, gap, constantSenders) = withContext(Dispatchers.IO) {
                val u = networkMonitor.queryUsage(dayAgo, now)
                val launcherPackages = AppVisibility.launcherPackages(context)
                val h = u.filterNot { it.isSystemApp }
                    .map { it.packageName }
                    .filterNot { it in launcherPackages }
                    .toSet()
                val a = BackgroundAppsMonitor(context).activePackages()
                val g = networkMonitor.deviceTotal(dayAgo, now)
                val cs = networkMonitor.constantSenders()
                DataBundle(u, h, a, g, cs)
            }
            val activeCount = usage.count { it.packageName in active }
            usageAdapter.submit(
                usage, MonitorService.TX_ALERT_THRESHOLD_BYTES, hidden, active,
                activeSectionTitle = getString(R.string.network_section_active, activeCount),
                inactiveSectionTitle = getString(R.string.network_section_inactive, usage.size - activeCount)
            )
            renderDataGap(usage, gap)
            renderConstantSenders(constantSenders)
            endRefresh()
        }
    }

    /** Somma i byte attribuiti alle app e li confronta col totale visto dal sistema. */
    private fun renderDataGap(usage: List<AppNetworkUsage>, deviceTotal: Pair<Long, Long>) {
        val attributedTx = usage.sumOf { it.txBytes }
        val (_, deviceTx) = deviceTotal
        val gapTx = (deviceTx - attributedTx).coerceAtLeast(0)
        dataGapText.text = if (deviceTx <= 0 || gapTx < GAP_THRESHOLD_BYTES) {
            getString(R.string.data_gap_small, SecurityAnalyst.formatSize(attributedTx), SecurityAnalyst.formatSize(deviceTx))
        } else {
            getString(
                R.string.data_gap_large,
                SecurityAnalyst.formatSize(attributedTx), SecurityAnalyst.formatSize(deviceTx),
                SecurityAnalyst.formatSize(gapTx)
            )
        }
    }

    private fun renderConstantSenders(senders: List<ConstantSender>) {
        val visible = senders.isNotEmpty()
        constantSendersTitle.visibility = if (visible) View.VISIBLE else View.GONE
        constantSendersIntro.visibility = if (visible) View.VISIBLE else View.GONE
        constantSenderAdapter.submitList(senders)
    }

    /**
     * Ferma l'app e verifica l'esito: `killBackgroundProcesses` non tocca i
     * processi con un Foreground Service attivo (protetti dal sistema).
     * Molte delle app elencate qui hanno esattamente questo tipo di
     * servizio, quindi lo stop può risultare senza alcun effetto reale:
     * lo verifichiamo invece di limitarci a dire "fatto".
     */
    private fun stopAndVerify(packageName: String, appLabel: String) {
        val context = requireContext()
        val monitor = BackgroundAppsMonitor(context)
        monitor.stop(packageName)
        Toast.makeText(context, getString(R.string.stop_requested, appLabel), Toast.LENGTH_SHORT).show()

        viewLifecycleOwner.lifecycleScope.launch {
            delay(STOP_VERIFY_DELAY_MS)
            val stillActive = withContext(Dispatchers.Default) {
                monitor.isForegroundServiceActive(packageName)
            }
            val message = if (stillActive) getString(R.string.stop_still_active, appLabel)
            else getString(R.string.stop_confirmed, appLabel)
            AppLog.log(context, LogCategory.RETE, message)
            if (isAdded) {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                refreshUsage()
            }
        }
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

    private data class DataBundle(
        val usage: List<AppNetworkUsage>,
        val hidden: Set<String>,
        val active: Set<String>,
        val gap: Pair<Long, Long>,
        val constantSenders: List<ConstantSender>
    )

    companion object {
        private const val STOP_VERIFY_DELAY_MS = 1500L

        /** Sotto questa soglia il divario non attribuito è considerato rumore di misura, non un segnale. */
        private const val GAP_THRESHOLD_BYTES = 2L * 1024 * 1024
    }
}
