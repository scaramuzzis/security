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
import com.cybersentinel.phoneguard.data.AppEnergyUsage
import com.google.android.material.button.MaterialButton

/**
 * Lista delle app energivore: tempo in primo piano, impatto percentuale,
 * badge Foreground Service e pulsante di gestione (la scheda di sistema
 * offre Arresto forzato e restrizione batteria, azioni che Android
 * riserva all'utente).
 */
class EnergyAdapter(
    private val onManage: (AppEnergyUsage) -> Unit
) : ListAdapter<AppEnergyUsage, EnergyAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_energy, parent, false)
        return Holder(view, onManage)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    class Holder(
        view: View,
        private val onManage: (AppEnergyUsage) -> Unit
    ) : RecyclerView.ViewHolder(view) {

        private val icon: ImageView = view.findViewById(R.id.energyIcon)
        private val name: TextView = view.findViewById(R.id.energyName)
        private val details: TextView = view.findViewById(R.id.energyDetails)
        private val fgsBadge: TextView = view.findViewById(R.id.energyFgsBadge)
        private val manageButton: MaterialButton = view.findViewById(R.id.manageButton)

        fun bind(item: AppEnergyUsage) {
            val context = itemView.context
            name.text = item.appLabel
            details.text = context.getString(
                R.string.energy_details,
                formatDuration(item.foregroundMillis),
                item.impactPercent
            )
            fgsBadge.visibility =
                if (item.usedForegroundService) View.VISIBLE else View.GONE

            val drawable = runCatching {
                context.packageManager.getApplicationIcon(item.packageName)
            }.getOrNull()
            if (drawable != null) icon.setImageDrawable(drawable)
            else icon.setImageResource(R.drawable.ic_energy)

            manageButton.setOnClickListener { onManage(item) }
        }

        private fun formatDuration(millis: Long): String {
            val minutes = millis / 60_000
            val hours = minutes / 60
            return if (hours > 0) "${hours}h ${minutes % 60}m" else "${minutes}m"
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AppEnergyUsage>() {
            override fun areItemsTheSame(oldItem: AppEnergyUsage, newItem: AppEnergyUsage) =
                oldItem.packageName == newItem.packageName

            override fun areContentsTheSame(oldItem: AppEnergyUsage, newItem: AppEnergyUsage) =
                oldItem == newItem
        }
    }
}
