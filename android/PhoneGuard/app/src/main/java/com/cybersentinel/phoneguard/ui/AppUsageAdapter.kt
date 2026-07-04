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

/**
 * Lista di TUTTE le app che scambiano dati in rete (comprese quelle senza
 * icona nel launcher), ordinata per byte inviati decrescenti. Le app
 * sospette (upload elevato) sono evidenziate con un'icona di avviso, le
 * app nascoste con un badge; ogni app non di sistema ha il pulsante Ferma.
 */
class AppUsageAdapter(
    private val onStop: (AppNetworkUsage) -> Unit
) : ListAdapter<AppNetworkUsage, AppUsageAdapter.Holder>(DIFF) {

    private var txThreshold: Long = Long.MAX_VALUE
    private var hiddenPackages: Set<String> = emptySet()

    fun submit(newItems: List<AppNetworkUsage>, threshold: Long, hiddenPackages: Set<String> = emptySet()) {
        txThreshold = threshold
        this.hiddenPackages = hiddenPackages
        submitList(newItems)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_usage, parent, false)
        return Holder(view, onStop)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        holder.bind(item, txThreshold, item.packageName in hiddenPackages)
    }

    class Holder(
        view: View,
        private val onStop: (AppNetworkUsage) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.appIcon)
        private val name: TextView = view.findViewById(R.id.appName)
        private val details: TextView = view.findViewById(R.id.appDetails)
        private val hiddenBadge: TextView = view.findViewById(R.id.appHiddenBadge)
        private val warning: ImageView = view.findViewById(R.id.warningIcon)
        private val stopButton: MaterialButton = view.findViewById(R.id.appStopButton)

        fun bind(item: AppNetworkUsage, threshold: Long, isHidden: Boolean) {
            name.text = item.appLabel
            details.text = itemView.context.getString(
                R.string.usage_details,
                formatBytes(item.txBytes),
                formatBytes(item.rxBytes)
            )
            warning.visibility =
                if (item.isSuspicious(threshold)) View.VISIBLE else View.GONE
            hiddenBadge.visibility = if (isHidden) View.VISIBLE else View.GONE

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
        private val DIFF = object : DiffUtil.ItemCallback<AppNetworkUsage>() {
            override fun areItemsTheSame(oldItem: AppNetworkUsage, newItem: AppNetworkUsage) =
                oldItem.uid == newItem.uid && oldItem.packageName == newItem.packageName

            override fun areContentsTheSame(oldItem: AppNetworkUsage, newItem: AppNetworkUsage) =
                oldItem == newItem
        }
    }
}
