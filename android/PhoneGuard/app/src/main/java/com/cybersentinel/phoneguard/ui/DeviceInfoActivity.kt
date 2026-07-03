package com.cybersentinel.phoneguard.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cybersentinel.phoneguard.R
import com.cybersentinel.phoneguard.monitor.DeviceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pagina con la versione di Android e tutte le informazioni sul dispositivo. */
class DeviceInfoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_info)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.device_info_title)

        val list = findViewById<RecyclerView>(R.id.infoList)
        list.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch {
            val rows = withContext(Dispatchers.Default) { DeviceInfo(this@DeviceInfoActivity).collect() }
            list.adapter = DeviceInfoAdapter(rows)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
