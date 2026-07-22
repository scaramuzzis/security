package com.cybersentinel.phoneguard.ui

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cybersentinel.phoneguard.R
import com.cybersentinel.phoneguard.data.FileEntry
import com.cybersentinel.phoneguard.data.ViewMode
import com.cybersentinel.phoneguard.monitor.SecurityAnalyst
import com.google.android.material.checkbox.MaterialCheckBox

/**
 * Lista di file/cartelle del file manager, con vista elenco o griglia
 * intercambiabile e checkbox di selezione per copia/taglia/elimina.
 */
class FileEntryAdapter(
    private val isSelected: (FileEntry) -> Boolean,
    private val onToggleSelect: (FileEntry) -> Unit,
    private val onOpen: (FileEntry) -> Unit
) : ListAdapter<FileEntry, FileEntryAdapter.Holder>(DIFF) {

    var viewMode: ViewMode = ViewMode.LIST

    override fun getItemViewType(position: Int): Int =
        if (viewMode == ViewMode.GRID) TYPE_GRID else TYPE_LIST

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val layoutId = if (viewType == TYPE_GRID) R.layout.item_file_grid else R.layout.item_file_list
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return Holder(view, isSelected, onToggleSelect, onOpen)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    class Holder(
        view: View,
        private val isSelected: (FileEntry) -> Boolean,
        private val onToggleSelect: (FileEntry) -> Unit,
        private val onOpen: (FileEntry) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val check: MaterialCheckBox = view.findViewById(R.id.fileCheck)
        private val icon: ImageView = view.findViewById(R.id.fileIcon)
        private val name: TextView = view.findViewById(R.id.fileName)
        private val info: TextView = view.findViewById(R.id.fileInfo)

        fun bind(item: FileEntry) {
            name.text = item.file.name
            icon.setImageResource(if (item.isDirectory) R.drawable.ic_folder else R.drawable.ic_file)

            info.text = if (item.isDirectory) {
                DateUtils.getRelativeTimeSpanString(item.file.lastModified()).toString()
            } else {
                "${SecurityAnalyst.formatSize(item.sizeBytes)} · ${
                    DateUtils.getRelativeTimeSpanString(item.lastModified)
                }"
            }

            check.setOnCheckedChangeListener(null)
            check.isChecked = isSelected(item)
            check.setOnCheckedChangeListener { _, _ -> onToggleSelect(item) }

            itemView.setOnClickListener {
                if (item.isDirectory) onOpen(item) else check.isChecked = !check.isChecked
            }
        }
    }

    companion object {
        private const val TYPE_LIST = 0
        private const val TYPE_GRID = 1

        private val DIFF = object : DiffUtil.ItemCallback<FileEntry>() {
            override fun areItemsTheSame(o: FileEntry, n: FileEntry) =
                o.file.absolutePath == n.file.absolutePath
            override fun areContentsTheSame(o: FileEntry, n: FileEntry) = o == n
        }
    }
}
