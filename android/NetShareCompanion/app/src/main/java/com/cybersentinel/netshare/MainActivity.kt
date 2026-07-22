package com.cybersentinel.netshare

import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cybersentinel.netshare.data.SmbEntry
import com.cybersentinel.netshare.ui.SmbEntryAdapter
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Browser di cartelle condivise in rete (SMB/Samba).
 *
 * App companion di PhoneGuard: è separata di proposito perché richiede il
 * permesso INTERNET, che PhoneGuard non ha per garantire di non poter
 * inviare dati all'esterno.
 */
class MainActivity : AppCompatActivity() {

    private val browser = SmbBrowser()
    private lateinit var adapter: SmbEntryAdapter

    private lateinit var host: TextInputEditText
    private lateinit var shareName: TextInputEditText
    private lateinit var username: TextInputEditText
    private lateinit var password: TextInputEditText
    private lateinit var domain: TextInputEditText
    private lateinit var guest: MaterialCheckBox
    private lateinit var connectButton: MaterialButton
    private lateinit var upButton: MaterialButton
    private lateinit var pathLabel: TextView
    private lateinit var status: TextView
    private lateinit var progress: LinearProgressIndicator
    private lateinit var connectPanel: View
    private lateinit var browsePanel: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        host = findViewById(R.id.host)
        shareName = findViewById(R.id.shareName)
        username = findViewById(R.id.username)
        password = findViewById(R.id.password)
        domain = findViewById(R.id.domain)
        guest = findViewById(R.id.guest)
        connectButton = findViewById(R.id.connectButton)
        upButton = findViewById(R.id.upButton)
        pathLabel = findViewById(R.id.pathLabel)
        status = findViewById(R.id.status)
        progress = findViewById(R.id.progress)
        connectPanel = findViewById(R.id.connectPanel)
        browsePanel = findViewById(R.id.browsePanel)

        adapter = SmbEntryAdapter(onClick = ::onEntryClicked)
        findViewById<RecyclerView>(R.id.entryList).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        connectButton.setOnClickListener { connect() }
        upButton.setOnClickListener { goUp() }
        findViewById<MaterialButton>(R.id.disconnectButton).setOnClickListener { disconnect() }
    }

    override fun onDestroy() {
        lifecycleScope.launch(Dispatchers.IO) { browser.close() }
        super.onDestroy()
    }

    private fun connect() {
        val hostValue = host.text?.toString()?.trim().orEmpty()
        val shareValue = shareName.text?.toString()?.trim().orEmpty()
        if (hostValue.isEmpty() || shareValue.isEmpty()) {
            status.text = getString(R.string.error_missing_host)
            return
        }
        setBusy(true)
        status.text = getString(R.string.connecting)

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    browser.connect(
                        host = hostValue,
                        shareName = shareValue,
                        username = username.text?.toString().orEmpty(),
                        password = password.text?.toString().orEmpty(),
                        domain = domain.text?.toString().orEmpty(),
                        guest = guest.isChecked
                    )
                    browser.list()
                }
            }
            setBusy(false)
            result.onSuccess { entries ->
                connectPanel.visibility = View.GONE
                browsePanel.visibility = View.VISIBLE
                showEntries(entries)
            }.onFailure { e ->
                status.text = getString(R.string.error_connect, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun onEntryClicked(entry: SmbEntry) {
        if (entry.isDirectory) {
            setBusy(true)
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { browser.enterDirectory(entry.name); browser.list() }
                }
                setBusy(false)
                result.onSuccess { showEntries(it) }
                    .onFailure { status.text = getString(R.string.error_list, it.message ?: "") }
            }
        } else {
            downloadFile(entry)
        }
    }

    private fun downloadFile(entry: SmbEntry) {
        setBusy(true)
        status.text = getString(R.string.downloading, entry.name)
        lifecycleScope.launch {
            val dest = File(
                getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), entry.name
            )
            val result = withContext(Dispatchers.IO) {
                runCatching { browser.download(entry.name, dest); dest }
            }
            setBusy(false)
            result.onSuccess {
                status.text = getString(R.string.downloaded, it.absolutePath)
                Toast.makeText(this@MainActivity, R.string.download_ok, Toast.LENGTH_SHORT).show()
            }.onFailure {
                status.text = getString(R.string.error_download, it.message ?: "")
            }
        }
    }

    private fun goUp() {
        setBusy(true)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                if (browser.goUp()) runCatching { browser.list() } else null
            }
            setBusy(false)
            result?.onSuccess { showEntries(it) }
        }
    }

    private fun disconnect() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { browser.close() }
            browsePanel.visibility = View.GONE
            connectPanel.visibility = View.VISIBLE
            adapter.submitList(emptyList())
            status.text = ""
        }
    }

    private fun showEntries(entries: List<SmbEntry>) {
        adapter.submitList(entries)
        val path = browser.currentPath.ifEmpty { "/" }
        pathLabel.text = getString(R.string.path, path)
        upButton.isEnabled = browser.currentPath.isNotEmpty()
        status.text = getString(R.string.entries_count, entries.size)
    }

    private fun setBusy(busy: Boolean) {
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        connectButton.isEnabled = !busy
    }
}
