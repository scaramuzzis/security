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
import com.cybersentinel.phoneguard.data.RunningApp
import com.cybersentinel.phoneguard.monitor.BackgroundAppsMonitor
import com.cybersentinel.phoneguard.util.AppLog
import com.cybersentinel.phoneguard.util.LogCategory
import com.cybersentinel.phoneguard.util.SystemIntents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pagina App attive in background: Foreground Service genuinamente attivi. */
class RunningAppsFragment : Fragment(R.layout.fragment_running) {

    private lateinit var runningAdapter: RunningAppAdapter
    private lateinit var runningHeader: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        runningHeader = view.findViewById(R.id.runningHeader)
        runningAdapter = RunningAppAdapter(
            onStop = ::stopApp,
            onDetails = { openAppDetailsByPackage(it.packageName) }
        )
        view.findViewById<RecyclerView>(R.id.runningList).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = runningAdapter
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val running = withContext(Dispatchers.Default) { BackgroundAppsMonitor(context).runningApps() }
            runningAdapter.submitList(running)
            runningHeader.text = when {
                !BackgroundAppsMonitor(context).hasUsageAccess() -> getString(R.string.running_no_access)
                running.isEmpty() -> getString(R.string.running_none)
                else -> getString(R.string.running_found, running.size)
            }
        }
    }

    private fun stopApp(app: RunningApp) {
        val context = requireContext()
        BackgroundAppsMonitor(context).stop(app.packageName)
        AppLog.log(context, LogCategory.SISTEMA, "Richiesta di stop app in background: ${app.appLabel}")
        Toast.makeText(context, getString(R.string.stop_requested, app.appLabel), Toast.LENGTH_SHORT).show()
        view?.postDelayed({ if (isAdded) refresh() }, 800)
    }

    private fun openAppDetailsByPackage(packageName: String) =
        SystemIntents.openAppDetails(requireContext(), packageName)
}
