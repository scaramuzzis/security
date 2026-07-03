package com.cybersentinel.phoneguard.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.cybersentinel.phoneguard.R
import com.cybersentinel.phoneguard.util.AppLog
import com.cybersentinel.phoneguard.data.Prefs
import com.cybersentinel.phoneguard.monitor.FileScanner
import com.cybersentinel.phoneguard.monitor.MonitorService
import com.cybersentinel.phoneguard.monitor.NetworkMonitor
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Impostazioni: regole di automazione e stato delle autorizzazioni di
 * sistema, con scorciatoie alle schermate dove concederle.
 */
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        bindSwitches(view)
        bindLog(view)

        view.findViewById<MaterialButton>(R.id.usageAccessButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        view.findViewById<MaterialButton>(R.id.storageAccessButton).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${requireContext().packageName}")
                )
                runCatching { startActivity(intent) }.onFailure {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
        }
        view.findViewById<MaterialButton>(R.id.notificationsButton).setOnClickListener {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
            runCatching { startActivity(intent) }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
        refreshLog()
    }

    private fun bindLog(view: View) {
        val context = requireContext()

        val logging = view.findViewById<MaterialSwitch>(R.id.switchLogging)
        logging.isChecked = Prefs.loggingEnabled(context)
        logging.setOnCheckedChangeListener { _, checked ->
            Prefs.setLoggingEnabled(context, checked)
            if (checked) AppLog.log(context, "SISTEMA", "Registro attività attivato")
            refreshLog()
        }

        view.findViewById<View>(R.id.logRefreshButton).setOnClickListener {
            refreshLog()
        }
        view.findViewById<View>(R.id.logClearButton).setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                withContext(Dispatchers.IO) { AppLog.clear(context) }
                refreshLog()
            }
        }
    }

    private fun refreshLog() {
        val context = requireContext()
        val logText = view?.findViewById<TextView>(R.id.logText) ?: return
        if (!Prefs.loggingEnabled(context)) {
            logText.text = getString(R.string.log_disabled)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val content = withContext(Dispatchers.IO) { AppLog.read(context) }
            logText.text = content.ifBlank { getString(R.string.log_empty) }
        }
    }

    private fun bindSwitches(view: View) {
        val context = requireContext()

        val monitoring = view.findViewById<MaterialSwitch>(R.id.switchMonitoring)
        monitoring.isChecked = Prefs.monitoringEnabled(context)
        monitoring.setOnCheckedChangeListener { _, checked ->
            Prefs.setMonitoringEnabled(context, checked)
            if (checked) MonitorService.start(context) else MonitorService.stop(context)
        }

        val threatScan = view.findViewById<MaterialSwitch>(R.id.switchThreatScan)
        threatScan.isChecked = Prefs.threatScanEnabled(context)
        threatScan.setOnCheckedChangeListener { _, checked ->
            Prefs.setThreatScanEnabled(context, checked)
        }

        val networkAlerts = view.findViewById<MaterialSwitch>(R.id.switchNetworkAlerts)
        networkAlerts.isChecked = Prefs.networkAlertsEnabled(context)
        networkAlerts.setOnCheckedChangeListener { _, checked ->
            Prefs.setNetworkAlertsEnabled(context, checked)
        }

        val battery = view.findViewById<MaterialSwitch>(R.id.switchBatteryAlerts)
        battery.isChecked = Prefs.batteryAlertsEnabled(context)
        battery.setOnCheckedChangeListener { _, checked ->
            Prefs.setBatteryAlertsEnabled(context, checked)
        }

        val files = view.findViewById<MaterialSwitch>(R.id.switchFileScan)
        files.isChecked = Prefs.autoFileScanEnabled(context)
        files.setOnCheckedChangeListener { _, checked ->
            Prefs.setAutoFileScanEnabled(context, checked)
        }

        val appStarts = view.findViewById<MaterialSwitch>(R.id.switchAppStartAlerts)
        appStarts.isChecked = Prefs.appStartAlertsEnabled(context)
        appStarts.setOnCheckedChangeListener { _, checked ->
            Prefs.setAppStartAlertsEnabled(context, checked)
        }
    }

    private fun refreshPermissionStatus() {
        val context = requireContext()
        val view = view ?: return

        val usageOk = NetworkMonitor(context).hasUsageAccess()
        view.findViewById<MaterialButton>(R.id.usageAccessButton).text = getString(
            if (usageOk) R.string.perm_usage_ok else R.string.perm_usage_missing
        )

        val storageOk = FileScanner(context).hasStorageAccess()
        view.findViewById<MaterialButton>(R.id.storageAccessButton).text = getString(
            if (storageOk) R.string.perm_storage_ok else R.string.perm_storage_missing
        )

        val notificationsOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        view.findViewById<MaterialButton>(R.id.notificationsButton).text = getString(
            if (notificationsOk) R.string.perm_notifications_ok
            else R.string.perm_notifications_missing
        )
    }
}
