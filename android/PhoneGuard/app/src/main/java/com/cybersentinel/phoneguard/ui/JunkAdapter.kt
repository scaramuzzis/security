package com.cybersentinel.phoneguard.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cybersentinel.phoneguard.R
import com.cybersentinel.phoneguard.data.JunkItem
import com.cybersentinel.phoneguard.monitor.SecurityAnalyst
import com.google.android.material.checkbox.MaterialCheckBox

/**
 * Lista dei file/cartelle inutili trovati, con categoria, dimensione e una
 * casella di selezione: l'utente sceglie che cosa eliminare.
 */
class JunkAdapter(
    private val isSelected: (JunkItem) -> Boolean,
    private val onToggle: (JunkItem, Boolean) -> Unit
) : ListAdapter<JunkItem, JunkAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_junk, parent, false)
        return Holder(view, isSelected, onToggle)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) =
        holder.bind(getItem(position))

    class Holder(
        view: View,
        private val isSelected: (JunkItem) -> Boolean,
        private val onToggle: (JunkItem, Boolean) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val check: MaterialCheckBox = view.findViewById(R.id.junkCheck)
        private val title: TextView = view.findViewById(R.id.junkTitle)
        private val path: TextView = view.findViewById(R.id.junkPath)
        private val size: TextView = view.findViewById(R.id.junkSize)

        fun bind(item: JunkItem) {
            title.text = item.category.label
            path.text = item.path
            size.text = if (item.sizeBytes > 0)
                SecurityAnalyst.formatSize(item.sizeBytes) else "—"

            check.setOnCheckedChangeListener(null)
            check.isChecked = isSelected(item)
            check.setOnCheckedChangeListener { _, checked -> onToggle(item, checked) }

            itemView.setOnClickListener { check.isChecked = !check.isChecked }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<JunkItem>() {
            override fun areItemsTheSame(o: JunkItem, n: JunkItem) = o.path == n.path
            override fun areContentsTheSame(o: JunkItem, n: JunkItem) = o == n
        }
    }
}
