package com.cybersentinel.netwatch.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cybersentinel.netwatch.R
import com.cybersentinel.netwatch.data.DomainLogEntry
import java.text.SimpleDateFormat
import java.util.Locale

/** Elenco delle richieste DNS osservate, più recenti in cima. */
class DomainLogAdapter : RecyclerView.Adapter<DomainLogAdapter.Holder>() {

    private var items: List<DomainLogEntry> = emptyList()
    private val formatter = SimpleDateFormat("HH:mm:ss", Locale.ITALY)

    fun submit(newItems: List<DomainLogEntry>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_domain_log, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position], formatter)

    override fun getItemCount(): Int = items.size

    class Holder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        private val app: TextView = view.findViewById(R.id.logApp)
        private val domain: TextView = view.findViewById(R.id.logDomain)
        private val time: TextView = view.findViewById(R.id.logTime)

        fun bind(entry: DomainLogEntry, formatter: SimpleDateFormat) {
            app.text = entry.appLabel
            domain.text = entry.domain
            time.text = formatter.format(entry.timestamp)
        }
    }
}
