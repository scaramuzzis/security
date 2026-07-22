package com.cybersentinel.phoneguard.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cybersentinel.phoneguard.R
import com.cybersentinel.phoneguard.data.AppUpdateInfo
import com.cybersentinel.phoneguard.monitor.UpdateChecker
import com.cybersentinel.phoneguard.ui.base.RefreshableFragment
import com.cybersentinel.phoneguard.util.SystemIntents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pagina App da aggiornare: ordinate per anzianità, con tasto Aggiorna. */
class UpdatesFragment : RefreshableFragment(R.layout.fragment_updates) {

    private lateinit var updateAdapter: UpdateAdapter
    private lateinit var updatesHeader: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updatesHeader = view.findViewById(R.id.updatesHeader)
        updateAdapter = UpdateAdapter(onUpdate = ::openPlayStore)
        view.findViewById<RecyclerView>(R.id.updateList).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = updateAdapter
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
            val updates = withContext(Dispatchers.Default) { UpdateChecker(context).list() }
            updateAdapter.submitList(updates)
            val stale = updates.count { it.stale }
            updatesHeader.text = when {
                updates.isEmpty() -> getString(R.string.updates_none)
                stale > 0 -> getString(R.string.updates_stale, stale)
                else -> getString(R.string.updates_ok, updates.size)
            }
            endRefresh()
        }
    }

    private fun openPlayStore(app: AppUpdateInfo) =
        SystemIntents.openPlayStoreListing(requireContext(), app.packageName)
}
