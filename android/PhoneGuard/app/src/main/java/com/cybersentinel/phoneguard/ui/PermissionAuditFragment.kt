package com.cybersentinel.phoneguard.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.cybersentinel.phoneguard.R
import com.cybersentinel.phoneguard.monitor.PermissionAuditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pagina Audit permessi: raggruppa le app per permessi critici concessi. */
class PermissionAuditFragment : Fragment(R.layout.fragment_audit) {

    private lateinit var auditText: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        auditText = view.findViewById(R.id.auditText)
    }

    override fun onResume() {
        super.onResume()
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val audit = withContext(Dispatchers.Default) { PermissionAuditor(context).audit() }
            auditText.text = audit.joinToString("\n\n") { group ->
                val header = "${group.title} — ${group.description}"
                if (group.apps.isEmpty()) "✅ $header\n   ${getString(R.string.audit_none)}"
                else "⚠️ $header\n   ${group.apps.joinToString(", ")}"
            }
        }
    }
}
