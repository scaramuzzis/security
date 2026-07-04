package com.cybersentinel.phoneguard.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cybersentinel.phoneguard.R
import com.cybersentinel.phoneguard.data.SimInfo
import com.google.android.material.button.MaterialButton

/** Lista delle SIM installate con stato e scorciatoia alle impostazioni di rete. */
class SimAdapter(
    private val onOpenSettings: (SimInfo) -> Unit
) : ListAdapter<SimInfo, SimAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sim, parent, false)
        return Holder(view, onOpenSettings)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    class Holder(
        view: View,
        private val onOpenSettings: (SimInfo) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.simTitle)
        private val details: TextView = view.findViewById(R.id.simDetails)
        private val badges: TextView = view.findViewById(R.id.simBadges)
        private val settingsButton: MaterialButton = view.findViewById(R.id.simSettingsButton)

        fun bind(item: SimInfo) {
            val context = itemView.context
            title.text = context.getString(
                R.string.sim_title, item.slotIndex + 1, item.displayName
            )
            details.text = context.getString(
                R.string.sim_details,
                item.carrierName, item.phoneNumber, item.countryIso, item.networkTypeLabel
            )

            val flags = ArrayList<String>()
            if (item.isDefaultData) flags.add(context.getString(R.string.sim_flag_data))
            if (item.isDefaultVoice) flags.add(context.getString(R.string.sim_flag_voice))
            if (item.isDefaultSms) flags.add(context.getString(R.string.sim_flag_sms))
            if (item.isEmbedded) flags.add(context.getString(R.string.sim_flag_esim))
            flags.add(
                if (item.dataRoamingEnabled) context.getString(R.string.sim_flag_roaming_on)
                else context.getString(R.string.sim_flag_roaming_off)
            )
            badges.text = flags.joinToString("   ·   ")

            settingsButton.setOnClickListener { onOpenSettings(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SimInfo>() {
            override fun areItemsTheSame(o: SimInfo, n: SimInfo) =
                o.subscriptionId == n.subscriptionId
            override fun areContentsTheSame(o: SimInfo, n: SimInfo) = o == n
        }
    }
}
