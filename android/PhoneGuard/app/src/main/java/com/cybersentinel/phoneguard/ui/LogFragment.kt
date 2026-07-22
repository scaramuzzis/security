package com.cybersentinel.phoneguard.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.cybersentinel.phoneguard.R
import com.cybersentinel.phoneguard.data.Prefs
import com.cybersentinel.phoneguard.ui.base.RefreshableFragment
import com.cybersentinel.phoneguard.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pagina Registro attività: visualizza gli eventi registrati (attivabili
 * dalle Impostazioni), con Aggiorna e Svuota.
 */
class LogFragment : RefreshableFragment(R.layout.fragment_log) {

    private lateinit var logText: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        swipeRefresh.setOnRefreshListener { refresh() }
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
        swipeRefresh.isRefreshing = true
        val context = requireContext()
        if (!Prefs.loggingEnabled(context)) {
            logText.setText(R.string.log_disabled)
            endRefresh()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val content = withContext(Dispatchers.IO) { AppLog.read(context) }
            logText.text = content.ifBlank { getString(R.string.log_empty) }
            endRefresh()
        }
    }
}
