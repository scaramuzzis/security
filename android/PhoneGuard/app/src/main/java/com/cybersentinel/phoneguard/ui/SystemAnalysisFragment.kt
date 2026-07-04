package com.cybersentinel.phoneguard.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.cybersentinel.phoneguard.R
import com.cybersentinel.phoneguard.data.SecurityCheck
import com.cybersentinel.phoneguard.monitor.SystemAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pagina Analisi di sistema: root, MDM, VPN, ADB, blocco schermo, sideload. */
class SystemAnalysisFragment : Fragment(R.layout.fragment_system) {

    private lateinit var systemText: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        systemText = view.findViewById(R.id.systemText)
    }

    override fun onResume() {
        super.onResume()
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val checks = withContext(Dispatchers.Default) { SystemAnalyzer(context).analyze() }
            systemText.text = formatChecks(checks)
        }
    }

    private fun formatChecks(checks: List<SecurityCheck>): CharSequence =
        checks.joinToString("\n\n") { check ->
            val icon = if (check.ok) "✅" else "⚠️"
            "$icon ${check.title}: ${check.detail}"
        }
}
