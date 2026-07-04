package com.cybersentinel.phoneguard.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.cybersentinel.phoneguard.R
import com.cybersentinel.phoneguard.monitor.PermissionAuditor
import com.cybersentinel.phoneguard.ui.base.RefreshableFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pagina Audit permessi: raggruppa le app per permessi critici concessi. */
class PermissionAuditFragment : RefreshableFragment(R.layout.fragment_audit) {

    private lateinit var auditText: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auditText = view.findViewById(R.id.auditText)
        swipeRefresh.setOnRefreshListener { refresh() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        swipeRefresh.isRefreshing = true
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val audit = withContext(Dispatchers.Default) { PermissionAuditor(context).audit() }
            auditText.text = audit.joinToString("\n\n") { group ->
                val header = "${group.title} — ${group.description}"
                if (group.apps.isEmpty()) "✅ $header\n   ${getString(R.string.audit_none)}"
                else "⚠️ $header\n   ${group.apps.joinToString(", ")}"
            }
            endRefresh()
        }
    }
}
