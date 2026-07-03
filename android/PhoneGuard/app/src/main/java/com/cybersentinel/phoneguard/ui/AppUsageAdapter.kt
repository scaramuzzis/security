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
import java.util.Locale

/**
 * Lista delle app con i relativi dati inviati/ricevuti.
 * Le app sospette (upload elevato) sono evidenziate con un'icona di avviso.
 */
class AppUsageAdapter : ListAdapter<AppNetworkUsage, AppUsageAdapter.Holder>(DIFF) {

    private var txThreshold: Long = Long.MAX_VALUE

    fun submit(newItems: List<AppNetworkUsage>, threshold: Long) {
        txThreshold = threshold
        submitList(newItems)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_usage, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position), txThreshold)
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.appIcon)
        private val name: TextView = view.findViewById(R.id.appName)
        private val details: TextView = view.findViewById(R.id.appDetails)
        private val warning: ImageView = view.findViewById(R.id.warningIcon)

        fun bind(item: AppNetworkUsage, threshold: Long) {
            name.text = item.appLabel
            details.text = itemView.context.getString(
                R.string.usage_details,
                formatBytes(item.txBytes),
                formatBytes(item.rxBytes)
            )
            warning.visibility =
                if (item.isSuspicious(threshold)) View.VISIBLE else View.GONE

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
