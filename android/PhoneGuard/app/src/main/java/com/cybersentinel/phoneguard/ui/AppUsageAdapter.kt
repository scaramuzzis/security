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
import com.cybersentinel.phoneguard.data.AppNetworkUsage
import com.google.android.material.button.MaterialButton
import java.util.Locale

/** Riga della lista: intestazione di sezione oppure app con il suo traffico. */
sealed class UsageRow {
    data class SectionHeader(val title: String) : UsageRow()
    data class AppRow(
        val usage: AppNetworkUsage,
        val isHidden: Boolean,
        val isActive: Boolean,
        val isSuspicious: Boolean
    ) : UsageRow()
}

/**
 * Lista di TUTTE le app che scambiano dati in rete (comprese quelle senza
 * icona nel launcher), divisa in due sezioni — "Servizi attivi ora" e
 * "Non attivi" — così lo stato dopo uno stop è visibile a colpo d'occhio
 * nella struttura della lista, non in un Toast che sparisce. Dentro ogni
 * sezione l'ordine resta per byte inviati decrescenti. Le app sospette
 * (upload elevato) sono evidenziate con un'icona di avviso, le app nascoste
 * con un badge; ogni app non di sistema ha il pulsante Ferma.
 */
class AppUsageAdapter(
    private val onStop: (AppNetworkUsage) -> Unit
) : ListAdapter<UsageRow, RecyclerView.ViewHolder>(DIFF) {

    fun submit(
        newItems: List<AppNetworkUsage>,
        threshold: Long,
        hiddenPackages: Set<String> = emptySet(),
        activePackages: Set<String> = emptySet(),
        activeSectionTitle: String = "",
        inactiveSectionTitle: String = ""
    ) {
        val (active, inactive) = newItems.partition { it.packageName in activePackages }
        val rows = mutableListOf<UsageRow>()
        if (active.isNotEmpty()) {
            rows += UsageRow.SectionHeader(activeSectionTitle)
            rows += active.map { it.toRow(threshold, hiddenPackages, isActive = true) }
        }
        if (inactive.isNotEmpty()) {
            rows += UsageRow.SectionHeader(inactiveSectionTitle)
            rows += inactive.map { it.toRow(threshold, hiddenPackages, isActive = false) }
        }
        submitList(rows)
    }

    private fun AppNetworkUsage.toRow(threshold: Long, hidden: Set<String>, isActive: Boolean) =
        UsageRow.AppRow(this, packageName in hidden, isActive, isSuspicious(threshold))

    override fun getItemViewType(position: Int): Int =
        if (getItem(position) is UsageRow.SectionHeader) TYPE_HEADER else TYPE_APP

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(inflater.inflate(R.layout.item_info_header, parent, false))
        } else {
            AppHolder(inflater.inflate(R.layout.item_app_usage, parent, false), onStop)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is UsageRow.SectionHeader -> (holder as HeaderHolder).title.text = row.title
            is UsageRow.AppRow -> (holder as AppHolder).bind(row)
        }
    }

    class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.infoHeader)
    }

    class AppHolder(
        view: View,
        private val onStop: (AppNetworkUsage) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.appIcon)
        private val name: TextView = view.findViewById(R.id.appName)
        private val details: TextView = view.findViewById(R.id.appDetails)
        private val hiddenBadge: TextView = view.findViewById(R.id.appHiddenBadge)
        private val warning: ImageView = view.findViewById(R.id.warningIcon)
        private val stopButton: MaterialButton = view.findViewById(R.id.appStopButton)

        fun bind(row: UsageRow.AppRow) {
            val item = row.usage
            name.text = item.appLabel
            details.text = itemView.context.getString(
                R.string.usage_details,
                formatBytes(item.txBytes),
                formatBytes(item.rxBytes)
            )
            warning.visibility = if (row.isSuspicious) View.VISIBLE else View.GONE
            hiddenBadge.visibility = if (row.isHidden) View.VISIBLE else View.GONE

            // Fermare un'app di sistema può destabilizzare il telefono: mai offerto.
            stopButton.visibility = if (item.isSystemApp) View.GONE else View.VISIBLE
            stopButton.setOnClickListener { onStop(item) }

            val drawable = runCatching {
                itemView.context.packageManager.getApplicationIcon(item.packageName)
            }.getOrNull()
            if (drawable != null) icon.setImageDrawable(drawable)
            else icon.setImageResource(R.drawable.ic_shield)
        }

        private fun formatBytes(bytes: Long): String {
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
        private const val TYPE_HEADER = 0
        private const val TYPE_APP = 1

        private val DIFF = object : DiffUtil.ItemCallback<UsageRow>() {
            override fun areItemsTheSame(oldItem: UsageRow, newItem: UsageRow): Boolean =
                when {
                    oldItem is UsageRow.SectionHeader && newItem is UsageRow.SectionHeader ->
                        oldItem.title == newItem.title
                    oldItem is UsageRow.AppRow && newItem is UsageRow.AppRow ->
                        oldItem.usage.uid == newItem.usage.uid &&
                                oldItem.usage.packageName == newItem.usage.packageName
                    else -> false
                }

            override fun areContentsTheSame(oldItem: UsageRow, newItem: UsageRow) = oldItem == newItem
        }
    }
}
