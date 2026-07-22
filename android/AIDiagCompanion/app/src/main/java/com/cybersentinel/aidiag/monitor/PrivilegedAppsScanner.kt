package com.cybersentinel.aidiag.monitor

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Enumera le app con permessi privilegiati (amministratore dispositivo,
 * servizi di accessibilità, lettura notifiche) — stessa tecnica di
 * PhoneGuard (API pubbliche, nessuna dipendenza diretta fra le due app).
 *
 * Il valore aggiunto di questa app non è vedere QUALI app li hanno ora
 * (PhoneGuard lo fa già), ma QUANDO sono comparse: una concessione nuova
 * rispetto all'ultimo controllo è un segnale più forte di una semplice
 * presenza statica.
 */
class PrivilegedAppsScanner(private val context: Context) {

    fun deviceAdmins(): Set<String> {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.activeAdmins?.map { it.packageName }?.toSet() ?: emptySet()
    }

    fun accessibilityServices(): Set<String> =
        nonSystemPackages(enabledServicePackages(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES))

    fun notificationListeners(): Set<String> =
        nonSystemPackages(enabledServicePackages("enabled_notification_listeners"))

    fun appLabel(packageName: String): String = runCatching {
        context.packageManager.getApplicationLabel(
            context.packageManager.getApplicationInfo(packageName, 0)
        ).toString()
    }.getOrDefault(packageName)

    private fun enabledServicePackages(settingKey: String): Set<String> {
        val raw = Settings.Secure.getString(context.contentResolver, settingKey) ?: return emptySet()
        return raw.split(':')
            .mapNotNull { entry ->
                ComponentName.unflattenFromString(entry)?.packageName
                    ?: entry.substringBefore('/').takeIf { it.isNotBlank() }
            }
            .toSet()
    }

    private fun nonSystemPackages(packages: Set<String>): Set<String> {
        val pm = context.packageManager
        return packages.filterNot { pkg ->
            runCatching { (pm.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_SYSTEM) != 0 }
                .getOrDefault(false)
        }.toSet()
    }
}
