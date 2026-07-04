package com.cybersentinel.aidiag

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cybersentinel.aidiag.ai.AiClient
import com.cybersentinel.aidiag.ai.ApiKeyStore
import com.cybersentinel.aidiag.ai.PromptBuilder
import com.cybersentinel.aidiag.analysis.DiagnosticEngine
import com.cybersentinel.aidiag.analysis.ReportFormatter
import com.cybersentinel.aidiag.monitor.SamplingService
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * App companion di PhoneGuard: analisi con IA di segnali già raccolti in
 * locale (batteria, rete, permessi privilegiati), incrociati nel tempo per
 * ridurre i falsi positivi. Separata di proposito: richiede INTERNET per
 * chiamare il modello, che PhoneGuard non ha e non avrà mai.
 *
 * Limite dichiarato: nessun controllo qui può rilevare un impianto a livello
 * kernel/root che manipoli i dati letti da queste stesse API — vale lo
 * stesso limite di qualunque analisi in user-space, IA compresa: se i dati
 * di partenza sono falsificati, nessuna sintesi può accorgersene.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var monitoringSwitch: MaterialSwitch
    private lateinit var usageAccessButton: MaterialButton
    private lateinit var apiKeyInput: TextInputEditText
    private lateinit var modelInput: TextInputEditText
    private lateinit var saveKeyButton: MaterialButton
    private lateinit var runCheckButton: MaterialButton
    private lateinit var progress: LinearProgressIndicator
    private lateinit var aiSummaryCard: View
    private lateinit var aiSummaryText: TextView
    private lateinit var rawReportText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        monitoringSwitch = findViewById(R.id.monitoringSwitch)
        usageAccessButton = findViewById(R.id.usageAccessButton)
        apiKeyInput = findViewById(R.id.apiKeyInput)
        modelInput = findViewById(R.id.modelInput)
        saveKeyButton = findViewById(R.id.saveKeyButton)
        runCheckButton = findViewById(R.id.runCheckButton)
        progress = findViewById(R.id.progress)
        aiSummaryCard = findViewById(R.id.aiSummaryCard)
        aiSummaryText = findViewById(R.id.aiSummaryText)
        rawReportText = findViewById(R.id.rawReportText)

        apiKeyInput.setText(ApiKeyStore.apiKey(this).orEmpty())
        modelInput.setText(ApiKeyStore.model(this))

        monitoringSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) SamplingService.start(this) else SamplingService.stop(this)
        }
        usageAccessButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        saveKeyButton.setOnClickListener { saveApiKey() }
        runCheckButton.setOnClickListener { runCheck() }
    }

    override fun onResume() {
        super.onResume()
        usageAccessButton.visibility = if (hasUsageAccess()) View.GONE else View.VISIBLE
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun saveApiKey() {
        val key = apiKeyInput.text?.toString().orEmpty()
        val model = modelInput.text?.toString()?.trim().orEmpty()
        ApiKeyStore.setApiKey(this, key)
        if (model.isNotEmpty()) ApiKeyStore.setModel(this, model)
        Toast.makeText(this, R.string.key_saved, Toast.LENGTH_SHORT).show()
    }

    private fun runCheck() {
        runCheckButton.isEnabled = false
        progress.visibility = View.VISIBLE
        aiSummaryCard.visibility = View.GONE
        rawReportText.text = ""

        lifecycleScope.launch {
            val report = withContext(Dispatchers.Default) { DiagnosticEngine(this@MainActivity).buildReport() }
            rawReportText.text = withContext(Dispatchers.Default) { ReportFormatter.format(report) }

            val apiKey = ApiKeyStore.apiKey(this@MainActivity)
            if (apiKey != null) {
                val model = ApiKeyStore.model(this@MainActivity)
                val prompt = PromptBuilder.build(report)
                val result = withContext(Dispatchers.IO) {
                    runCatching { AiClient(apiKey, model).analyze(prompt) }
                }
                result.onSuccess { summary ->
                    aiSummaryText.text = summary
                    aiSummaryCard.visibility = View.VISIBLE
                }.onFailure { error ->
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.ai_error, error.message ?: error.javaClass.simpleName),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            progress.visibility = View.GONE
            runCheckButton.isEnabled = true
        }
    }
}
