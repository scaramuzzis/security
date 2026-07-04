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
import com.cybersentinel.phoneguard.data.RunningApp
import com.cybersentinel.phoneguard.monitor.BackgroundAppsMonitor
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
    private lateinit var runningAdapter: RunningAppAdapter
    private lateinit var runningHeader: android.widget.TextView

    private lateinit var junkAdapter: JunkAdapter
    private lateinit var cleanSummary: android.widget.TextView
    private lateinit var cleanProgress: LinearProgressIndicator
    private lateinit var scanJunkButton: MaterialButton
    private lateinit var cleanButton: MaterialButton
    private lateinit var selectAll: com.google.android.material.checkbox.MaterialCheckBox

    private var junkFound: List<JunkItem> = emptyList()
    private val selectedJunk = LinkedHashSet<String>()

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

        runningHeader = view.findViewById(R.id.runningHeader)
        runningAdapter = RunningAppAdapter(
            onStop = ::stopApp,
            onDetails = { openAppDetailsByPackage(it.packageName) }
        )
        view.findViewById<RecyclerView>(R.id.runningList).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = runningAdapter
        }

        cleanSummary = view.findViewById(R.id.cleanSummary)
        cleanProgress = view.findViewById(R.id.cleanProgress)
        scanJunkButton = view.findViewById(R.id.scanJunkButton)
        cleanButton = view.findViewById(R.id.cleanButton)
        selectAll = view.findViewById(R.id.selectAll)

        junkAdapter = JunkAdapter(
            isSelected = { it.path in selectedJunk },
            onToggle = { item, checked -> toggleJunk(item, checked) }
        )
        view.findViewById<RecyclerView>(R.id.junkList).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = junkAdapter
        }

        selectAll.setOnClickListener {
            val all = selectAll.isChecked
            selectedJunk.clear()
            if (all) selectedJunk.addAll(junkFound.map { it.path })
            junkAdapter.notifyDataSetChanged()
            updateCleanButton()
        }

        scanJunkButton.setOnClickListener { scanJunk() }
        cleanButton.setOnClickListener { confirmClean() }
    }

    private fun toggleJunk(item: JunkItem, checked: Boolean) {
        if (checked) selectedJunk.add(item.path) else selectedJunk.remove(item.path)
        updateCleanButton()
    }

    private fun updateCleanButton() {
        val selected = junkFound.filter { it.path in selectedJunk }
        val total = selected.sumOf { it.sizeBytes }
        cleanButton.isEnabled = selected.isNotEmpty()
        cleanButton.text = if (selected.isEmpty()) getString(R.string.clean_now)
        else getString(R.string.clean_selected, selected.size, SecurityAnalyst.formatSize(total))
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
        refreshRunning()
    }

    private fun refreshRunning() {
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val running = withContext(Dispatchers.Default) {
                BackgroundAppsMonitor(context).runningApps()
            }
            runningAdapter.submitList(running)
            runningHeader.text = when {
                !BackgroundAppsMonitor(context).hasUsageAccess() ->
                    getString(R.string.running_no_access)
                running.isEmpty() -> getString(R.string.running_none)
                else -> getString(R.string.running_found, running.size)
            }
        }
    }

    private fun stopApp(app: RunningApp) {
        val context = requireContext()
        BackgroundAppsMonitor(context).stop(app.packageName)
        com.cybersentinel.phoneguard.util.AppLog.log(
            context, "SISTEMA", "Richiesta di stop app in background: ${app.appLabel}"
        )
        android.widget.Toast.makeText(
            context, getString(R.string.stop_requested, app.appLabel),
            android.widget.Toast.LENGTH_SHORT
        ).show()
        // Ricontrolla dopo un istante per riflettere lo stato aggiornato.
        view?.postDelayed({ if (isAdded) refreshRunning() }, 800)
    }

    private fun openAppDetailsByPackage(packageName: String) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName")
        )
        runCatching { startActivity(intent) }
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
            // Preselezione prudente: tutto tranne i file di log/backup
            // (.log, .bak, .old), che potrebbero essere backup intenzionali
            // dell'utente e non solo scarti — meglio chiedere conferma esplicita.
            selectedJunk.clear()
            selectedJunk.addAll(
                found.filter { it.category != com.cybersentinel.phoneguard.data.JunkCategory.LOG_FILE }
                    .map { it.path }
            )
            junkAdapter.submitList(found)
            cleanProgress.visibility = View.GONE
            scanJunkButton.isEnabled = true

            val total = found.sumOf { it.sizeBytes }
            if (found.isEmpty()) {
                cleanSummary.setText(R.string.clean_none)
                selectAll.visibility = View.GONE
            } else {
                cleanSummary.text = getString(
                    R.string.clean_found, found.size, SecurityAnalyst.formatSize(total)
                )
                selectAll.visibility = View.VISIBLE
                selectAll.isChecked = selectedJunk.size == found.size
            }
            updateCleanButton()
            AppLog.log(context, "PULIZIA", "Scansione file inutili: ${found.size} elementi, ${SecurityAnalyst.formatSize(total)}")
        }
    }

    private fun confirmClean() {
        val selected = junkFound.filter { it.path in selectedJunk }
        if (selected.isEmpty()) return
        val total = selected.sumOf { it.sizeBytes }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.clean_confirm_title)
            .setMessage(
                getString(
                    R.string.clean_confirm_message,
                    selected.size, SecurityAnalyst.formatSize(total)
                )
            )
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.clean_confirm_ok) { _, _ -> doClean(selected) }
            .show()
    }

    private fun doClean(toClean: List<JunkItem>) {
        val context = requireContext()
        cleanButton.isEnabled = false
        scanJunkButton.isEnabled = false
        cleanProgress.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            val freed = withContext(Dispatchers.IO) {
                JunkScanner(context).clean(toClean)
            }
            val cleanedPaths = toClean.map { it.path }.toSet()
            junkFound = junkFound.filterNot { it.path in cleanedPaths }
            selectedJunk.removeAll(cleanedPaths)
            junkAdapter.submitList(junkFound)
            selectAll.visibility = if (junkFound.isEmpty()) View.GONE else View.VISIBLE
            cleanProgress.visibility = View.GONE
            scanJunkButton.isEnabled = true
            updateCleanButton()
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
