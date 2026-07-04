package com.cybersentinel.phoneguard.ui

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.cybersentinel.phoneguard.R
import com.cybersentinel.phoneguard.ui.chart.DonutChartView
import com.cybersentinel.phoneguard.ui.chart.RingGaugeView
import com.cybersentinel.phoneguard.util.AppLog
import com.cybersentinel.phoneguard.data.Prefs
import com.cybersentinel.phoneguard.data.RiskLevel
import com.cybersentinel.phoneguard.monitor.AppInventory
import com.cybersentinel.phoneguard.monitor.BatteryMonitor
import com.cybersentinel.phoneguard.monitor.EnergyMonitor
import com.cybersentinel.phoneguard.monitor.FileScanner
import com.cybersentinel.phoneguard.monitor.JunkScanner
import com.cybersentinel.phoneguard.monitor.MonitorService
import com.cybersentinel.phoneguard.monitor.NetworkMonitor
import com.cybersentinel.phoneguard.monitor.SecurityAnalyst
import com.cybersentinel.phoneguard.monitor.SystemAnalyzer
import com.cybersentinel.phoneguard.monitor.ThreatScanner
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Home: stato complessivo del telefono, Analisi Globale con un tap,
 * contatori, batteria in tempo reale e interruttori delle automazioni.
 */
