package com.cybersentinel.phoneguard.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cybersentinel.phoneguard.R
import com.cybersentinel.phoneguard.monitor.BackgroundAppsMonitor
import com.cybersentinel.phoneguard.monitor.BatteryMonitor
import com.cybersentinel.phoneguard.monitor.ResourceMonitor
import com.cybersentinel.phoneguard.monitor.SecurityAnalyst
import com.cybersentinel.phoneguard.monitor.ThreatScanner
import com.cybersentinel.phoneguard.ui.base.RefreshableFragment
import com.cybersentinel.phoneguard.util.AppLog
import com.cybersentinel.phoneguard.util.LogCategory
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pagina "Salute dispositivo": riassume in un'unica schermata batteria,
 * spazio, memoria e minacce — ispirata alle schermate di sistema tipo
 * "Assistenza dispositivo", ma senza introdurre nuove misurazioni: riusa
 * gli stessi motori già calcolati altrove in PhoneGuard (Dashboard, App
 * sospette), quindi resta sempre coerente con le altre pagine.
 */
class DeviceHealthFragment : RefreshableFragment(R.layout.fragment_device_health) {

    private lateinit var verdict: TextView
    private lateinit var hint: TextView
    private lateinit var optimizeButton: MaterialButton
    private lateinit var optimizeResult: TextView
    private lateinit var batteryDetail: TextView
    private lateinit var batteryBar: LinearProgressIndicator
    private lateinit var storageDetail: TextView
    private lateinit var storageBar: LinearProgressIndicator
    private lateinit var ramDetail: TextView
    private lateinit var ramBar: LinearProgressIndicator
    private lateinit var protectionCard: MaterialCardView
    private lateinit var protectionDetail: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        verdict = view.findViewById(R.id.healthVerdict)
        hint = view.findViewById(R.id.healthHint)
        optimizeButton = view.findViewById(R.id.healthOptimizeButton)
        optimizeResult = view.findViewById(R.id.healthOptimizeResult)
        batteryDetail = view.findViewById(R.id.healthBatteryDetail)
        batteryBar = view.findViewById(R.id.healthBatteryBar)
        storageDetail = view.findViewById(R.id.healthStorageDetail)
        storageBar = view.findViewById(R.id.healthStorageBar)
        ramDetail = view.findViewById(R.id.healthRamDetail)
        ramBar = view.findViewById(R.id.healthRamBar)
        protectionCard = view.findViewById(R.id.healthProtectionCard)
        protectionDetail = view.findViewById(R.id.healthProtectionDetail)

        optimizeButton.setOnClickListener { optimize() }
        protectionCard.setOnClickListener {
            startActivity(SectionHostActivity.intentFor(requireContext(), SectionHostActivity.Section.THREATS))
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
            val battery = withContext(Dispatchers.Default) { BatteryMonitor(context).snapshot() }
            val ram = withContext(Dispatchers.Default) { ResourceMonitor.ram(context) }
            val storage = withContext(Dispatchers.Default) { ResourceMonitor.storage() }
            val threats = withContext(Dispatchers.Default) { ThreatScanner(context).scan().size }

            val minutesRemaining = battery.estimatedMinutesRemaining
            batteryDetail.text = when {
                battery.isCharging -> getString(R.string.health_battery_detail_charging, battery.levelPercent)
                minutesRemaining != null -> getString(
                    R.string.health_battery_detail_remaining,
                    battery.levelPercent, minutesRemaining / 60, minutesRemaining % 60
                )
                else -> getString(R.string.health_battery_detail_unknown, battery.levelPercent)
            }
            batteryBar.setProgressCompat(battery.levelPercent, true)
            batteryBar.setIndicatorColor(barColorFor(battery.levelPercent, inverted = true))

            storageDetail.text = getString(
                R.string.health_storage_detail, storage.percent,
                SecurityAnalyst.formatSize(storage.usedBytes), SecurityAnalyst.formatSize(storage.totalBytes)
            )
            storageBar.setProgressCompat(storage.percent, true)
            storageBar.setIndicatorColor(barColorFor(storage.percent, inverted = false))

            ramDetail.text = getString(
                R.string.health_ram_detail, ram.percent,
                SecurityAnalyst.formatSize(ram.usedBytes), SecurityAnalyst.formatSize(ram.totalBytes)
            )
            ramBar.setProgressCompat(ram.percent, true)
            ramBar.setIndicatorColor(barColorFor(ram.percent, inverted = false))

            protectionDetail.text = if (threats == 0) getString(R.string.health_protection_ok)
            else getString(R.string.health_protection_threats, threats)

            renderVerdict(storage.percent, ram.percent, threats)
            endRefresh()
        }
    }

    /**
     * Verdetto di sintesi: minacce rilevate prima di tutto, poi spazio/RAM
     * quasi saturi. È un giudizio distinto da quello della Dashboard: qui
     * riguarda lo stato delle risorse, non l'analisi di sicurezza completa.
     */
    private fun renderVerdict(storagePercent: Int, ramPercent: Int, threats: Int) {
        val (verdictRes, hintRes) = when {
            threats > 0 -> R.string.health_critical to R.string.health_threats_hint
            storagePercent >= 90 -> R.string.health_warning to R.string.health_storage_hint
            ramPercent >= 90 -> R.string.health_warning to R.string.health_ram_hint
            else -> R.string.health_good to R.string.health_good_hint
        }
        verdict.setText(verdictRes)
        hint.setText(hintRes)
    }

    /** Verde/ambra/rosso in base alla soglia; [inverted] per la batteria (poca carica = rosso). */
    private fun barColorFor(percent: Int, inverted: Boolean): Int {
        val danger = if (inverted) percent <= 20 else percent >= 90
        val warn = if (inverted) percent <= 50 else percent >= 70
        val colorRes = when {
            danger -> R.color.status_danger
            warn -> R.color.status_warn
            else -> R.color.status_ok
        }
        return ContextCompat.getColor(requireContext(), colorRes)
    }

    /** Stesso "Ottimizza" della Dashboard (BackgroundAppsMonitor.stopAll), nessuna logica duplicata. */
    private fun optimize() {
        val context = requireContext()
        optimizeButton.isEnabled = false
        optimizeResult.visibility = View.VISIBLE
        optimizeResult.setText(R.string.optimize_running)
        viewLifecycleOwner.lifecycleScope.launch {
            val running = withContext(Dispatchers.Default) { BackgroundAppsMonitor(context).stopAll() }
            optimizeButton.isEnabled = true
            optimizeResult.text = if (running.isEmpty()) getString(R.string.optimize_none)
            else getString(R.string.optimize_done, running.size)
            AppLog.log(
                context, LogCategory.SISTEMA,
                "Ottimizzazione (Salute dispositivo): fermate ${running.size} app in background"
            )
            refresh()
        }
    }
}
