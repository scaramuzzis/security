package com.cybersentinel.phoneguard.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.cybersentinel.phoneguard.R
import com.google.android.material.appbar.MaterialToolbar

/**
 * Host generico per le pagine raggiunte dal menu ad hamburger: ogni voce del
 * drawer apre questa Activity con la propria sezione, così ogni funzione ha
 * la sua pagina separata con titolo e freccia "indietro" dedicati.
 */
class SectionHostActivity : AppCompatActivity() {

    enum class Section(val titleRes: Int, val fragmentFactory: () -> Fragment) {
        INTERCEPTION(R.string.nav_interception, ::InterceptionFragment),
        THREATS(R.string.nav_threats, ::ThreatsFragment),
        AUDIT(R.string.nav_audit, ::PermissionAuditFragment),
        APP_PERMISSIONS(R.string.nav_app_permissions, ::AppPermissionsFragment),
        SYSTEM(R.string.nav_system, ::SystemAnalysisFragment),
        FILES(R.string.nav_files, ::SuspiciousFilesFragment),
        WIFI(R.string.nav_wifi, ::WifiFragment),
        NETWORK(R.string.nav_network, ::NetworkUsageFragment),
        UPDATES(R.string.nav_updates, ::UpdatesFragment),
        DEVICE_HEALTH(R.string.nav_device_health, ::DeviceHealthFragment),
        RUNNING(R.string.nav_running, ::RunningAppsFragment),
        ENERGY_USAGE(R.string.nav_energy_usage, ::EnergyUsageFragment),
        CLEAN(R.string.nav_clean, ::CleanSpaceFragment),
        INVENTORY(R.string.nav_inventory, ::AppInventoryFragment),
        SETTINGS(R.string.nav_settings, ::SettingsFragment),
        LOG(R.string.nav_log, ::LogFragment),
        ABOUT(R.string.nav_about, ::AboutFragment)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_section_host)

        val sectionName = intent.getStringExtra(EXTRA_SECTION)
        val section = runCatching { Section.valueOf(sectionName ?: "") }.getOrNull()
            ?: return finish()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(section.titleRes)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.sectionContainer, section.fragmentFactory())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        private const val EXTRA_SECTION = "section"

        /** Mappa l'id del menu drawer alla sezione corrispondente, se esiste. */
        fun sectionFor(navItemId: Int): Section? = when (navItemId) {
            R.id.nav_interception -> Section.INTERCEPTION
            R.id.nav_threats -> Section.THREATS
            R.id.nav_audit -> Section.AUDIT
            R.id.nav_app_permissions -> Section.APP_PERMISSIONS
            R.id.nav_system -> Section.SYSTEM
            R.id.nav_files -> Section.FILES
            R.id.nav_wifi -> Section.WIFI
            R.id.nav_network -> Section.NETWORK
            R.id.nav_updates -> Section.UPDATES
            R.id.nav_device_health -> Section.DEVICE_HEALTH
            R.id.nav_running -> Section.RUNNING
            R.id.nav_energy_usage -> Section.ENERGY_USAGE
            R.id.nav_clean -> Section.CLEAN
            R.id.nav_inventory -> Section.INVENTORY
            R.id.nav_settings -> Section.SETTINGS
            R.id.nav_log -> Section.LOG
            R.id.nav_about -> Section.ABOUT
            else -> null
        }

        fun intentFor(context: Context, section: Section): Intent =
            Intent(context, SectionHostActivity::class.java)
                .putExtra(EXTRA_SECTION, section.name)
    }
}
