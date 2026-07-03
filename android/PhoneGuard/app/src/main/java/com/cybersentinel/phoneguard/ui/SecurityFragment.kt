package com.cybersentinel.phoneguard.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cybersentinel.phoneguard.R
import com.cybersentinel.phoneguard.data.AppStorageInfo
import com.cybersentinel.phoneguard.data.AppThreat
import com.cybersentinel.phoneguard.data.SecurityCheck
import com.cybersentinel.phoneguard.data.SuspiciousFile
import com.cybersentinel.phoneguard.monitor.AppInventory
import com.cybersentinel.phoneguard.monitor.FileScanner
import com.cybersentinel.phoneguard.monitor.MonitorService
import com.cybersentinel.phoneguard.monitor.NetworkMonitor
import com.cybersentinel.phoneguard.monitor.PermissionAuditor
import com.cybersentinel.phoneguard.monitor.SecurityAnalyst
import com.cybersentinel.phoneguard.monitor.SystemAnalyzer
import com.cybersentinel.phoneguard.monitor.ThreatScanner
import com.cybersentinel.phoneguard.util.AppLog
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sezione Sicurezza: anti-spyware (con disinstallazione), Permission
 * Auditor, analisi di sistema, scansione file e traffico di rete per-app.
 */
class SecurityFragment : Fragment(R.layout.fragment_security) {

    private lateinit var threatAdapter: ThreatAdapter
    private lateinit var usageAdapter: AppUsageAdapter
    private lateinit var inventoryAdapter: AppStorageAdapter
    private lateinit var inventoryHeader: TextView
    private lateinit var threatsEmpty: TextView
    private lateinit var auditText: TextView
    private lateinit var systemText: TextView
    private lateinit var filesText: TextView
    private lateinit var permissionButton: MaterialButton
    private lateinit var storagePermissionButton: MaterialButton
    private lateinit var scanButton: MaterialButton
    private lateinit var filesProgress: LinearProgressIndicator

    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            updateStorageButton()
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        threatsEmpty = view.findViewById(R.id.threatsEmpty)
        auditText = view.findViewById(R.id.auditText)
        systemText = view.findViewById(R.id.systemText)
        filesText = view.findViewById(R.id.filesText)
        permissionButton = view.findViewById(R.id.permissionButton)
        storagePermissionButton = view.findViewById(R.id.storagePermissionButton)
        scanButton = view.findViewById(R.id.scanButton)
        filesProgress = view.findViewById(R.id.filesProgress)

        threatAdapter = ThreatAdapter(
            onUninstall = ::uninstallApp,
            onAppInfo = ::openAppDetails
        )
        view.findViewById<RecyclerView>(R.id.threatList).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = threatAdapter
        }

        usageAdapter = AppUsageAdapter()
        view.findViewById<RecyclerView>(R.id.appList).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = usageAdapter
        }

        inventoryHeader = view.findViewById(R.id.inventoryHeader)
        inventoryAdapter = AppStorageAdapter(onManage = ::openAppDetailsByPackage)
        view.findViewById<RecyclerView>(R.id.inventoryList).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = inventoryAdapter
        }

        permissionButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        storagePermissionButton.setOnClickListener { requestStorageAccess() }
        scanButton.setOnClickListener { scanFiles() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val context = requireContext()
        val networkMonitor = NetworkMonitor(context)
        val hasUsageAccess = networkMonitor.hasUsageAccess()
        permissionButton.visibility = if (hasUsageAccess) View.GONE else View.VISIBLE
        updateStorageButton()

        viewLifecycleOwner.lifecycleScope.launch {
            val threats = withContext(Dispatchers.Default) {
                ThreatScanner(context).scan()
            }
            threatAdapter.submitList(threats)
            threatsEmpty.text =
                if (threats.isEmpty()) getString(R.string.threats_none)
                else getString(R.string.threats_found, threats.size)

            val audit = withContext(Dispatchers.Default) {
                PermissionAuditor(context).audit()
            }
            auditText.text = audit.joinToString("\n\n") { group ->
                val header = "${group.title} — ${group.description}"
                if (group.apps.isEmpty()) "✅ $header\n   ${getString(R.string.audit_none)}"
                else "⚠️ $header\n   ${group.apps.joinToString(", ")}"
            }

            val checks = withContext(Dispatchers.Default) {
                SystemAnalyzer(context).analyze()
            }
            systemText.text = formatChecks(checks)

            if (hasUsageAccess) {
                val now = System.currentTimeMillis()
                val usage = withContext(Dispatchers.IO) {
                    networkMonitor.queryUsage(now - 24L * 60 * 60 * 1000, now)
                }
                usageAdapter.submit(usage, MonitorService.TX_ALERT_THRESHOLD_BYTES)

                val inventory = AppInventory(context)
                val apps = withContext(Dispatchers.IO) { inventory.list() }
                inventoryAdapter.submitList(apps)
                inventoryHeader.text = getString(
                    R.string.inventory_header,
                    apps.size,
                    SecurityAnalyst.formatSize(inventory.totalCacheBytes(apps))
                )
            } else {
                inventoryHeader.setText(R.string.inventory_no_access)
                inventoryAdapter.submitList(emptyList())
            }
        }
    }

    private fun openAppDetailsByPackage(app: AppStorageInfo) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${app.packageName}")
        )
        runCatching { startActivity(intent) }
    }

    private fun scanFiles() {
        val context = requireContext()
        val fileScanner = FileScanner(context)
        if (!fileScanner.hasStorageAccess()) {
            requestStorageAccess()
            return
        }
        scanButton.isEnabled = false
        filesProgress.visibility = View.VISIBLE
        val roots = fileScanner.storageRoots()
        filesText.text = getString(
            R.string.files_scanning_roots,
            roots.joinToString(", ") { it.absolutePath }
        )

        viewLifecycleOwner.lifecycleScope.launch {
            val found = withContext(Dispatchers.IO) { fileScanner.scan() }
            filesText.text = formatFiles(found)
            filesProgress.visibility = View.GONE
            scanButton.isEnabled = true
            AppLog.log(
                context,
                "Scansione file (${roots.size} volumi): ${found.size} sospetti"
            )
        }
    }

    private fun formatChecks(checks: List<SecurityCheck>): CharSequence =
        checks.joinToString("\n\n") { check ->
            val icon = if (check.ok) "✅" else "⚠️"
            "$icon ${check.title}: ${check.detail}"
        }

    private fun formatFiles(found: List<SuspiciousFile>): CharSequence {
        if (found.isEmpty()) return getString(R.string.files_none)
        val header = getString(R.string.files_found, found.size)
        val body = found.take(20).joinToString("\n\n") { file ->
            "⚠️ ${file.path}\n   ${file.reason}"
        }
        val more = if (found.size > 20) "\n\n…" else ""
        return "$header\n\n$body$more"
    }

    private fun uninstallApp(threat: AppThreat) {
        val intent = Intent(
            Intent.ACTION_DELETE,
            Uri.parse("package:${threat.packageName}")
        )
        runCatching { startActivity(intent) }
    }

    private fun openAppDetails(threat: AppThreat) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${threat.packageName}")
        )
        runCatching { startActivity(intent) }
    }

    private fun updateStorageButton() {
        storagePermissionButton.visibility =
            if (FileScanner(requireContext()).hasStorageAccess()) View.GONE
            else View.VISIBLE
    }

    private fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${requireContext().packageName}")
            )
            runCatching { startActivity(intent) }.onFailure {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}
