package com.cybersentinel.phoneguard.ui

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cybersentinel.phoneguard.R
import com.cybersentinel.phoneguard.data.AppUpdateInfo
import com.google.android.material.button.MaterialButton

/**
 * Lista delle app per anzianità dell'ultimo aggiornamento, con il tasto
 * "Aggiorna" che apre la scheda dell'app sul Play Store.
 */
class UpdateAdapter(
    private val onUpdate: (AppUpdateInfo) -> Unit
) : ListAdapter<AppUpdateInfo, UpdateAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_update, parent, false)
        return Holder(view, onUpdate)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) =
        holder.bind(getItem(position))

    class Holder(
        view: View,
        private val onUpdate: (AppUpdateInfo) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.updateIcon)
        private val name: TextView = view.findViewById(R.id.updateName)
        private val details: TextView = view.findViewById(R.id.updateDetails)
        private val button: MaterialButton = view.findViewById(R.id.updateButton)

        fun bind(item: AppUpdateInfo) {
            val context = itemView.context
            val ago = DateUtils.getRelativeTimeSpanString(
                item.lastUpdateTime, System.currentTimeMillis(),
                DateUtils.DAY_IN_MILLIS
            )
            val prefix = if (item.stale) "⚠️ " else ""
            name.text = "$prefix${item.appLabel}"
            details.text = context.getString(
                R.string.update_details, item.versionName, ago
            )
            button.setOnClickListener { onUpdate(item) }

            val drawable = runCatching {
                context.packageManager.getApplicationIcon(item.packageName)
            }.getOrNull()
            if (drawable != null) icon.setImageDrawable(drawable)
            else icon.setImageResource(R.drawable.ic_dashboard)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AppUpdateInfo>() {
            override fun areItemsTheSame(o: AppUpdateInfo, n: AppUpdateInfo) =
                o.packageName == n.packageName
            override fun areContentsTheSame(o: AppUpdateInfo, n: AppUpdateInfo) = o == n
        }
    }
}
