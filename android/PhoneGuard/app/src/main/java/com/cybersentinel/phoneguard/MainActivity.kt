package com.cybersentinel.phoneguard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.cybersentinel.phoneguard.data.Prefs
import com.cybersentinel.phoneguard.monitor.MonitorService
import com.cybersentinel.phoneguard.ui.DeviceInfoActivity
import com.cybersentinel.phoneguard.ui.FileManagerActivity
import com.cybersentinel.phoneguard.ui.SectionHostActivity
import com.cybersentinel.phoneguard.ui.SimInfoActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView

/**
 * Shell dell'app: Dashboard sempre visibile come home, con menu ad hamburger
 * che raccoglie per categoria l'accesso a ogni singola funzione, ognuna
 * mostrata nella propria pagina separata (Activity dedicata).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawerLayout)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { drawerLayout.openDrawer(Gravity.START) }

        val navView = findViewById<NavigationView>(R.id.navView)
        navView.setNavigationItemSelectedListener { item ->
            handleNavItem(item.itemId)
            drawerLayout.closeDrawers()
            true
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(Gravity.START)) {
                    drawerLayout.closeDrawers()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        askNotificationPermission()
        if (Prefs.monitoringEnabled(this)) {
            MonitorService.start(this)
        }
    }

    private fun handleNavItem(itemId: Int) {
        when (itemId) {
            R.id.nav_dashboard -> { /* già la home mostrata nel container */ }
            R.id.nav_device_info -> startActivity(Intent(this, DeviceInfoActivity::class.java))
            R.id.nav_sim_info -> startActivity(Intent(this, SimInfoActivity::class.java))
            R.id.nav_file_manager -> startActivity(Intent(this, FileManagerActivity::class.java))
            else -> {
                val section = SectionHostActivity.sectionFor(itemId) ?: return
                startActivity(SectionHostActivity.intentFor(this, section))
            }
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
