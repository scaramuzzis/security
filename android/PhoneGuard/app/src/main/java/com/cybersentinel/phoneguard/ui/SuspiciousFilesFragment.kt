package com.cybersentinel.phoneguard.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.cybersentinel.phoneguard.R
import com.cybersentinel.phoneguard.data.SuspiciousFile
import com.cybersentinel.phoneguard.monitor.FileScanner
import com.cybersentinel.phoneguard.util.AppLog
import com.cybersentinel.phoneguard.util.SystemIntents
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pagina Controllo file sospetti: scansione memoria interna + microSD. */
class SuspiciousFilesFragment : Fragment(R.layout.fragment_files) {

    private lateinit var filesText: TextView
    private lateinit var storagePermissionButton: MaterialButton
    private lateinit var scanButton: MaterialButton
    private lateinit var filesProgress: LinearProgressIndicator

    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            updateStorageButton()
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        filesText = view.findViewById(R.id.filesText)
        storagePermissionButton = view.findViewById(R.id.storagePermissionButton)
        scanButton = view.findViewById(R.id.scanButton)
        filesProgress = view.findViewById(R.id.filesProgress)
        storagePermissionButton.setOnClickListener { requestStorageAccess() }
        scanButton.setOnClickListener { scanFiles() }
    }

    override fun onResume() {
        super.onResume()
        updateStorageButton()
    }

    private fun scanFiles() {
        val context = requireContext()
        val fileScanner = FileScanner(context)
        if (!fileScanner.hasStorageAccess()) {
            requestStorageAccess()
            return
        }
        scanButton.isEnabled = false
        filesProgress.visibility = View.VISIBLE
        val roots = fileScanner.storageRoots()
        filesText.text = getString(
            R.string.files_scanning_roots,
            roots.joinToString(", ") { it.absolutePath }
        )

        viewLifecycleOwner.lifecycleScope.launch {
            val found = withContext(Dispatchers.IO) { fileScanner.scan() }
            filesText.text = formatFiles(found)
            filesProgress.visibility = View.GONE
            scanButton.isEnabled = true
            AppLog.log(context, "SCANSIONE", "Scansione file (${roots.size} volumi): ${found.size} sospetti")
        }
    }

    private fun formatFiles(found: List<SuspiciousFile>): CharSequence {
        if (found.isEmpty()) return getString(R.string.files_none)
        val header = getString(R.string.files_found, found.size)
        val body = found.take(20).joinToString("\n\n") { file -> "⚠️ ${file.path}\n   ${file.reason}" }
        val more = if (found.size > 20) "\n\n…" else ""
        return "$header\n\n$body$more"
    }

    private fun updateStorageButton() {
        storagePermissionButton.visibility =
            if (FileScanner(requireContext()).hasStorageAccess()) View.GONE else View.VISIBLE
    }

    private fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            SystemIntents.requestAllFilesAccess(requireContext())
        } else {
            storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}
