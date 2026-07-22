package com.cybersentinel.aidiag.monitor

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.PowerManager
import android.os.Process
import com.cybersentinel.aidiag.data.NetworkSample

/**
 * Legge il traffico dati per-app in un intervallo, sullo stesso principio di
 * `NetworkMonitor` di PhoneGuard (stessa API di sistema, nessuna dipendenza
 * diretta fra le due app: sono sandboxate separatamente).
 */
class NetworkReader(private val context: Context) {

    private val statsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
    private val packageManager: PackageManager = context.packageManager

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    @Suppress("DEPRECATION") // NetworkStatsManager.querySummary richiede questi identificatori legacy: nessun sostituto.
    fun sampleSince(startTime: Long, endTime: Long): List<NetworkSample> {
        if (!hasUsageAccess()) return emptyList()
        val rxByUid = HashMap<Int, Long>()
        val txByUid = HashMap<Int, Long>()

        for (networkType in listOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)) {
            runCatching {
                statsManager.querySummary(networkType, null, startTime, endTime).use { stats ->
                    val bucket = NetworkStats.Bucket()
                    while (stats.hasNextBucket()) {
                        stats.getNextBucket(bucket)
                        rxByUid.merge(bucket.uid, bucket.rxBytes, Long::plus)
                        txByUid.merge(bucket.uid, bucket.txBytes, Long::plus)
                    }
                }
            }
        }

        val screenOn = (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        val now = System.currentTimeMillis()

        return rxByUid.keys.union(txByUid.keys)
            .mapNotNull { uid -> toSample(uid, rxByUid[uid] ?: 0, txByUid[uid] ?: 0, now, screenOn) }
            .filter { it.txBytes + it.rxBytes > 0 }
    }

    private fun toSample(uid: Int, rx: Long, tx: Long, ts: Long, screenOn: Boolean): NetworkSample? {
        if (uid == android.os.Process.SYSTEM_UID) return null
        val packages = packageManager.getPackagesForUid(uid) ?: return null
        val packageName = packages.firstOrNull() ?: return null
        val label = try {
            val info: ApplicationInfo = packageManager.getApplicationInfo(packageName, 0)
            if ((info.flags and ApplicationInfo.FLAG_SYSTEM) != 0) return null
            packageManager.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
        return NetworkSample(ts, packageName, label, tx, rx, screenOn)
    }
}
