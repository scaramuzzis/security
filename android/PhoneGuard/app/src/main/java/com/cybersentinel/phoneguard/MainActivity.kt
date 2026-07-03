package com.cybersentinel.phoneguard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.cybersentinel.phoneguard.data.Prefs
import com.cybersentinel.phoneguard.monitor.MonitorService
import com.cybersentinel.phoneguard.ui.DashboardFragment
import com.cybersentinel.phoneguard.ui.EnergyFragment
import com.cybersentinel.phoneguard.ui.SecurityFragment
import com.cybersentinel.phoneguard.ui.SettingsFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Shell di navigazione: Bottom Navigation a 4 sezioni stabili
 * (Dashboard, Sicurezza, Energia, Impostazioni).
 */
class MainActivity : AppCompatActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_security -> SecurityFragment()
                R.id.nav_energy -> EnergyFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> DashboardFragment()
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit()
            true
        }

        if (savedInstanceState == null) {
            nav.selectedItemId = R.id.nav_dashboard
        }

        askNotificationPermission()
        if (Prefs.monitoringEnabled(this)) {
            MonitorService.start(this)
        }
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
