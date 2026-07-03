package com.cybersentinel.phoneguard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.cybersentinel.phoneguard.data.AppThreat
import com.cybersentinel.phoneguard.data.SecurityCheck
import com.cybersentinel.phoneguard.data.SuspiciousFile
import com.cybersentinel.phoneguard.monitor.BatteryMonitor
import com.cybersentinel.phoneguard.monitor.FileScanner
import com.cybersentinel.phoneguard.monitor.MonitorService
import com.cybersentinel.phoneguard.monitor.NetworkMonitor
import com.cybersentinel.phoneguard.monitor.SystemAnalyzer
import com.cybersentinel.phoneguard.monitor.ThreatScanner
import com.cybersentinel.phoneguard.ui.AppUsageAdapter
import com.cybersentinel.phoneguard.ui.ThreatAdapter
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var batteryMonitor: BatteryMonitor
    private lateinit var systemAnalyzer: SystemAnalyzer
    private lateinit var fileScanner: FileScanner
    private lateinit var threatScanner: ThreatScanner
    private lateinit var adapter: AppUsageAdapter
    private lateinit var threatAdapter: ThreatAdapter

    private lateinit var batteryText: TextView
    private lateinit var systemText: TextView
    private lateinit var filesText: TextView
    private lateinit var threatsEmpty: TextView
    private lateinit var permissionButton: MaterialButton
    private lateinit var storagePermissionButton: MaterialButton
    private lateinit var scanButton: MaterialButton
    private lateinit var swipeRefresh: SwipeRefreshLayout

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            updateStorageButton()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        networkMonitor = NetworkMonitor(this)
        batteryMonitor = BatteryMonitor(this)
        systemAnalyzer = SystemAnalyzer(this)
        fileScanner = FileScanner(this)
        threatScanner = ThreatScanner(this)
        adapter = AppUsageAdapter()
        threatAdapter = ThreatAdapter(
            onUninstall = ::uninstallApp,
            onAppInfo = ::openAppDetails
        )

        batteryText = findViewById(R.id.batteryText)
        systemText = findViewById(R.id.systemText)
        filesText = findViewById(R.id.filesText)
        threatsEmpty = findViewById(R.id.threatsEmpty)
        permissionButton = findViewById(R.id.permissionButton)
        storagePermissionButton = findViewById(R.id.storagePermissionButton)
        scanButton = findViewById(R.id.scanButton)
        swipeRefresh = findViewById(R.id.swipeRefresh)

        val list = findViewById<RecyclerView>(R.id.appList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        val threats = findViewById<RecyclerView>(R.id.threatList)
        threats.layoutManager = LinearLayoutManager(this)
        threats.adapter = threatAdapter

        permissionButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        storagePermissionButton.setOnClickListener { requestStorageAccess() }
        scanButton.setOnClickListener { scanFiles() }
        swipeRefresh.setOnRefreshListener { refresh() }

        askNotificationPermission()
        startMonitorService()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val hasUsageAccess = networkMonitor.hasUsageAccess()
        permissionButton.visibility = if (hasUsageAccess) View.GONE else View.VISIBLE
        updateStorageButton()

        lifecycleScope.launch {
            // Batteria
            val battery = withContext(Dispatchers.Default) { batteryMonitor.snapshot() }
            batteryText.text = getString(
                R.string.battery_summary,
                battery.levelPercent,
                if (battery.isCharging) getString(R.string.charging)
                else getString(R.string.discharging),
                battery.temperatureCelsius,
                battery.healthLabel,
                battery.estimatedWatts
            )

            // Analisi di sistema
            val checks = withContext(Dispatchers.Default) { systemAnalyzer.analyze() }
            systemText.text = formatChecks(checks)

            // Scansione anti-spyware: app nascoste e sospette
            val foundThreats = withContext(Dispatchers.Default) { threatScanner.scan() }
            threatAdapter.submitList(foundThreats)
            threatsEmpty.text =
                if (foundThreats.isEmpty()) getString(R.string.threats_none)
                else getString(R.string.threats_found, foundThreats.size)

            // Traffico di rete (ultime 24 ore)
            if (hasUsageAccess) {
                val now = System.currentTimeMillis()
                val usage = withContext(Dispatchers.IO) {
                    networkMonitor.queryUsage(now - 24L * 60 * 60 * 1000, now)
                }
                adapter.submit(usage, MonitorService.TX_ALERT_THRESHOLD_BYTES)
            }
            swipeRefresh.isRefreshing = false
        }
    }

    private fun scanFiles() {
        if (!fileScanner.hasStorageAccess()) {
            requestStorageAccess()
            return
        }
        scanButton.isEnabled = false
        filesText.text = getString(R.string.files_scanning)

        lifecycleScope.launch {
            val found = withContext(Dispatchers.IO) { fileScanner.scan() }
            filesText.text = formatFiles(found)
            scanButton.isEnabled = true
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

    /**
     * Avvia la disinstallazione tramite il dialogo di sistema (l'utente
     * conferma sempre). Se l'app è amministratore del dispositivo Android
     * bloccherà la rimozione: prima va revocato il privilegio dalla sua
     * scheda, raggiungibile con il pulsante "Info app".
     */
    private fun uninstallApp(threat: AppThreat) {
        val intent = Intent(
            Intent.ACTION_DELETE,
            Uri.parse("package:${threat.packageName}")
        )
        runCatching { startActivity(intent) }
    }

    /** Apre la scheda di sistema dell'app (permessi, admin, arresto forzato). */
    private fun openAppDetails(threat: AppThreat) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${threat.packageName}")
        )
        runCatching { startActivity(intent) }
    }

    private fun updateStorageButton() {
        storagePermissionButton.visibility =
            if (fileScanner.hasStorageAccess()) View.GONE else View.VISIBLE
    }

    private fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: permesso speciale "Accesso a tutti i file"
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName")
            )
            runCatching { startActivity(intent) }.onFailure {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun startMonitorService() {
        val intent = Intent(this, MonitorService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
