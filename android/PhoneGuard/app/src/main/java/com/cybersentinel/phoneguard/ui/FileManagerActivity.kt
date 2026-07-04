package com.cybersentinel.phoneguard.ui

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cybersentinel.phoneguard.R
import com.cybersentinel.phoneguard.data.FileEntry
import com.cybersentinel.phoneguard.data.ViewMode
import com.cybersentinel.phoneguard.monitor.FileRepository
import com.cybersentinel.phoneguard.monitor.SecurityAnalyst
import com.cybersentinel.phoneguard.util.SystemIntents
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Gestione file: sfoglia memoria interna e microSD con vista elenco o
 * griglia, e copia/taglia/incolla i file fra le cartelle — come Esplora
 * File di Windows.
 */
class FileManagerActivity : AppCompatActivity() {

    private lateinit var repository: FileRepository
    private lateinit var adapter: FileEntryAdapter
    private lateinit var fileList: RecyclerView
    private lateinit var rootChips: ChipGroup
    private lateinit var pathLabel: TextView
    private lateinit var upButton: MaterialButton
    private lateinit var viewModeButton: ImageButton
    private lateinit var pasteButton: ImageButton
    private lateinit var actionBar: View
    private lateinit var fileProgress: LinearProgressIndicator
    private lateinit var emptyText: TextView
    private lateinit var permissionButton: MaterialButton

