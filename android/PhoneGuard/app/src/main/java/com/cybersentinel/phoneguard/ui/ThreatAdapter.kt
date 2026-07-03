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
import com.cybersentinel.phoneguard.data.AppThreat
import com.cybersentinel.phoneguard.data.RiskLevel
import com.google.android.material.button.MaterialButton

/**
 * Lista delle app sospette/nascoste con livello di rischio, motivi della
 * segnalazione e azioni dirette: disinstalla o apri la scheda di sistema
 * (necessaria per revocare i privilegi di amministratore che bloccano
 * la disinstallazione).
 */
class ThreatAdapter(
    private val onUninstall: (AppThreat) -> Unit,
    private val onAppInfo: (AppThreat) -> Unit
) : ListAdapter<AppThreat, ThreatAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_threat, parent, false)
        return Holder(view, onUninstall, onAppInfo)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    class Holder(
        view: View,
        private val onUninstall: (AppThreat) -> Unit,
        private val onAppInfo: (AppThreat) -> Unit
    ) : RecyclerView.ViewHolder(view) {

        private val icon: ImageView = view.findViewById(R.id.threatIcon)
        private val name: TextView = view.findViewById(R.id.threatName)
        private val risk: TextView = view.findViewById(R.id.threatRisk)
        private val reasons: TextView = view.findViewById(R.id.threatReasons)
        private val uninstallButton: MaterialButton = view.findViewById(R.id.uninstallButton)
        private val infoButton: MaterialButton = view.findViewById(R.id.appInfoButton)

        fun bind(item: AppThreat) {
            val context = itemView.context

            name.text = context.getString(
                R.string.threat_name, item.label, item.packageName
            )
            risk.text = when (item.riskLevel) {
                RiskLevel.CRITICO -> context.getString(R.string.risk_critical, item.score)
                RiskLevel.ALTO -> context.getString(R.string.risk_high, item.score)
                RiskLevel.SOSPETTO -> context.getString(R.string.risk_suspicious, item.score)
            }
            reasons.text = item.reasons.joinToString("\n") { "• $it" }

            val drawable = runCatching {
                context.packageManager.getApplicationIcon(item.packageName)
            }.getOrNull()
            if (drawable != null) icon.setImageDrawable(drawable)
            else icon.setImageResource(R.drawable.ic_warning)

            uninstallButton.setOnClickListener { onUninstall(item) }
            infoButton.setOnClickListener { onAppInfo(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AppThreat>() {
            override fun areItemsTheSame(oldItem: AppThreat, newItem: AppThreat) =
                oldItem.packageName == newItem.packageName

            override fun areContentsTheSame(oldItem: AppThreat, newItem: AppThreat) =
                oldItem == newItem
        }
    }
}
