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
import com.cybersentinel.phoneguard.data.ConstantSender
import com.google.android.material.button.MaterialButton

/** Lista delle app che inviano dati con regolarità, con frequenza e pulsante Ferma. */
class ConstantSenderAdapter(
    private val onStop: (ConstantSender) -> Unit
) : ListAdapter<ConstantSender, ConstantSenderAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_constant_sender, parent, false)
        return Holder(view, onStop)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    class Holder(
        view: View,
        private val onStop: (ConstantSender) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.senderIcon)
        private val name: TextView = view.findViewById(R.id.senderName)
        private val details: TextView = view.findViewById(R.id.senderDetails)
        private val stopButton: MaterialButton = view.findViewById(R.id.senderStopButton)

        fun bind(item: ConstantSender) {
            name.text = item.appLabel
            details.text = itemView.context.getString(
                R.string.constant_sender_detail, item.activeSlices, item.totalSlices, item.frequencyPercent
            )
            stopButton.setOnClickListener { onStop(item) }

            val drawable = runCatching {
                itemView.context.packageManager.getApplicationIcon(item.packageName)
            }.getOrNull()
            if (drawable != null) icon.setImageDrawable(drawable)
            else icon.setImageResource(R.drawable.ic_shield)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ConstantSender>() {
            override fun areItemsTheSame(oldItem: ConstantSender, newItem: ConstantSender) =
                oldItem.packageName == newItem.packageName

            override fun areContentsTheSame(oldItem: ConstantSender, newItem: ConstantSender) =
                oldItem == newItem
        }
    }
}
