package com.cybersentinel.phoneguard.monitor

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Process
import com.cybersentinel.phoneguard.data.AppNetworkUsage
import com.cybersentinel.phoneguard.data.ConstantSender

/**
 * Legge, tramite NetworkStatsManager, quanti dati ogni app ha inviato e
 * ricevuto (Wi-Fi + rete mobile) in un intervallo di tempo.
 *
 * Richiede il permesso speciale "Accesso ai dati di utilizzo"
 * (PACKAGE_USAGE_STATS), che l'utente deve concedere manualmente.
 */
class NetworkMonitor(private val context: Context) {

    private val statsManager =
        context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
    private val packageManager: PackageManager = context.packageManager

    /** Verifica se l'utente ha concesso l'accesso ai dati di utilizzo. */
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Traffico per-app (Wi-Fi + mobile) tra [startTime] e [endTime]
     * (millisecondi epoch), ordinato per byte inviati decrescenti.
     */
    @Suppress("DEPRECATION") // NetworkStatsManager.querySummary richiede questi identificatori legacy: nessun sostituto.
    fun queryUsage(startTime: Long, endTime: Long): List<AppNetworkUsage> {
        val rxByUid = HashMap<Int, Long>()
        val txByUid = HashMap<Int, Long>()

        for (networkType in listOf(
            ConnectivityManager.TYPE_WIFI,
            ConnectivityManager.TYPE_MOBILE
        )) {
            collect(networkType, startTime, endTime, rxByUid, txByUid)
        }

        return rxByUid.keys.union(txByUid.keys)
            .mapNotNull { uid -> toAppUsage(uid, rxByUid[uid] ?: 0, txByUid[uid] ?: 0) }
            .filter { it.totalBytes > 0 }
            .sortedByDescending { it.txBytes }
    }

    /**
     * Totale wifi+mobile visto dal sistema per l'INTERO dispositivo tra
     * [startTime] ed [endTime] — non solo le app che PhoneGuard riesce ad
     * attribuire a un pacchetto. Confrontato con la somma di [queryUsage]
     * nello stesso intervallo, un divario ampio può segnalare traffico non
     * attribuito a una app specifica (vedi [NetworkUsageFragment]).
     */
    @Suppress("DEPRECATION") // stesso motivo di queryUsage: nessun sostituto per TYPE_WIFI/TYPE_MOBILE.
    fun deviceTotal(startTime: Long, endTime: Long): Pair<Long, Long> {
        var rx = 0L
        var tx = 0L
        for (networkType in listOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)) {
            runCatching {
                val bucket = statsManager.querySummaryForDevice(networkType, null, startTime, endTime)
                rx += bucket.rxBytes
                tx += bucket.txBytes
            }
        }
        return rx to tx
    }

    /**
     * App che hanno inviato dati in almeno [minFrequencyPercent]% delle
     * finestre analizzate nelle ultime [hoursBack] ore (divise in blocchi da
     * [sliceHours] ore) — cioè inviano dati con regolarità, non solo una
     * volta ogni tanto. Riusa [queryUsage] più volte invece di introdurre
     * una query diversa: stessa logica di rilevamento, stessa definizione di
     * "app di sistema" ed "escluse", nessuna duplicazione.
     */
    fun constantSenders(
        hoursBack: Int = 24,
        sliceHours: Int = 3,
        minFrequencyPercent: Int = 75
    ): List<ConstantSender> {
        val now = System.currentTimeMillis()
        val sliceMs = sliceHours * 60L * 60 * 1000
        val totalSlices = hoursBack / sliceHours
        val activeSlices = HashMap<String, Int>()
        val labels = HashMap<String, String>()

        for (i in 0 until totalSlices) {
            val end = now - i * sliceMs
            val start = end - sliceMs
            queryUsage(start, end)
                .filter { !it.isSystemApp && it.txBytes > 0 }
                .forEach { app ->
                    activeSlices.merge(app.packageName, 1, Int::plus)
                    labels[app.packageName] = app.appLabel
                }
        }

        return activeSlices.entries
            .filter { (it.value * 100 / totalSlices) >= minFrequencyPercent }
            .map { ConstantSender(it.key, labels[it.key] ?: it.key, it.value, totalSlices) }
            .sortedByDescending { it.activeSlices }
    }

    private fun collect(
        networkType: Int,
        startTime: Long,
        endTime: Long,
        rxByUid: MutableMap<Int, Long>,
        txByUid: MutableMap<Int, Long>
    ) {
        val stats: NetworkStats = try {
            // subscriberId null: dal livello API 28+ aggrega tutte le SIM/reti del tipo indicato
            statsManager.querySummary(networkType, null, startTime, endTime)
        } catch (e: SecurityException) {
            return // permesso di accesso all'utilizzo non concesso
        } catch (e: Exception) {
            return
        }

        stats.use {
            val bucket = NetworkStats.Bucket()
            while (it.hasNextBucket()) {
                it.getNextBucket(bucket)
                rxByUid.merge(bucket.uid, bucket.rxBytes, Long::plus)
                txByUid.merge(bucket.uid, bucket.txBytes, Long::plus)
            }
        }
    }

    private fun toAppUsage(uid: Int, rx: Long, tx: Long): AppNetworkUsage? {
        val (packageName, label, isSystem) = resolveUid(uid) ?: return null
        return AppNetworkUsage(uid, packageName, label, rx, tx, isSystem)
    }

    private fun resolveUid(uid: Int): Triple<String, String, Boolean>? {
        // UID speciali di sistema che non corrispondono a un pacchetto
        when (uid) {
            NetworkStats.Bucket.UID_REMOVED ->
                return Triple("uid.removed", "App disinstallate", true)
            NetworkStats.Bucket.UID_TETHERING ->
                return Triple("uid.tethering", "Tethering / hotspot", true)
            android.os.Process.SYSTEM_UID ->
                return Triple("android.system", "Sistema Android", true)
        }

        val packages = packageManager.getPackagesForUid(uid) ?: return null
        val packageName = packages.firstOrNull() ?: return null
        return try {
            val info: ApplicationInfo = packageManager.getApplicationInfo(packageName, 0)
            val label = packageManager.getApplicationLabel(info).toString()
            val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            Triple(packageName, label, isSystem)
        } catch (e: PackageManager.NameNotFoundException) {
            Triple(packageName, packageName, false)
        }
    }
}
