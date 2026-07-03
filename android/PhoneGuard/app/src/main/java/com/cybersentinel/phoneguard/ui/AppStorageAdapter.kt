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
import com.cybersentinel.phoneguard.data.AppStorageInfo
import com.cybersentinel.phoneguard.monitor.SecurityAnalyst
import com.google.android.material.button.MaterialButton

/**
 * Inventario app con occupazione di spazio (totale + cache liberabile).
 * "Gestisci" apre la scheda di sistema (Svuota cache / Disinstalla).
 */
class AppStorageAdapter(
    private val onManage: (AppStorageInfo) -> Unit
) : ListAdapter<AppStorageInfo, AppStorageAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_storage, parent, false)
        return Holder(view, onManage)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) =
        holder.bind(getItem(position))

    class Holder(
        view: View,
        private val onManage: (AppStorageInfo) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.storageIcon)
        private val name: TextView = view.findViewById(R.id.storageName)
        private val details: TextView = view.findViewById(R.id.storageDetails)
        private val manage: MaterialButton = view.findViewById(R.id.storageManage)

        fun bind(item: AppStorageInfo) {
            val context = itemView.context
            val tag = if (item.isSystemApp) " · ${context.getString(R.string.system_tag)}" else ""
            name.text = item.appLabel
            details.text = context.getString(
                R.string.storage_details,
                SecurityAnalyst.formatSize(item.totalBytes),
                SecurityAnalyst.formatSize(item.cacheBytes)
            ) + tag

            val drawable = runCatching {
                context.packageManager.getApplicationIcon(item.packageName)
            }.getOrNull()
            if (drawable != null) icon.setImageDrawable(drawable)
            else icon.setImageResource(R.drawable.ic_dashboard)

            manage.setOnClickListener { onManage(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AppStorageInfo>() {
            override fun areItemsTheSame(o: AppStorageInfo, n: AppStorageInfo) =
                o.packageName == n.packageName
            override fun areContentsTheSame(o: AppStorageInfo, n: AppStorageInfo) = o == n
        }
    }
}
