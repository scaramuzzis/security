package com.cybersentinel.phoneguard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.View
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
import com.cybersentinel.phoneguard.util.UsageTracker
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView

/**
 * Shell dell'app: Dashboard sempre visibile come home, con menu ad hamburger
 * che raccoglie per categoria l'accesso a ogni singola funzione, ognuna
 * mostrata nella propria pagina separata (Activity dedicata).
 *
 * In cima al menu, sotto Dashboard, una sezione "Più usati" mostra le 5
 * funzioni aperte più spesso: si aggiorna a ogni utilizzo (il conteggio è
 * incrementato subito) e viene ricostruita ogni volta che il drawer si
 * apre, così è sempre coerente con l'uso più recente.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawerLayout)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { drawerLayout.openDrawer(Gravity.START) }

        navView = findViewById(R.id.navView)
        navView.setNavigationItemSelectedListener { item ->
            recordUse(item.itemId)
            handleNavItem(item.itemId)
            drawerLayout.closeDrawers()
            true
        }

        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                refreshMostUsed()
            }
        })

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

    /** Registra l'uso, tranne la Dashboard: è già sempre visibile, non serve segnalarla come "più usata". */
    private fun recordUse(itemId: Int) {
        if (itemId == R.id.nav_dashboard) return
        UsageTracker.recordUse(this, itemId)
    }

    /**
     * Ricostruisce la sezione dinamica "Più usati" con le 5 funzioni più
     * aperte finora, copiando titolo e icona dalla voce statica originale.
     */
    private fun refreshMostUsed() {
        val menu = navView.menu
        menu.removeGroup(GROUP_MOST_USED)

        val topIds = UsageTracker.topUsed(this, MOST_USED_COUNT)
        if (topIds.isEmpty()) return

        val subMenu = menu.addSubMenu(GROUP_MOST_USED, Menu.NONE, ORDER_MOST_USED, getString(R.string.category_most_used))
        topIds.forEachIndexed { index, id ->
            val original = menu.findItem(id) ?: return@forEachIndexed
            subMenu.add(GROUP_MOST_USED, id, index, original.title).setIcon(original.icon)
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

    companion object {
        private const val GROUP_MOST_USED = 1001
        private const val ORDER_MOST_USED = 20
        private const val MOST_USED_COUNT = 5
    }
}
