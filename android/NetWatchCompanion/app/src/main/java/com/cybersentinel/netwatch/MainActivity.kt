package com.cybersentinel.netwatch

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cybersentinel.netwatch.data.DomainLogStore
import com.cybersentinel.netwatch.ui.DomainLogAdapter
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * App companion di PhoneGuard per vedere quali domini contattano le altre
 * app installate. Separata di proposito: richiede il permesso INTERNET (per
 * inoltrare le query DNS a un resolver reale) che PhoneGuard non ha e non
 * avrà mai, per garantire di non poter inviare dati all'esterno.
 *
 * Intercetta SOLO le interrogazioni DNS (vedi NetWatchVpnService): le app
 * che usano DNS-over-HTTPS/TLS proprio non compaiono nel log.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var toggleButton: MaterialButton
    private lateinit var statusText: TextView
    private lateinit var clearButton: MaterialButton
    private lateinit var adapter: DomainLogAdapter
    private var refreshJob: Job? = null

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) startProtection()
        else updateStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toggleButton = findViewById(R.id.toggleButton)
        statusText = findViewById(R.id.statusText)
        clearButton = findViewById(R.id.clearButton)
        adapter = DomainLogAdapter()
        findViewById<RecyclerView>(R.id.logList).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        toggleButton.setOnClickListener { toggleProtection() }
        clearButton.setOnClickListener {
            DomainLogStore.clear()
            adapter.submit(emptyList())
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        refreshJob = lifecycleScope.launch {
            while (isActive) {
                adapter.submit(DomainLogStore.snapshot())
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    override fun onPause() {
        refreshJob?.cancel()
        super.onPause()
    }

    private fun toggleProtection() {
        if (NetWatchVpnService.isRunning) {
            stopService(Intent(this, NetWatchVpnService::class.java).setAction(NetWatchVpnService.ACTION_STOP))
            updateStatus()
            return
        }
        val consentIntent = VpnService.prepare(this)
        if (consentIntent != null) vpnPermissionLauncher.launch(consentIntent) else startProtection()
    }

    private fun startProtection() {
        startService(Intent(this, NetWatchVpnService::class.java))
        updateStatus()
    }

    private fun updateStatus() {
        val running = NetWatchVpnService.isRunning
        statusText.setText(if (running) R.string.status_active else R.string.status_inactive)
        toggleButton.setText(if (running) R.string.stop_protection else R.string.start_protection)
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 2000L
    }
}
