package com.cybersentinel.phoneguard.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cybersentinel.phoneguard.R
import com.cybersentinel.phoneguard.data.AppThreat
import com.cybersentinel.phoneguard.monitor.ThreatScanner
import com.cybersentinel.phoneguard.util.SystemIntents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pagina anti-spyware: app sospette e nascoste, con disinstallazione. */
class ThreatsFragment : Fragment(R.layout.fragment_threats) {

    private lateinit var threatAdapter: ThreatAdapter
    private lateinit var threatsEmpty: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        threatsEmpty = view.findViewById(R.id.threatsEmpty)
        threatAdapter = ThreatAdapter(onUninstall = ::uninstallApp, onAppInfo = ::openAppDetails)
        view.findViewById<RecyclerView>(R.id.threatList).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = threatAdapter
        }
    }

    override fun onResume() {
        super.onResume()
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val threats = withContext(Dispatchers.Default) { ThreatScanner(context).scan() }
            threatAdapter.submitList(threats)
            threatsEmpty.text = if (threats.isEmpty()) getString(R.string.threats_none)
            else getString(R.string.threats_found, threats.size)
        }
    }

    private fun uninstallApp(threat: AppThreat) = SystemIntents.uninstallApp(requireContext(), threat.packageName)

    private fun openAppDetails(threat: AppThreat) =
        SystemIntents.openAppDetails(requireContext(), threat.packageName)
}
