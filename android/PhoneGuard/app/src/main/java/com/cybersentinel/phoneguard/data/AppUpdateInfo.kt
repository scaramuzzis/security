package com.cybersentinel.phoneguard.data

/**
 * Stato di aggiornamento di un'app installata dal Play Store.
 *
 * Nota di piattaforma: Android non espone alle app di terze parti se esista
 * una versione più recente sullo store. Usiamo quindi la data dell'ultimo
 * aggiornamento come indicatore: un'app ferma da molto tempo è quella con
 * più probabilità di avere aggiornamenti (anche di sicurezza) in sospeso.
 */
data class AppUpdateInfo(
    val packageName: String,
    val appLabel: String,
    val versionName: String,
    val lastUpdateTime: Long,
    val daysSinceUpdate: Int,
    val stale: Boolean
)
