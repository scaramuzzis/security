package com.cybersentinel.phoneguard.monitor

import android.content.Context
import android.content.pm.ApplicationInfo

/** Un'app con l'autorizzazione della categoria effettivamente concessa. */
data class GrantedApp(val packageName: String, val appLabel: String)

/** Una categoria di autorizzazione "classica" (fotocamera, posizione...) con le app che la hanno concessa. */
data class PermissionCategory(val icon: String, val title: String, val apps: List<GrantedApp>)

/**
 * Raggruppa le app per le autorizzazioni che l'utente riconosce subito —
 * fotocamera, microfono, posizione, contatti, SMS, telefono, archiviazione,
 * calendario, sensori corporei — diverse dalle capacità di sorveglianza più
 * insolite già coperte da [PermissionAuditor] (accessibilità, amministratore
 * dispositivo, lettura notifiche): quelle rispondono "chi può controllare il
 * telefono", questa risponde "chi vede la mia fotocamera/posizione/rubrica".
 */
class AppPermissionScanner(private val context: Context) {

    fun scan(): List<PermissionCategory> {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(0)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }

        val appsByCategory = HashMap<String, MutableList<GrantedApp>>()
        CATEGORIES.forEach { appsByCategory[it.title] = ArrayList() }

        apps.forEach { app ->
            val granted = SystemServices.grantedPermissions(context, app.packageName)
            if (granted.isEmpty()) return@forEach
            val label = runCatching { pm.getApplicationLabel(app).toString() }.getOrDefault(app.packageName)
            CATEGORIES.forEach { category ->
                if (category.permissions.any { it in granted }) {
                    appsByCategory.getValue(category.title).add(GrantedApp(app.packageName, label))
                }
            }
        }

        return CATEGORIES.map { category ->
            PermissionCategory(
                category.icon, category.title,
                appsByCategory.getValue(category.title).sortedBy { it.appLabel.lowercase() }
            )
        }
    }

    private data class CategoryDef(val icon: String, val title: String, val permissions: Set<String>)

    companion object {
        private val CATEGORIES = listOf(
            CategoryDef("📷", "Fotocamera", setOf("android.permission.CAMERA")),
            CategoryDef("🎙️", "Microfono", setOf("android.permission.RECORD_AUDIO")),
            CategoryDef(
                "📍", "Posizione", setOf(
                    "android.permission.ACCESS_FINE_LOCATION",
                    "android.permission.ACCESS_COARSE_LOCATION",
                    "android.permission.ACCESS_BACKGROUND_LOCATION"
                )
            ),
            CategoryDef("👥", "Contatti", setOf("android.permission.READ_CONTACTS")),
            CategoryDef(
                "💬", "SMS", setOf(
                    "android.permission.READ_SMS",
                    "android.permission.SEND_SMS",
                    "android.permission.RECEIVE_SMS"
                )
            ),
            CategoryDef(
                "📞", "Telefono e chiamate", setOf(
                    "android.permission.READ_PHONE_STATE",
                    "android.permission.CALL_PHONE",
                    "android.permission.READ_CALL_LOG"
                )
            ),
            CategoryDef(
                "🗂️", "File e archiviazione", setOf(
                    "android.permission.READ_EXTERNAL_STORAGE",
                    "android.permission.READ_MEDIA_IMAGES",
                    "android.permission.READ_MEDIA_VIDEO",
                    "android.permission.READ_MEDIA_AUDIO"
                )
            ),
            CategoryDef("📅", "Calendario", setOf("android.permission.READ_CALENDAR")),
            CategoryDef("❤️", "Sensori corporei", setOf("android.permission.BODY_SENSORS"))
        )
    }
}
