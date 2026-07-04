package com.cybersentinel.phoneguard.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.cybersentinel.phoneguard.R
import com.cybersentinel.phoneguard.data.Prefs
import com.cybersentinel.phoneguard.monitor.FileScanner
import com.cybersentinel.phoneguard.monitor.MonitorService
import com.cybersentinel.phoneguard.monitor.NetworkMonitor
import com.cybersentinel.phoneguard.util.AppLog
import com.cybersentinel.phoneguard.util.LogCategory
import com.cybersentinel.phoneguard.util.SystemIntents
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * Impostazioni: ogni funzione dell'app è attivabile/disattivabile
 * singolarmente, più lo stato delle autorizzazioni di sistema con
 * scorciatoie alle schermate dove concederle.
 */
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        bindSwitches(view)

        view.findViewById<MaterialButton>(R.id.usageAccessButton).setOnClickListener {
            SystemIntents.openUsageAccessSettings(requireContext())
        }
        view.findViewById<MaterialButton>(R.id.storageAccessButton).setOnClickListener {
            SystemIntents.requestAllFilesAccess(requireContext())
        }
        view.findViewById<MaterialButton>(R.id.notificationsButton).setOnClickListener {
            SystemIntents.openNotificationSettings(requireContext())
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
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

        val logging = view.findViewById<MaterialSwitch>(R.id.switchLogging)
        logging.isChecked = Prefs.loggingEnabled(context)
        logging.setOnCheckedChangeListener { _, checked ->
            Prefs.setLoggingEnabled(context, checked)
            if (checked) AppLog.log(context, LogCategory.SISTEMA, "Registro attività attivato")
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
