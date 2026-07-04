package com.cybersentinel.phoneguard.monitor

import android.content.Context
import android.content.pm.ApplicationInfo
import android.provider.Settings

/**
 * Permission Auditor: raggruppa le app per capacità critiche concesse,
 * così da vedere a colpo d'occhio chi può sorvegliare il telefono.
 *
 * Gruppi: Accessibilità, Amministratore dispositivo, Lettura notifiche,
 * Posizione sempre attiva (in background).
 */
class PermissionAuditor(private val context: Context) {

    data class PermissionGroup(
        val title: String,
        val description: String,
        val apps: List<String>
    )

    fun audit(): List<PermissionGroup> {
        val pm = context.packageManager

        val accessibility = SystemServices.enabledServicePackages(
            context, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        val admins = SystemServices.deviceAdminPackages(context)
        val listeners = SystemServices.enabledServicePackages(
            context, SystemServices.NOTIFICATION_LISTENERS_SETTING
        )
        val backgroundLocation = appsWithGrantedPermission(
            "android.permission.ACCESS_BACKGROUND_LOCATION"
        )

        fun labels(packages: Collection<String>): List<String> =
            packages.mapNotNull { pkg ->
                runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(pkg)
            }.sorted()

        return listOf(
            PermissionGroup(
                "Accessibilità",
                "Possono leggere schermo e digitazione",
                labels(accessibility)
            ),
            PermissionGroup(
                "Amministratore dispositivo",
                "Possono bloccare il telefono e resistere alla disinstallazione",
                labels(admins)
            ),
            PermissionGroup(
                "Lettura notifiche",
                "Vedono messaggi, chat e codici OTP",
                labels(listeners)
            ),
            PermissionGroup(
                "Posizione sempre attiva",
                "Ti localizzano anche quando non le usi",
                labels(backgroundLocation)
            )
        )
    }

    /** App utente con il permesso indicato effettivamente concesso. */
    private fun appsWithGrantedPermission(permission: String): List<String> =
        context.packageManager.getInstalledApplications(0)
            .asSequence()
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .filter { permission in SystemServices.grantedPermissions(context, it.packageName) }
            .map { it.packageName }
            .toList()
}
