package com.cybersentinel.phoneguard.monitor

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import com.cybersentinel.phoneguard.data.AppStorageInfo

/**
 * Inventario di TUTTE le app installate — app utente e app di sistema —
 * con l'occupazione reale di spazio (codice, dati, cache) letta da
 * StorageStatsManager.
 *
 * Nota di piattaforma: i file interni del sistema operativo (/system, i
 * dati privati delle altre app) non sono leggibili da un'app non-root;
 * questo inventario è la fotografia più completa consentita e mostra,
 * per ogni app, quanto occupa e quanta cache si può liberare.
 *
 * Richiede il permesso "Accesso ai dati di utilizzo" (PACKAGE_USAGE_STATS).
 */
class AppInventory(private val context: Context) {

    fun hasStorageStatsAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Tutte le app ordinate per spazio occupato decrescente. */
    fun list(includeSystem: Boolean = true): List<AppStorageInfo> {
        val pm = context.packageManager
        val ssm = context.getSystemService(Context.STORAGE_STATS_SERVICE)
                as StorageStatsManager

        return pm.getInstalledApplications(0)
            .asSequence()
            .filter { includeSystem || (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .map { app ->
                val stats = runCatching {
                    ssm.queryStatsForPackage(
                        StorageManager.UUID_DEFAULT,
                        app.packageName,
                        Process.myUserHandle()
                    )
                }.getOrNull()
                AppStorageInfo(
                    packageName = app.packageName,
                    appLabel = pm.getApplicationLabel(app).toString(),
                    appBytes = stats?.appBytes ?: 0,
                    dataBytes = stats?.dataBytes ?: 0,
                    cacheBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        stats?.cacheBytes ?: 0 else 0,
                    isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
            .sortedByDescending { it.totalBytes }
            .toList()
    }

    /** Cache totale liberabile su tutte le app. */
    fun totalCacheBytes(apps: List<AppStorageInfo>): Long = apps.sumOf { it.cacheBytes }
}
