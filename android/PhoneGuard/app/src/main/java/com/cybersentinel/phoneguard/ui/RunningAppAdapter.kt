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
import com.cybersentinel.phoneguard.data.RunningApp
import com.google.android.material.button.MaterialButton

/**
 * Lista delle app attive in background con il tasto "Ferma".
 * Il tocco sulla riga apre la scheda di sistema (Arresto forzato).
 */
class RunningAppAdapter(
    private val onStop: (RunningApp) -> Unit,
    private val onDetails: (RunningApp) -> Unit
) : ListAdapter<RunningApp, RunningAppAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_running_app, parent, false)
        return Holder(view, onStop, onDetails)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) =
        holder.bind(getItem(position))

    class Holder(
        view: View,
        private val onStop: (RunningApp) -> Unit,
        private val onDetails: (RunningApp) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.runningIcon)
        private val name: TextView = view.findViewById(R.id.runningName)
        private val reason: TextView = view.findViewById(R.id.runningReason)
        private val stopButton: MaterialButton = view.findViewById(R.id.stopButton)

        fun bind(item: RunningApp) {
            val context = itemView.context
            name.text = item.appLabel
            val badge = if (item.hasForegroundService) "⚡ " else ""
            reason.text = "$badge${item.reason}"

            val drawable = runCatching {
                context.packageManager.getApplicationIcon(item.packageName)
            }.getOrNull()
            if (drawable != null) icon.setImageDrawable(drawable)
            else icon.setImageResource(R.drawable.ic_energy)

            stopButton.setOnClickListener { onStop(item) }
            itemView.setOnClickListener { onDetails(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<RunningApp>() {
            override fun areItemsTheSame(o: RunningApp, n: RunningApp) =
                o.packageName == n.packageName
            override fun areContentsTheSame(o: RunningApp, n: RunningApp) = o == n
        }
    }
}
