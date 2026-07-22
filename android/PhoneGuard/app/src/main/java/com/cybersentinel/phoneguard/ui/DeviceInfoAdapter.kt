package com.cybersentinel.phoneguard.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cybersentinel.phoneguard.R
import com.cybersentinel.phoneguard.monitor.DeviceInfoRow

/** Renderizza le righe della pagina informazioni (intestazioni + voci). */
class DeviceInfoAdapter(
    private val rows: List<DeviceInfoRow>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun getItemViewType(position: Int): Int =
        if (rows[position] is DeviceInfoRow.Header) TYPE_HEADER else TYPE_ENTRY

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(inflater.inflate(R.layout.item_info_header, parent, false))
        } else {
            EntryHolder(inflater.inflate(R.layout.item_info_entry, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is DeviceInfoRow.Header -> (holder as HeaderHolder).title.text = row.title
            is DeviceInfoRow.Entry -> (holder as EntryHolder).bind(row)
        }
    }

    override fun getItemCount(): Int = rows.size

    class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.infoHeader)
    }

    class EntryHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val label: TextView = view.findViewById(R.id.infoLabel)
        private val value: TextView = view.findViewById(R.id.infoValue)
        fun bind(row: DeviceInfoRow.Entry) {
            label.text = row.label
            value.text = row.value
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ENTRY = 1
    }
}
