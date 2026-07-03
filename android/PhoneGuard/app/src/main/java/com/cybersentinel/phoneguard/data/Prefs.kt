package com.cybersentinel.phoneguard.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Regole di automazione dell'app (i toggle di Dashboard/Impostazioni).
 * Unico punto di accesso alle SharedPreferences.
 */
object Prefs {

    private const val NAME = "phoneguard"

    private const val KEY_MONITORING = "monitoring_enabled"
    private const val KEY_BATTERY_ALERTS = "battery_alerts_enabled"
    private const val KEY_AUTO_FILE_SCAN = "auto_file_scan_enabled"
    private const val KEY_APP_START_ALERTS = "app_start_alerts_enabled"
    private const val KEY_LOGGING = "logging_enabled"
    private const val KEY_THREAT_SCAN = "threat_scan_enabled"
    private const val KEY_NETWORK_ALERTS = "network_alerts_enabled"

    private fun sp(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Monitoraggio continuo in background (servizio in foreground). */
    fun monitoringEnabled(context: Context): Boolean =
        sp(context).getBoolean(KEY_MONITORING, true)

    fun setMonitoringEnabled(context: Context, enabled: Boolean) =
        sp(context).edit().putBoolean(KEY_MONITORING, enabled).apply()

    /** Avvisi per surriscaldamento e scarica anomala della batteria. */
    fun batteryAlertsEnabled(context: Context): Boolean =
        sp(context).getBoolean(KEY_BATTERY_ALERTS, true)

    fun setBatteryAlertsEnabled(context: Context, enabled: Boolean) =
        sp(context).edit().putBoolean(KEY_BATTERY_ALERTS, enabled).apply()

    /** Scansione automatica dei nuovi file sospetti a ogni ciclo. */
    fun autoFileScanEnabled(context: Context): Boolean =
        sp(context).getBoolean(KEY_AUTO_FILE_SCAN, true)

    fun setAutoFileScanEnabled(context: Context, enabled: Boolean) =
        sp(context).edit().putBoolean(KEY_AUTO_FILE_SCAN, enabled).apply()

    /** Avviso popup quando un'app si avvia in background da sola. */
    fun appStartAlertsEnabled(context: Context): Boolean =
        sp(context).getBoolean(KEY_APP_START_ALERTS, true)

    fun setAppStartAlertsEnabled(context: Context, enabled: Boolean) =
        sp(context).edit().putBoolean(KEY_APP_START_ALERTS, enabled).apply()

    /** Registro attività (log) su file locale. */
    fun loggingEnabled(context: Context): Boolean =
        sp(context).getBoolean(KEY_LOGGING, false)

    fun setLoggingEnabled(context: Context, enabled: Boolean) =
        sp(context).edit().putBoolean(KEY_LOGGING, enabled).apply()

    /** Scansione anti-spyware periodica in background. */
    fun threatScanEnabled(context: Context): Boolean =
        sp(context).getBoolean(KEY_THREAT_SCAN, true)

    fun setThreatScanEnabled(context: Context, enabled: Boolean) =
        sp(context).edit().putBoolean(KEY_THREAT_SCAN, enabled).apply()

    /** Avvisi per traffico dati in uscita sospetto. */
    fun networkAlertsEnabled(context: Context): Boolean =
        sp(context).getBoolean(KEY_NETWORK_ALERTS, true)

    fun setNetworkAlertsEnabled(context: Context, enabled: Boolean) =
        sp(context).edit().putBoolean(KEY_NETWORK_ALERTS, enabled).apply()
}
