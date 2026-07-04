package com.cybersentinel.phoneguard.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cybersentinel.phoneguard.R
import com.cybersentinel.phoneguard.data.AppStorageInfo
import com.cybersentinel.phoneguard.monitor.AppInventory
import com.cybersentinel.phoneguard.monitor.NetworkMonitor
import com.cybersentinel.phoneguard.monitor.SecurityAnalyst
import com.cybersentinel.phoneguard.util.SystemIntents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pagina Inventario app e sistema: spazio occupato da app/dati/cache. */
class AppInventoryFragment : Fragment(R.layout.fragment_inventory) {

    private lateinit var inventoryAdapter: AppStorageAdapter
    private lateinit var inventoryHeader: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        inventoryHeader = view.findViewById(R.id.inventoryHeader)
        inventoryAdapter = AppStorageAdapter(onManage = ::openAppDetails)
        view.findViewById<RecyclerView>(R.id.inventoryList).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = inventoryAdapter
        }
    }

    override fun onResume() {
        super.onResume()
        val context = requireContext()
        if (!NetworkMonitor(context).hasUsageAccess()) {
            inventoryHeader.setText(R.string.inventory_no_access)
            inventoryAdapter.submitList(emptyList())
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val inventory = AppInventory(context)
            val apps = withContext(Dispatchers.IO) { inventory.list() }
            inventoryAdapter.submitList(apps)
            inventoryHeader.text = getString(
                R.string.inventory_header, apps.size,
                SecurityAnalyst.formatSize(inventory.totalCacheBytes(apps))
            )
        }
    }

    private fun openAppDetails(app: AppStorageInfo) =
        SystemIntents.openAppDetails(requireContext(), app.packageName)
}
