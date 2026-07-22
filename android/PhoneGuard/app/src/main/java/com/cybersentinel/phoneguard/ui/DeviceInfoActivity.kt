package com.cybersentinel.phoneguard.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.cybersentinel.phoneguard.R
import com.cybersentinel.phoneguard.monitor.DeviceInfo
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pagina con la versione di Android e tutte le informazioni sul dispositivo. */
class DeviceInfoActivity : AppCompatActivity() {

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var list: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_info)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.device_info_title)

        swipeRefresh = findViewById(R.id.swipeRefresh)
        swipeRefresh.setColorSchemeResources(R.color.primary)
        swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.cyber_surface_variant)
        swipeRefresh.setOnRefreshListener { loadDeviceInfo() }
        list = findViewById(R.id.infoList)
        list.layoutManager = LinearLayoutManager(this)

        loadDeviceInfo()
    }

    private fun loadDeviceInfo() {
        swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            val rows = withContext(Dispatchers.Default) { DeviceInfo(this@DeviceInfoActivity).collect() }
            list.adapter = DeviceInfoAdapter(rows)
            swipeRefresh.isRefreshing = false
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