class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private lateinit var statusIcon: TextView
    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView
    private lateinit var batteryText: TextView
    private lateinit var selfUsageText: TextView
    private lateinit var globalScanButton: MaterialButton
    private lateinit var scanProgress: LinearProgressIndicator
    private lateinit var scanProgressLabel: TextView
    private lateinit var analystCard: View
    private lateinit var analystHeadline: TextView
    private lateinit var analystSummary: TextView
    private lateinit var analystRecommendations: TextView
    private lateinit var ringBattery: RingGaugeView
    private lateinit var ringRam: RingGaugeView
    private lateinit var ringStorage: RingGaugeView
    private lateinit var securityChartCard: View
    private lateinit var securityDonut: DonutChartView
    private lateinit var legendOk: TextView
    private lateinit var legendWarn: TextView
    private lateinit var legendDanger: TextView

    private var selfUsageJob: Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        statusIcon = view.findViewById(R.id.statusIcon)
        statusTitle = view.findViewById(R.id.statusTitle)
        statusDetail = view.findViewById(R.id.statusDetail)
        batteryText = view.findViewById(R.id.batteryText)
        selfUsageText = view.findViewById(R.id.selfUsageText)
        globalScanButton = view.findViewById(R.id.globalScanButton)
        scanProgress = view.findViewById(R.id.scanProgress)
        scanProgressLabel = view.findViewById(R.id.scanProgressLabel)
        analystCard = view.findViewById(R.id.analystCard)
        analystHeadline = view.findViewById(R.id.analystHeadline)
        analystSummary = view.findViewById(R.id.analystSummary)
        analystRecommendations = view.findViewById(R.id.analystRecommendations)
        ringBattery = view.findViewById(R.id.ringBattery)
        ringRam = view.findViewById(R.id.ringRam)
        ringStorage = view.findViewById(R.id.ringStorage)
        securityChartCard = view.findViewById(R.id.securityChartCard)
        securityDonut = view.findViewById(R.id.securityDonut)
        legendOk = view.findViewById(R.id.legendOk)
        legendWarn = view.findViewById(R.id.legendWarn)
        legendDanger = view.findViewById(R.id.legendDanger)

        setCounterLabel(R.id.counterThreats, R.string.counter_threats)
        setCounterLabel(R.id.counterChecks, R.string.counter_checks)
        setCounterLabel(R.id.counterFiles, R.string.counter_files)
        setCounterLabel(R.id.counterEnergy, R.string.counter_energy)

        globalScanButton.setOnClickListener { runGlobalScan() }
        bindSwitches(view)
    }

    override fun onResume() {
        super.onResume()
        refreshBattery()
        refreshRings()
        startSelfUsageLoop()
    }

    /** Aggiorna gli anelli di stato: batteria, RAM e archiviazione. */
    private fun refreshRings() {
        val context = context ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val battery = withContext(Dispatchers.Default) { BatteryMonitor(context).snapshot() }
            val batteryColor = when {
                battery.levelPercent <= 20 -> colorOf(R.color.status_danger)
                battery.levelPercent <= 50 -> colorOf(R.color.status_warn)
                else -> colorOf(R.color.status_ok)
            }
            ringBattery.setValue(battery.levelPercent, batteryColor)

            val (ramPercent, storagePercent) = withContext(Dispatchers.Default) {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val memInfo = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
                val ramUsed = if (memInfo.totalMem > 0)
                    (100 - (memInfo.availMem * 100 / memInfo.totalMem)).toInt() else 0

                val stat = StatFs(Environment.getDataDirectory().path)
                val totalStorage = stat.totalBytes
                val storageUsed = if (totalStorage > 0)
                    (100 - (stat.availableBytes * 100 / totalStorage)).toInt() else 0

                ramUsed to storageUsed
            }
            ringRam.setValue(ramPercent, ringColorFor(ramPercent))
            ringStorage.setValue(storagePercent, ringColorFor(storagePercent))
        }
    }

    /** Verde sotto il 70%, ambra fino all'90%, rosso oltre: soglie di utilizzo risorse. */
    private fun ringColorFor(usedPercent: Int): Int = when {
        usedPercent >= 90 -> colorOf(R.color.status_danger)
        usedPercent >= 70 -> colorOf(R.color.status_warn)
        else -> colorOf(R.color.status_ok)
    }

    private fun colorOf(colorRes: Int): Int =
        ContextCompat.getColor(requireContext(), colorRes)

    override fun onPause() {
        selfUsageJob?.cancel()
        selfUsageJob = null
        super.onPause()
    }

    /**
     * Consumo in tempo reale di PhoneGuard stessa: CPU% (delta del tempo
     * CPU del processo sul tempo reale trascorso) e RAM (PSS), aggiornati
     * ogni 2 secondi finché la Dashboard è visibile.
     */
    private fun startSelfUsageLoop() {
        val context = requireContext().applicationContext
        selfUsageJob?.cancel()
        selfUsageJob = viewLifecycleOwner.lifecycleScope.launch {
            var lastCpu = Process.getElapsedCpuTime()
            var lastClock = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(2_000)
                val cpu = Process.getElapsedCpuTime()
                val clock = SystemClock.elapsedRealtime()
                val cpuPercent =
                    (cpu - lastCpu) * 100f / (clock - lastClock).coerceAtLeast(1)
                lastCpu = cpu
                lastClock = clock

                val ramMb = withContext(Dispatchers.Default) {
                    val am = context.getSystemService(Context.ACTIVITY_SERVICE)
                            as ActivityManager
                    val info = am.getProcessMemoryInfo(intArrayOf(Process.myPid()))
                    (info.firstOrNull()?.totalPss ?: 0) / 1024f
                }
                selfUsageText.text =
                    getString(R.string.self_usage, cpuPercent, ramMb)
            }
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

    /** Analisi Globale: minacce + sistema + file + energia in un tap. */
    private fun runGlobalScan() {
        val context = requireContext()
        globalScanButton.isEnabled = false
        globalScanButton.setText(R.string.global_scan_running)
        scanProgress.visibility = View.VISIBLE
        scanProgressLabel.visibility = View.VISIBLE
        scanProgress.setProgressCompat(0, false)

        viewLifecycleOwner.lifecycleScope.launch {
            setScanPhase(0, R.string.phase_threats)
            val threats = withContext(Dispatchers.Default) {
                ThreatScanner(context).scan()
            }

            setScanPhase(1, R.string.phase_system)
            val systemChecks = withContext(Dispatchers.Default) {
                SystemAnalyzer(context).analyze()
            }
            val failedChecks = systemChecks.count { !it.ok }

            setScanPhase(2, R.string.phase_files)
            val fileScanner = FileScanner(context)
            val suspiciousFiles = withContext(Dispatchers.IO) {
                if (fileScanner.hasStorageAccess()) fileScanner.scan().size else null
            }

            setScanPhase(3, R.string.phase_energy)
            val hasUsage = NetworkMonitor(context).hasUsageAccess()
            val energyHogs = withContext(Dispatchers.Default) {
                if (hasUsage) {
                    EnergyMonitor(context).topConsumers()
                        .count { it.usedForegroundService }
                } else null
            }
            val reclaimable = withContext(Dispatchers.IO) {
                var bytes = 0L
                if (JunkScanner(context).hasStorageAccess()) {
                    bytes += JunkScanner(context).scan().sumOf { it.sizeBytes }
                }
                if (hasUsage) {
                    val inv = AppInventory(context)
                    bytes += inv.totalCacheBytes(inv.list())
                }
                bytes
            }

            setScanPhase(4, R.string.phase_analyst)
            val report = withContext(Dispatchers.Default) {
                SecurityAnalyst.analyze(
                    threats, systemChecks, suspiciousFiles ?: 0, reclaimable
                )
            }

            scanProgress.setProgressCompat(5, true)
            scanProgressLabel.setText(R.string.phase_done)
            AppLog.log(
                context, "SCANSIONE",
                "Analisi Globale — verdetto ${report.verdict}: ${threats.size} app sospette, " +
                        "$failedChecks controlli falliti, " +
                        "${suspiciousFiles ?: "n/d"} file sospetti, " +
                        "${SecurityAnalyst.formatSize(reclaimable)} recuperabili"
            )

            setCounterValue(R.id.counterThreats, threats.size.toString())
            setCounterValue(R.id.counterChecks, failedChecks.toString())
            setCounterValue(R.id.counterFiles, suspiciousFiles?.toString() ?: "—")
            setCounterValue(R.id.counterEnergy, energyHogs?.toString() ?: "—")

            showAnalystReport(report)
            showSecurityDonut(
                threatsCritical = threats.count { it.riskLevel == RiskLevel.CRITICO },
                threatsWarning = threats.count { it.riskLevel == RiskLevel.ALTO || it.riskLevel == RiskLevel.SOSPETTO },
                threatsOk = 1, // il sistema stesso conta come una voce verificata
                checksOk = systemChecks.count { it.ok },
                checksFailed = failedChecks
            )
            refreshRings()
            when (report.verdict) {
                RiskLevel.CRITICO -> setStatus(
                    "🚨", R.string.status_critical,
                    getString(R.string.status_detail, threats.size, failedChecks),
                    R.color.status_danger
                )
                RiskLevel.ALTO, RiskLevel.SOSPETTO -> setStatus(
                    "⚠️", R.string.status_warnings,
                    getString(R.string.status_detail, threats.size, failedChecks),
                    R.color.status_warn
                )
                RiskLevel.SICURO -> setStatus(
                    "🛡️", R.string.status_ok,
                    getString(R.string.status_ok_detail),
                    R.color.status_ok
                )
            }

            refreshBattery()
            globalScanButton.isEnabled = true
            globalScanButton.setText(R.string.global_scan)
            delay(1_200)
            scanProgress.visibility = View.GONE
            scanProgressLabel.visibility = View.GONE
        }
    }

    private fun setScanPhase(step: Int, labelRes: Int) {
        scanProgress.setProgressCompat(step, true)
        scanProgressLabel.setText(labelRes)
    }

    private fun showAnalystReport(report: com.cybersentinel.phoneguard.data.AnalystReport) {
        val colorRes = when (report.verdict) {
            RiskLevel.CRITICO -> R.color.status_danger
            RiskLevel.ALTO, RiskLevel.SOSPETTO -> R.color.status_warn
            RiskLevel.SICURO -> R.color.status_ok
        }
        analystHeadline.text = "🤖 ${report.headline}"
        analystHeadline.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
        analystSummary.text = report.summary
        analystRecommendations.text =
            report.recommendations.joinToString("\n\n") { "• $it" }
        analystCard.visibility = View.VISIBLE
    }

    /**
     * Donut "ripartizione sicurezza": quante voci controllate sono OK,
     * da verificare o critiche, contando minacce + controlli di sistema.
     */
    private fun showSecurityDonut(
        threatsCritical: Int, threatsWarning: Int, threatsOk: Int,
        checksOk: Int, checksFailed: Int
    ) {
        val ok = threatsOk + checksOk
        val warn = threatsWarning
        val danger = threatsCritical + checksFailed
        val okColor = colorOf(R.color.status_ok)
        val warnColor = colorOf(R.color.status_warn)
        val dangerColor = colorOf(R.color.status_danger)

        securityDonut.setData(
            listOf(
                DonutChartView.Segment(ok.toFloat(), okColor),
                DonutChartView.Segment(warn.toFloat(), warnColor),
                DonutChartView.Segment(danger.toFloat(), dangerColor)
            ),
            centerLabel = "${ok + warn + danger}"
        )
        legendOk.text = getString(R.string.legend_ok, ok)
        legendWarn.text = getString(R.string.legend_warn, warn)
        legendDanger.text = getString(R.string.legend_danger, danger)
        legendOk.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(okColor)
        legendWarn.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(warnColor)
        legendDanger.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(dangerColor)
        securityChartCard.visibility = View.VISIBLE
    }

    private fun refreshBattery() {
        val context = context ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val snap = withContext(Dispatchers.Default) {
                BatteryMonitor(context).snapshot()
            }
            val base = getString(
                R.string.battery_summary,
                snap.levelPercent,
                if (snap.isCharging) getString(R.string.charging)
                else getString(R.string.discharging),
                snap.temperatureCelsius,
                snap.healthLabel,
                snap.estimatedWatts
            )
            // Stima autonomia: carica residua / corrente di scarica
            val estimate = if (!snap.isCharging &&
                snap.currentMicroAmpere < 0 && snap.chargeCounterMicroAmpereHour > 0
            ) {
                val hours = snap.chargeCounterMicroAmpereHour.toDouble() /
                        abs(snap.currentMicroAmpere).toDouble()
                val h = hours.toInt()
                val m = ((hours - h) * 60).toInt()
                "\n" + getString(R.string.battery_estimate, h, m)
            } else ""
            batteryText.text = base + estimate
        }
    }

    private fun setStatus(icon: String, titleRes: Int, detail: String, colorRes: Int) {
        statusIcon.text = icon
        statusTitle.setText(titleRes)
        statusTitle.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
        statusDetail.text = detail
    }

    private fun setCounterLabel(counterId: Int, labelRes: Int) {
        view?.findViewById<View>(counterId)
            ?.findViewById<TextView>(R.id.counterLabel)?.setText(labelRes)
    }

    private fun setCounterValue(counterId: Int, value: String) {
        view?.findViewById<View>(counterId)
            ?.findViewById<TextView>(R.id.counterValue)?.text = value
    }
}
