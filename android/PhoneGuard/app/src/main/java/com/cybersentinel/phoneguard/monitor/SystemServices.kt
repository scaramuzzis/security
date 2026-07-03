package com.cybersentinel.phoneguard.monitor

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/**
 * Letture di sistema condivise tra ThreatScanner e SystemAnalyzer.
 */
object SystemServices {

    const val NOTIFICATION_LISTENERS_SETTING = "enabled_notification_listeners"

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
