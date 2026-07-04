package com.cybersentinel.phoneguard.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.cybersentinel.phoneguard.R
import com.cybersentinel.phoneguard.data.Prefs
import com.cybersentinel.phoneguard.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pagina Registro attività: visualizza gli eventi registrati (attivabili
 * dalle Impostazioni), con Aggiorna e Svuota.
 */
class LogFragment : Fragment(R.layout.fragment_log) {

    private lateinit var logText: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        logText = view.findViewById(R.id.logText)
        view.findViewById<View>(R.id.logRefreshButton).setOnClickListener { refresh() }
        view.findViewById<View>(R.id.logClearButton).setOnClickListener {
            val context = requireContext()
            viewLifecycleOwner.lifecycleScope.launch {
                withContext(Dispatchers.IO) { AppLog.clear(context) }
                refresh()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val context = requireContext()
        if (!Prefs.loggingEnabled(context)) {
            logText.setText(R.string.log_disabled)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val content = withContext(Dispatchers.IO) { AppLog.read(context) }
            logText.text = content.ifBlank { getString(R.string.log_empty) }
        }
    }
}
