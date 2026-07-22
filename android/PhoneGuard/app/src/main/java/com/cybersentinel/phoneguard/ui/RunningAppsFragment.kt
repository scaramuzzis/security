package com.cybersentinel.phoneguard.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cybersentinel.phoneguard.R
import com.cybersentinel.phoneguard.data.RunningApp
import com.cybersentinel.phoneguard.monitor.BackgroundAppsMonitor
import com.cybersentinel.phoneguard.ui.base.RefreshableFragment
import com.cybersentinel.phoneguard.util.AppLog
import com.cybersentinel.phoneguard.util.LogCategory
import com.cybersentinel.phoneguard.util.SystemIntents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pagina App attive in background: Foreground Service genuinamente attivi. */
class RunningAppsFragment : RefreshableFragment(R.layout.fragment_running) {

    private lateinit var runningAdapter: RunningAppAdapter
    private lateinit var runningHeader: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        runningHeader = view.findViewById(R.id.runningHeader)
        runningAdapter = RunningAppAdapter(
            onStop = ::stopApp,
            onDetails = { openAppDetailsByPackage(it.packageName) }
        )
        view.findViewById<RecyclerView>(R.id.runningList).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = runningAdapter
        }
        swipeRefresh.setOnRefreshListener { refresh() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        swipeRefresh.isRefreshing = true
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val running = withContext(Dispatchers.Default) { BackgroundAppsMonitor(context).runningApps() }
            runningAdapter.submitList(running)
            runningHeader.text = when {
                !BackgroundAppsMonitor(context).hasUsageAccess() -> getString(R.string.running_no_access)
                running.isEmpty() -> getString(R.string.running_none)
                else -> getString(R.string.running_found, running.size)
            }
            endRefresh()
        }
    }

    /**
     * Ferma l'app e verifica l'esito: `killBackgroundProcesses` non tocca i
     * processi con un Foreground Service attivo (protetti dal sistema), cioè
     * esattamente le app elencate qui — quindi lo stop può risultare senza
     * effetto. Lo verifichiamo davvero invece di assumere che sia riuscito.
     */
    private fun stopApp(app: RunningApp) {
        val context = requireContext()
        val monitor = BackgroundAppsMonitor(context)
        monitor.stop(app.packageName)
        Toast.makeText(context, getString(R.string.stop_requested, app.appLabel), Toast.LENGTH_SHORT).show()

        viewLifecycleOwner.lifecycleScope.launch {
            delay(STOP_VERIFY_DELAY_MS)
            val stillActive = withContext(Dispatchers.Default) {
                monitor.isForegroundServiceActive(app.packageName)
            }
            val message = if (stillActive) getString(R.string.stop_still_active, app.appLabel)
            else getString(R.string.stop_confirmed, app.appLabel)
            AppLog.log(context, LogCategory.SISTEMA, message)
            if (isAdded) {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                refresh()
            }
        }
    }

    private fun openAppDetailsByPackage(packageName: String) =
        SystemIntents.openAppDetails(requireContext(), packageName)

    companion object {
        private const val STOP_VERIFY_DELAY_MS = 1500L
    }
}
