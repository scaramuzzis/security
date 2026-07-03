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
import com.cybersentinel.phoneguard.data.JunkItem
import com.cybersentinel.phoneguard.monitor.EnergyMonitor
import com.cybersentinel.phoneguard.monitor.JunkScanner
import com.cybersentinel.phoneguard.monitor.NetworkMonitor
import com.cybersentinel.phoneguard.monitor.SecurityAnalyst
import com.cybersentinel.phoneguard.util.AppLog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sezione Energia & Spazio: app energivore + pulizia dei file inutili.
 */
class EnergyFragment : Fragment(R.layout.fragment_energy) {

    private lateinit var energyAdapter: EnergyAdapter
    private lateinit var usagePermissionButton: MaterialButton

    private lateinit var junkAdapter: JunkAdapter
    private lateinit var cleanSummary: android.widget.TextView
    private lateinit var cleanProgress: LinearProgressIndicator
    private lateinit var scanJunkButton: MaterialButton
    private lateinit var cleanButton: MaterialButton

    private var junkFound: List<JunkItem> = emptyList()

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

        cleanSummary = view.findViewById(R.id.cleanSummary)
        cleanProgress = view.findViewById(R.id.cleanProgress)
        scanJunkButton = view.findViewById(R.id.scanJunkButton)
        cleanButton = view.findViewById(R.id.cleanButton)

        junkAdapter = JunkAdapter()
        view.findViewById<RecyclerView>(R.id.junkList).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = junkAdapter
        }

        scanJunkButton.setOnClickListener { scanJunk() }
        cleanButton.setOnClickListener { confirmClean() }
    }

    override fun onResume() {
        super.onResume()
        refreshEnergy()
    }

    private fun refreshEnergy() {
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

    private fun scanJunk() {
        val context = requireContext()
        val scanner = JunkScanner(context)
        if (!scanner.hasStorageAccess()) {
            requestStorageAccess()
            return
        }
        scanJunkButton.isEnabled = false
        cleanButton.isEnabled = false
        cleanProgress.visibility = View.VISIBLE
        cleanSummary.setText(R.string.clean_scanning)

        viewLifecycleOwner.lifecycleScope.launch {
            val found = withContext(Dispatchers.IO) { scanner.scan() }
            junkFound = found
            junkAdapter.submitList(found)
            cleanProgress.visibility = View.GONE
            scanJunkButton.isEnabled = true

            val total = found.sumOf { it.sizeBytes }
            if (found.isEmpty()) {
                cleanSummary.setText(R.string.clean_none)
                cleanButton.isEnabled = false
            } else {
                cleanSummary.text = getString(
                    R.string.clean_found, found.size, SecurityAnalyst.formatSize(total)
                )
                cleanButton.isEnabled = true
            }
            AppLog.log(context, "PULIZIA", "Scansione file inutili: ${found.size} elementi, ${SecurityAnalyst.formatSize(total)}")
        }
    }

    private fun confirmClean() {
        if (junkFound.isEmpty()) return
        val total = junkFound.sumOf { it.sizeBytes }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.clean_confirm_title)
            .setMessage(
                getString(
                    R.string.clean_confirm_message,
                    junkFound.size, SecurityAnalyst.formatSize(total)
                )
            )
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.clean_confirm_ok) { _, _ -> doClean() }
            .show()
    }

    private fun doClean() {
        val context = requireContext()
        val toClean = junkFound
        cleanButton.isEnabled = false
        scanJunkButton.isEnabled = false
        cleanProgress.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            val freed = withContext(Dispatchers.IO) {
                JunkScanner(context).clean(toClean)
            }
            junkFound = emptyList()
            junkAdapter.submitList(emptyList())
            cleanProgress.visibility = View.GONE
            scanJunkButton.isEnabled = true
            cleanSummary.text = getString(R.string.clean_done, SecurityAnalyst.formatSize(freed))
            AppLog.log(context, "PULIZIA", "Pulizia completata: liberati ${SecurityAnalyst.formatSize(freed)}")
        }
    }

    private fun requestStorageAccess() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${requireContext().packageName}")
            )
            runCatching { startActivity(intent) }.onFailure {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
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
