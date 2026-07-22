package com.cybersentinel.phoneguard.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cybersentinel.phoneguard.R
import com.cybersentinel.phoneguard.data.JunkCategory
import com.cybersentinel.phoneguard.data.JunkItem
import com.cybersentinel.phoneguard.monitor.JunkScanner
import com.cybersentinel.phoneguard.monitor.SecurityAnalyst
import com.cybersentinel.phoneguard.ui.base.RefreshableFragment
import com.cybersentinel.phoneguard.util.AppLog
import com.cybersentinel.phoneguard.util.LogCategory
import com.cybersentinel.phoneguard.util.SystemIntents
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pagina Pulizia spazio: file inutili su memoria interna + microSD, selezionabili. */
class CleanSpaceFragment : RefreshableFragment(R.layout.fragment_clean) {

    private lateinit var junkAdapter: JunkAdapter
    private lateinit var cleanSummary: TextView
    private lateinit var cleanProgress: LinearProgressIndicator
    private lateinit var scanJunkButton: MaterialButton
    private lateinit var cleanButton: MaterialButton
    private lateinit var selectAll: MaterialCheckBox

    private var junkFound: List<JunkItem> = emptyList()
    private val selectedJunk = LinkedHashSet<String>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        swipeRefresh.setOnRefreshListener {
            if (JunkScanner(requireContext()).hasStorageAccess()) scanJunk() else endRefresh()
        }
        cleanSummary = view.findViewById(R.id.cleanSummary)
        cleanProgress = view.findViewById(R.id.cleanProgress)
        scanJunkButton = view.findViewById(R.id.scanJunkButton)
        cleanButton = view.findViewById(R.id.cleanButton)
        selectAll = view.findViewById(R.id.selectAll)

        junkAdapter = JunkAdapter(
            isSelected = { it.path in selectedJunk },
            onToggle = { item, checked -> toggleJunk(item, checked) }
        )
        view.findViewById<RecyclerView>(R.id.junkList).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = junkAdapter
        }

        selectAll.setOnClickListener {
            val all = selectAll.isChecked
            selectedJunk.clear()
            if (all) selectedJunk.addAll(junkFound.map { it.path })
            junkAdapter.notifyDataSetChanged()
            updateCleanButton()
        }

        scanJunkButton.setOnClickListener { scanJunk() }
        cleanButton.setOnClickListener { confirmClean() }
    }

    private fun toggleJunk(item: JunkItem, checked: Boolean) {
        if (checked) selectedJunk.add(item.path) else selectedJunk.remove(item.path)
        updateCleanButton()
    }

    private fun updateCleanButton() {
        val selected = junkFound.filter { it.path in selectedJunk }
        val total = selected.sumOf { it.sizeBytes }
        cleanButton.isEnabled = selected.isNotEmpty()
        cleanButton.text = if (selected.isEmpty()) getString(R.string.clean_now)
        else getString(R.string.clean_selected, selected.size, SecurityAnalyst.formatSize(total))
    }

    private fun scanJunk() {
        val context = requireContext()
        val scanner = JunkScanner(context)
        if (!scanner.hasStorageAccess()) {
            requestStorageAccess()
            return
        }
        scanJunkButton.isEnabled = false
        cleanButton.isEnabled = false
        cleanProgress.visibility = View.VISIBLE
        cleanSummary.setText(R.string.clean_scanning)

        viewLifecycleOwner.lifecycleScope.launch {
            val found = withContext(Dispatchers.IO) { scanner.scan() }
            junkFound = found
            selectedJunk.clear()
            selectedJunk.addAll(found.filter { it.category != JunkCategory.LOG_FILE }.map { it.path })
            junkAdapter.submitList(found)
            cleanProgress.visibility = View.GONE
            scanJunkButton.isEnabled = true

            val total = found.sumOf { it.sizeBytes }
            if (found.isEmpty()) {
                cleanSummary.setText(R.string.clean_none)
                selectAll.visibility = View.GONE
            } else {
                cleanSummary.text = getString(R.string.clean_found, found.size, SecurityAnalyst.formatSize(total))
                selectAll.visibility = View.VISIBLE
                selectAll.isChecked = selectedJunk.size == found.size
            }
            updateCleanButton()
            AppLog.log(context, LogCategory.PULIZIA, "Scansione file inutili: ${found.size} elementi, ${SecurityAnalyst.formatSize(total)}")
            endRefresh()
        }
    }

    private fun confirmClean() {
        val selected = junkFound.filter { it.path in selectedJunk }
        if (selected.isEmpty()) return
        val total = selected.sumOf { it.sizeBytes }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.clean_confirm_title)
            .setMessage(getString(R.string.clean_confirm_message, selected.size, SecurityAnalyst.formatSize(total)))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.clean_confirm_ok) { _, _ -> doClean(selected) }
            .show()
    }

    private fun doClean(toClean: List<JunkItem>) {
        val context = requireContext()
        cleanButton.isEnabled = false
        scanJunkButton.isEnabled = false
        cleanProgress.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            val freed = withContext(Dispatchers.IO) { JunkScanner(context).clean(toClean) }
            val cleanedPaths = toClean.map { it.path }.toSet()
            junkFound = junkFound.filterNot { it.path in cleanedPaths }
            selectedJunk.removeAll(cleanedPaths)
            junkAdapter.submitList(junkFound)
            selectAll.visibility = if (junkFound.isEmpty()) View.GONE else View.VISIBLE
            cleanProgress.visibility = View.GONE
            scanJunkButton.isEnabled = true
            updateCleanButton()
            cleanSummary.text = getString(R.string.clean_done, SecurityAnalyst.formatSize(freed))
            AppLog.log(context, LogCategory.PULIZIA, "Pulizia completata: liberati ${SecurityAnalyst.formatSize(freed)}")
        }
    }

    private fun requestStorageAccess() = SystemIntents.requestAllFilesAccess(requireContext())
}
