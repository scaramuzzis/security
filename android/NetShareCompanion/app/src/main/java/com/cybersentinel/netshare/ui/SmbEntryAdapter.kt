package com.cybersentinel.netshare.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cybersentinel.netshare.R
import com.cybersentinel.netshare.data.SmbEntry
import java.util.Locale

/** Lista di file e cartelle della condivisione. */
class SmbEntryAdapter(
    private val onClick: (SmbEntry) -> Unit
) : ListAdapter<SmbEntry, SmbEntryAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_entry, parent, false)
        return Holder(view, onClick)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) =
        holder.bind(getItem(position))

    class Holder(
        view: View,
        private val onClick: (SmbEntry) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val icon: TextView = view.findViewById(R.id.entryIcon)
        private val name: TextView = view.findViewById(R.id.entryName)
        private val info: TextView = view.findViewById(R.id.entryInfo)

        fun bind(item: SmbEntry) {
            icon.text = if (item.isDirectory) "📁" else "📄"
            name.text = item.name
            info.text = if (item.isDirectory) itemView.context.getString(R.string.folder)
            else formatSize(item.sizeBytes)
            itemView.setOnClickListener { onClick(item) }
        }

        private fun formatSize(bytes: Long): String {
            val kb = 1024.0
            return when {
                bytes >= kb * kb * kb -> String.format(Locale.getDefault(), "%.2f GB", bytes / (kb * kb * kb))
                bytes >= kb * kb -> String.format(Locale.getDefault(), "%.1f MB", bytes / (kb * kb))
                bytes >= kb -> String.format(Locale.getDefault(), "%.0f KB", bytes / kb)
                else -> "$bytes B"
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SmbEntry>() {
            override fun areItemsTheSame(o: SmbEntry, n: SmbEntry) =
                o.name == n.name && o.isDirectory == n.isDirectory
            override fun areContentsTheSame(o: SmbEntry, n: SmbEntry) = o == n
        }
    }
}
