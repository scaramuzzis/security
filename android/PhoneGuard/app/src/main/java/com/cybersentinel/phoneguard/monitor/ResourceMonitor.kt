package com.cybersentinel.phoneguard.monitor

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs

/** Byte usati/totali di una risorsa (RAM o archiviazione), con la percentuale derivata. */
data class ResourceUsage(val usedBytes: Long, val totalBytes: Long) {
    val percent: Int get() = if (totalBytes > 0) (usedBytes * 100 / totalBytes).toInt() else 0
}

/** Legge l'occupazione di RAM e spazio di archiviazione, riusato da Dashboard e Salute dispositivo. */
object ResourceMonitor {

    fun ram(context: Context): ResourceUsage {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return ResourceUsage(usedBytes = info.totalMem - info.availMem, totalBytes = info.totalMem)
    }

    fun storage(): ResourceUsage {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes
        return ResourceUsage(usedBytes = total - stat.availableBytes, totalBytes = total)
    }
}
