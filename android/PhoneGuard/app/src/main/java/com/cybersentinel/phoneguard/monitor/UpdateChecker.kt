package com.cybersentinel.phoneguard.monitor

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.cybersentinel.phoneguard.data.AppUpdateInfo

/**
 * Elenca le app installate dal Play Store ordinandole per "anzianità"
 * dell'ultimo aggiornamento: quelle ferme da più tempo compaiono per prime
 * e vengono segnalate come potenzialmente da aggiornare.
 *
 * L'aggiornamento vero avviene aprendo la scheda dell'app sul Play Store
 * (unico canale consentito a un'app non di sistema).
 */
class UpdateChecker(private val context: Context) {

    fun list(): List<AppUpdateInfo> {
        val pm = context.packageManager
        val now = System.currentTimeMillis()

        return pm.getInstalledApplications(0)
            .asSequence()
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .filter { it.packageName != context.packageName }
            .filter { installedFromPlayStore(pm, it.packageName) }
            .mapNotNull { app ->
                val info = runCatching {
                    pm.getPackageInfo(app.packageName, 0)
                }.getOrNull() ?: return@mapNotNull null
                val days = ((now - info.lastUpdateTime) / DAY_MS).toInt()
                AppUpdateInfo(
                    packageName = app.packageName,
                    appLabel = pm.getApplicationLabel(app).toString(),
                    versionName = info.versionName ?: "?",
                    lastUpdateTime = info.lastUpdateTime,
                    daysSinceUpdate = days,
                    stale = days >= STALE_DAYS
                )
            }
            .sortedByDescending { it.daysSinceUpdate }
            .toList()
    }

    private fun installedFromPlayStore(pm: PackageManager, packageName: String): Boolean {
        val installer = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(packageName)
            }
        }.getOrNull()
        return installer == PLAY_STORE
    }

    companion object {
        private const val PLAY_STORE = "com.android.vending"
        private const val DAY_MS = 24L * 60 * 60 * 1000

        /** Oltre questa soglia (giorni) l'app è segnalata come da aggiornare. */
        const val STALE_DAYS = 120
    }
}
