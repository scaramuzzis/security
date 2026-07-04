package com.cybersentinel.phoneguard.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cybersentinel.phoneguard.R
import com.cybersentinel.phoneguard.monitor.AppPermissionScanner
import com.cybersentinel.phoneguard.ui.base.RefreshableFragment
import com.cybersentinel.phoneguard.util.SystemIntents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pagina "Autorizzazioni app": raggruppa le app per le autorizzazioni che
 * si riconoscono subito — fotocamera, microfono, posizione, contatti, SMS,
 * telefono, archiviazione, calendario, sensori corporei. Il tocco su
 * un'app apre la sua scheda di sistema, dove il permesso si revoca.
 */
class AppPermissionsFragment : RefreshableFragment(R.layout.fragment_app_permissions) {

    private lateinit var adapter: AppPermissionAdapter
    private lateinit var emptyText: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        emptyText = view.findViewById(R.id.permissionsEmpty)
        adapter = AppPermissionAdapter(
            onOpenDetails = { SystemIntents.openAppDetails(requireContext(), it.packageName) }
        )
        view.findViewById<RecyclerView>(R.id.permissionsList).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@AppPermissionsFragment.adapter
        }
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
            val categories = withContext(Dispatchers.Default) { AppPermissionScanner(context).scan() }
            val hasAny = categories.any { it.apps.isNotEmpty() }
            emptyText.visibility = if (hasAny) View.GONE else View.VISIBLE
            adapter.submit(categories, context)
            endRefresh()
        }
    }
}
