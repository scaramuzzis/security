package com.cybersentinel.phoneguard.ui

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cybersentinel.phoneguard.R
import com.cybersentinel.phoneguard.data.AppEnergyUsage
import com.cybersentinel.phoneguard.monitor.EnergyMonitor
import com.cybersentinel.phoneguard.monitor.NetworkMonitor
import com.cybersentinel.phoneguard.ui.base.RefreshableFragment
import com.cybersentinel.phoneguard.util.SystemIntents
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pagina App energivore: classifica per impatto stimato nelle ultime 24 ore. */
class EnergyUsageFragment : RefreshableFragment(R.layout.fragment_energy_usage) {

    private lateinit var energyAdapter: EnergyAdapter
    private lateinit var usagePermissionButton: MaterialButton

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        usagePermissionButton = view.findViewById(R.id.usagePermissionButton)
        usagePermissionButton.setOnClickListener { SystemIntents.openUsageAccessSettings(requireContext()) }
        energyAdapter = EnergyAdapter(onManage = ::openAppDetails)
        view.findViewById<RecyclerView>(R.id.energyList).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = energyAdapter
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
        val hasAccess = NetworkMonitor(context).hasUsageAccess()
        usagePermissionButton.visibility = if (hasAccess) View.GONE else View.VISIBLE
        if (!hasAccess) {
            energyAdapter.submitList(emptyList())
            endRefresh()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val consumers = withContext(Dispatchers.Default) { EnergyMonitor(context).topConsumers() }
            energyAdapter.submitList(consumers)
            endRefresh()
        }
    }

    private fun openAppDetails(item: AppEnergyUsage) =
        SystemIntents.openAppDetails(requireContext(), item.packageName)
}