    private var roots: List<File> = emptyList()
    private lateinit var currentRoot: File
    private lateinit var currentDir: File
    private val selected = LinkedHashSet<String>()
    private var clipboard: List<File> = emptyList()
    private var clipboardCut = false
    private var viewMode = ViewMode.LIST

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_manager)
        repository = FileRepository(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        rootChips = findViewById(R.id.rootChips)
        pathLabel = findViewById(R.id.pathLabel)
        upButton = findViewById(R.id.upButton)
        viewModeButton = findViewById(R.id.viewModeButton)
        pasteButton = findViewById(R.id.pasteButton)
        actionBar = findViewById(R.id.actionBar)
        fileProgress = findViewById(R.id.fileProgress)
        emptyText = findViewById(R.id.emptyText)
        permissionButton = findViewById(R.id.permissionButton)
        fileList = findViewById(R.id.fileList)

        adapter = FileEntryAdapter(
            isSelected = { it.file.absolutePath in selected },
            onToggleSelect = ::toggleSelection,
            onOpen = { navigateInto(it.file) }
        )
        fileList.layoutManager = LinearLayoutManager(this)
        fileList.adapter = adapter

        upButton.setOnClickListener { navigateUp() }
        viewModeButton.setOnClickListener { toggleViewMode() }
        findViewById<ImageButton>(R.id.newFolderButton).setOnClickListener { showNewFolderDialog() }
        pasteButton.setOnClickListener { pasteClipboard() }
        permissionButton.setOnClickListener { requestStorageAccess() }

        findViewById<MaterialButton>(R.id.copyButton).setOnClickListener { copySelection() }
        findViewById<MaterialButton>(R.id.cutButton).setOnClickListener { cutSelection() }
        findViewById<MaterialButton>(R.id.deleteButton).setOnClickListener { confirmDeleteSelection() }
    }

    override fun onResume() {
        super.onResume()
        if (!repository.hasStorageAccess()) {
            permissionButton.visibility = View.VISIBLE
            fileList.visibility = View.GONE
            return
        }
        permissionButton.visibility = View.GONE
        fileList.visibility = View.VISIBLE
        if (roots.isEmpty()) setupRoots() else refresh()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onBackPressed() {
        if (::currentDir.isInitialized && currentDir != currentRoot) {
            navigateUp()
        } else {
            super.onBackPressed()
        }
    }

    private fun setupRoots() {
        roots = repository.roots()
        if (roots.isEmpty()) return
        rootChips.removeAllViews()
        roots.forEachIndexed { index, root ->
            val chip = Chip(this).apply {
                text = if (index == 0) getString(R.string.internal_storage) else getString(R.string.sd_card, index)
                isCheckable = true
                isChecked = index == 0
                tag = root
            }
            chip.setOnClickListener {
                currentRoot = root
                currentDir = root
                refresh()
            }
            rootChips.addView(chip)
        }
        currentRoot = roots.first()
        currentDir = currentRoot
        refresh()
    }

    private fun navigateInto(dir: File) {
        currentDir = dir
        selected.clear()
        refresh()
    }

    private fun navigateUp() {
        val parent = currentDir.parentFile
        if (parent == null || currentDir == currentRoot) return
        currentDir = if (isWithinRoot(parent)) parent else currentRoot
        selected.clear()
        refresh()
    }

    private fun isWithinRoot(dir: File): Boolean =
        dir.absolutePath.startsWith(currentRoot.absolutePath)

    private fun refresh() {
        upButton.isEnabled = currentDir != currentRoot
        pathLabel.text = currentDir.absolutePath

        lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) { repository.list(currentDir) }
            adapter.submitList(entries)
            emptyText.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        }
        updateActionBar()
        updatePasteButton()
    }

    private fun toggleSelection(entry: FileEntry) {
        val path = entry.file.absolutePath
        if (path in selected) selected.remove(path) else selected.add(path)
        updateActionBar()
    }

    private fun updateActionBar() {
        actionBar.visibility = if (selected.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun updatePasteButton() {
        pasteButton.visibility = if (clipboard.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun toggleViewMode() {
        viewMode = if (viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
        adapter.viewMode = viewMode
        fileList.layoutManager = if (viewMode == ViewMode.GRID)
            GridLayoutManager(this, 3) else LinearLayoutManager(this)
        viewModeButton.setImageResource(
            if (viewMode == ViewMode.GRID) R.drawable.ic_list_view else R.drawable.ic_grid_view
        )
        adapter.notifyDataSetChanged()
    }

    private fun selectedFiles(): List<File> =
        selected.mapNotNull { path -> adapter.currentList.find { it.file.absolutePath == path }?.file }

    private fun copySelection() {
        clipboard = selectedFiles()
        clipboardCut = false
        val count = clipboard.size
        selected.clear()
        updateActionBar()
        updatePasteButton()
        adapter.notifyDataSetChanged()
        Toast.makeText(this, getString(R.string.copied_to_clipboard, count), Toast.LENGTH_SHORT).show()
    }

    private fun cutSelection() {
        clipboard = selectedFiles()
        clipboardCut = true
        val count = clipboard.size
        selected.clear()
        updateActionBar()
        updatePasteButton()
        adapter.notifyDataSetChanged()
        Toast.makeText(this, getString(R.string.cut_to_clipboard, count), Toast.LENGTH_SHORT).show()
    }

    private fun pasteClipboard() {
        if (clipboard.isEmpty()) return
        val destination = currentDir
        val sources = clipboard
        val cut = clipboardCut
        fileProgress.visibility = View.VISIBLE
        pasteButton.isEnabled = false

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                if (cut) repository.move(sources, destination) else repository.copy(sources, destination)
            }
            fileProgress.visibility = View.GONE
            pasteButton.isEnabled = true
            if (cut) { clipboard = emptyList(); updatePasteButton() }
            Toast.makeText(
                this@FileManagerActivity,
                getString(R.string.paste_result, result.succeeded, SecurityAnalyst.formatSize(result.bytesCopied)) +
                        if (result.failed > 0) " " + getString(R.string.paste_failed, result.failed) else "",
                Toast.LENGTH_LONG
            ).show()
            refresh()
        }
    }

    private fun confirmDeleteSelection() {
        val files = selectedFiles()
        if (files.isEmpty()) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_confirm_title)
            .setMessage(getString(R.string.delete_confirm_message, files.size))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> deleteSelection(files) }
            .show()
    }

    private fun deleteSelection(files: List<File>) {
        fileProgress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { repository.delete(files) }
            fileProgress.visibility = View.GONE
            selected.clear()
            updateActionBar()
            Toast.makeText(this@FileManagerActivity, getString(R.string.delete_result, result.succeeded), Toast.LENGTH_SHORT).show()
            refresh()
        }
    }

    private fun showNewFolderDialog() {
        val input = EditText(this).apply { hint = getString(R.string.new_folder_hint) }
        val container = android.widget.FrameLayout(this).apply {
            setPadding(48, 24, 48, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.new_folder)
            .setView(container)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.create) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    repository.createFolder(currentDir, name)
                    refresh()
                }
            }
            .show()
    }

    private fun requestStorageAccess() = SystemIntents.requestAllFilesAccess(this)
}
