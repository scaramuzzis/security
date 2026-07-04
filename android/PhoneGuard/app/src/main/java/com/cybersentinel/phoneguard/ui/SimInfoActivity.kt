package com.cybersentinel.phoneguard.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cybersentinel.phoneguard.R
import com.cybersentinel.phoneguard.data.SimInfo
import com.cybersentinel.phoneguard.monitor.SimMonitor
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pagina delle schede SIM installate (fisiche ed eSIM).
 *
 * Un'app normale non può modificare roaming dati o SIM predefinite — sono
 * policy di sistema protette da `MODIFY_PHONE_STATE` (riservato alle app di
 * sistema/operatore). Questa pagina mostra lo stato reale di ogni SIM e
 * apre le schermate di sistema corrette per modificarlo.
 */
class SimInfoActivity : AppCompatActivity() {

    private lateinit var adapter: SimAdapter
    private lateinit var header: TextView
    private lateinit var permissionButton: MaterialButton

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sim_info)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.sim_title_page)

        header = findViewById(R.id.simHeader)
        permissionButton = findViewById(R.id.simPermissionButton)
        permissionButton.setOnClickListener {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_PHONE_NUMBERS
                )
            )
        }
        findViewById<MaterialButton>(R.id.simManageButton).setOnClickListener {
            openSystemSimSettings()
        }

        adapter = SimAdapter(onOpenSettings = { openNetworkSettings() })
        findViewById<RecyclerView>(R.id.simList).apply {
            layoutManager = LinearLayoutManager(this@SimInfoActivity)
            adapter = this@SimInfoActivity.adapter
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun refresh() {
        val monitor = SimMonitor(this)
        val hasPermission = monitor.hasPermission()
        permissionButton.visibility = if (hasPermission) View.GONE else View.VISIBLE

        if (!hasPermission) {
            header.setText(R.string.sim_permission_needed)
            adapter.submitList(emptyList())
            return
        }

        lifecycleScope.launch {
            val sims = withContext(Dispatchers.Default) { monitor.list() }
            adapter.submitList(sims)
            header.text = if (sims.isEmpty()) getString(R.string.sim_none)
            else getString(R.string.sim_count, sims.size)
        }
    }

    private fun openNetworkSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS))
        }.onFailure { openSystemSimSettings() }
    }

    private fun openSystemSimSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        }
    }
}
