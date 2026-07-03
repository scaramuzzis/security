package com.cybersentinel.phoneguard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import com.cybersentinel.phoneguard.monitor.BatteryMonitor
import com.cybersentinel.phoneguard.monitor.MonitorService
import com.cybersentinel.phoneguard.monitor.NetworkMonitor
import com.cybersentinel.phoneguard.ui.AppUsageAdapter
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var batteryMonitor: BatteryMonitor
    private lateinit var adapter: AppUsageAdapter

    private lateinit var batteryText: TextView
    private lateinit var permissionButton: MaterialButton
    private lateinit var swipeRefresh: SwipeRefreshLayout

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        networkMonitor = NetworkMonitor(this)
        batteryMonitor = BatteryMonitor(this)
        adapter = AppUsageAdapter()

        batteryText = findViewById(R.id.batteryText)
        permissionButton = findViewById(R.id.permissionButton)
        swipeRefresh = findViewById(R.id.swipeRefresh)

        val list = findViewById<RecyclerView>(R.id.appList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        permissionButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        swipeRefresh.setOnRefreshListener { refresh() }

        askNotificationPermission()
        startMonitorService()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val hasAccess = networkMonitor.hasUsageAccess()
        permissionButton.visibility = if (hasAccess) View.GONE else View.VISIBLE

        lifecycleScope.launch {
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

            if (hasAccess) {
                val now = System.currentTimeMillis()
                val usage = withContext(Dispatchers.IO) {
                    // Traffico delle ultime 24 ore
                    networkMonitor.queryUsage(now - 24L * 60 * 60 * 1000, now)
                }
                adapter.submit(usage, MonitorService.TX_ALERT_THRESHOLD_BYTES)
            }
            swipeRefresh.isRefreshing = false
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
