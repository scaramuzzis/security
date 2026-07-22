package com.cybersentinel.phoneguard.monitor

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Letture di sistema condivise tra ThreatScanner, SystemAnalyzer,
 * PermissionAuditor e AppPermissionScanner.
 */
object SystemServices {

    const val NOTIFICATION_LISTENERS_SETTING = "enabled_notification_listeners"

    /** Permessi effettivamente CONCESSI a [packageName] (non solo richiesti nel manifest). */
    fun grantedPermissions(context: Context, packageName: String): Set<String> {
        val pm = context.packageManager
        val info: PackageInfo = runCatching {
            pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        }.getOrNull() ?: return emptySet()

        val requested = info.requestedPermissions ?: return emptySet()
        val flags = info.requestedPermissionsFlags ?: return emptySet()
        return requested.indices
            .filter { (flags[it] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0 }
            .map { requested[it] }
            .toSet()
    }

    /** Pacchetti con privilegi di amministratore del dispositivo. */
    fun deviceAdminPackages(context: Context): Set<String> {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.activeAdmins?.map { it.packageName }?.toSet() ?: emptySet()
    }

    /**
     * Pacchetti elencati in un'impostazione di sistema del tipo
     * "componente1:componente2:..." (servizi di accessibilità,
     * listener delle notifiche).
     */
    fun enabledServicePackages(context: Context, settingKey: String): Set<String> {
        val raw = Settings.Secure.getString(context.contentResolver, settingKey)
            ?: return emptySet()
        return raw.split(':')
            .mapNotNull { entry ->
                ComponentName.unflattenFromString(entry)?.packageName
                    ?: entry.substringBefore('/').takeIf { it.isNotBlank() }
            }
            .toSet()
    }
}
