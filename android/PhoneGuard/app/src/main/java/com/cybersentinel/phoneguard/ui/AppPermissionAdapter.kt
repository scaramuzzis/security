package com.cybersentinel.phoneguard.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cybersentinel.phoneguard.R
import com.cybersentinel.phoneguard.monitor.GrantedApp
import com.cybersentinel.phoneguard.monitor.PermissionCategory

/** Riga della lista: intestazione di categoria oppure app con quel permesso concesso. */
sealed class PermissionRow {
    data class SectionHeader(val title: String) : PermissionRow()
    data class AppItem(val app: GrantedApp) : PermissionRow()
}

/**
 * Lista delle categorie di autorizzazioni (fotocamera, posizione...) con le
 * app che le hanno concesse; il tocco su un'app apre la sua scheda di
 * sistema, dove il permesso si può revocare.
 */
class AppPermissionAdapter(
    private val onOpenDetails: (GrantedApp) -> Unit
) : ListAdapter<PermissionRow, RecyclerView.ViewHolder>(DIFF) {

    fun submit(categories: List<PermissionCategory>, context: android.content.Context) {
        val rows = ArrayList<PermissionRow>()
        categories.filter { it.apps.isNotEmpty() }.forEach { category ->
            rows += PermissionRow.SectionHeader(
                context.getString(R.string.permission_category_header, category.icon, category.title, category.apps.size)
            )
            rows += category.apps.map { PermissionRow.AppItem(it) }
        }
        submitList(rows)
    }

    override fun getItemViewType(position: Int): Int =
        if (getItem(position) is PermissionRow.SectionHeader) TYPE_HEADER else TYPE_APP

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(inflater.inflate(R.layout.item_info_header, parent, false))
        } else {
            AppHolder(inflater.inflate(R.layout.item_permission_app, parent, false), onOpenDetails)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is PermissionRow.SectionHeader -> (holder as HeaderHolder).title.text = row.title
            is PermissionRow.AppItem -> (holder as AppHolder).bind(row.app)
        }
    }

    class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.infoHeader)
    }

    class AppHolder(
        view: View,
        private val onOpenDetails: (GrantedApp) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.permAppIcon)
        private val name: TextView = view.findViewById(R.id.permAppName)

        fun bind(app: GrantedApp) {
            name.text = app.appLabel
            itemView.setOnClickListener { onOpenDetails(app) }
            val drawable = runCatching {
                itemView.context.packageManager.getApplicationIcon(app.packageName)
            }.getOrNull()
            if (drawable != null) icon.setImageDrawable(drawable)
            else icon.setImageResource(R.drawable.ic_shield)
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_APP = 1

        private val DIFF = object : DiffUtil.ItemCallback<PermissionRow>() {
            override fun areItemsTheSame(oldItem: PermissionRow, newItem: PermissionRow): Boolean = when {
                oldItem is PermissionRow.SectionHeader && newItem is PermissionRow.SectionHeader ->
                    oldItem.title == newItem.title
                oldItem is PermissionRow.AppItem && newItem is PermissionRow.AppItem ->
                    oldItem.app.packageName == newItem.app.packageName
                else -> false
            }

            override fun areContentsTheSame(oldItem: PermissionRow, newItem: PermissionRow) = oldItem == newItem
        }
    }
}
