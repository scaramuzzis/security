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

/** Riga della lista: intestazione di sezione oppure app con la sua frequenza di invio. */
sealed class SenderRow {
    data class SectionHeader(val title: String) : SenderRow()
    data class SenderItem(val sender: ConstantSender, val isActiveNow: Boolean) : SenderRow()
}

/**
 * Lista delle app che inviano dati con regolarità, divisa in "attive ora" e
 * "non attive ora": la frequenza storica (finestre su 24 ore) non cambia
 * fermando un'app adesso, ma lo stato ATTUALE sì — è quello che deve
 * riflettersi subito dopo uno stop riuscito, non lo storico.
 *
 * Il tocco sulla riga apre la scheda di sistema dell'app (Arresto forzato):
 * lo stesso pattern già usato in "App in background", perché lo stop
 * "leggero" del pulsante Ferma può risultare senza effetto sulle app con un
 * Foreground Service protetto dal sistema.
 */
class ConstantSenderAdapter(
    private val onStop: (ConstantSender) -> Unit,
    private val onOpenDetails: (ConstantSender) -> Unit
) : ListAdapter<SenderRow, RecyclerView.ViewHolder>(DIFF) {

    fun submit(senders: List<ConstantSender>, activePackages: Set<String>, activeTitle: String, inactiveTitle: String) {
        val (active, inactive) = senders.partition { it.packageName in activePackages }
        val rows = ArrayList<SenderRow>()
        if (active.isNotEmpty()) {
            rows += SenderRow.SectionHeader(activeTitle)
            rows += active.map { SenderRow.SenderItem(it, isActiveNow = true) }
        }
        if (inactive.isNotEmpty()) {
            rows += SenderRow.SectionHeader(inactiveTitle)
            rows += inactive.map { SenderRow.SenderItem(it, isActiveNow = false) }
        }
        submitList(rows)
    }

    override fun getItemViewType(position: Int): Int =
        if (getItem(position) is SenderRow.SectionHeader) TYPE_HEADER else TYPE_ITEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(inflater.inflate(R.layout.item_info_header, parent, false))
        } else {
            SenderHolder(inflater.inflate(R.layout.item_constant_sender, parent, false), onStop, onOpenDetails)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is SenderRow.SectionHeader -> (holder as HeaderHolder).title.text = row.title
            is SenderRow.SenderItem -> (holder as SenderHolder).bind(row)
        }
    }

    class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.infoHeader)
    }

    class SenderHolder(
        view: View,
        private val onStop: (ConstantSender) -> Unit,
        private val onOpenDetails: (ConstantSender) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.senderIcon)
        private val name: TextView = view.findViewById(R.id.senderName)
        private val details: TextView = view.findViewById(R.id.senderDetails)
        private val stopButton: MaterialButton = view.findViewById(R.id.senderStopButton)

        fun bind(row: SenderRow.SenderItem) {
            val item = row.sender
            name.text = item.appLabel
            val context = itemView.context

            if (row.isActiveNow) {
                details.text = context.getString(R.string.constant_sender_detail_active, item.activeSlices, item.totalSlices, item.frequencyPercent)
                details.setTextColor(context.getColor(R.color.status_warn))
                stopButton.visibility = View.VISIBLE
                stopButton.setOnClickListener { onStop(item) }
            } else {
                details.text = context.getString(R.string.constant_sender_detail_stopped, item.activeSlices, item.totalSlices)
                details.setTextColor(context.getColor(R.color.status_ok))
                stopButton.visibility = View.GONE
            }
            // Il tocco sulla riga apre sempre la scheda di sistema: da lì l'Arresto forzato,
            // per quando lo stop "leggero" del pulsante non basta (Foreground Service protetto).
            itemView.setOnClickListener { onOpenDetails(item) }

            val drawable = runCatching {
                context.packageManager.getApplicationIcon(item.packageName)
            }.getOrNull()
            if (drawable != null) icon.setImageDrawable(drawable)
            else icon.setImageResource(R.drawable.ic_shield)
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1

        private val DIFF = object : DiffUtil.ItemCallback<SenderRow>() {
            override fun areItemsTheSame(oldItem: SenderRow, newItem: SenderRow): Boolean = when {
                oldItem is SenderRow.SectionHeader && newItem is SenderRow.SectionHeader -> oldItem.title == newItem.title
                oldItem is SenderRow.SenderItem && newItem is SenderRow.SenderItem ->
                    oldItem.sender.packageName == newItem.sender.packageName
                else -> false
            }

            override fun areContentsTheSame(oldItem: SenderRow, newItem: SenderRow) = oldItem == newItem
        }
    }
}
