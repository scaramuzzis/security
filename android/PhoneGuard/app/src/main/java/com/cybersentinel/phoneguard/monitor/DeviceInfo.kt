package com.cybersentinel.phoneguard.monitor

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import java.io.File
import java.util.Locale

/** Riga della pagina informazioni: intestazione di sezione o voce. */
sealed class DeviceInfoRow {
    data class Header(val title: String) : DeviceInfoRow()
    data class Entry(val label: String, val value: String) : DeviceInfoRow()
}

/**
 * Raccoglie tutte le informazioni utili sul dispositivo e sul sistema,
 * organizzate in sezioni. Nessun dato lascia il telefono.
 */
class DeviceInfo(private val context: Context) {

    fun collect(): List<DeviceInfoRow> {
        val rows = ArrayList<DeviceInfoRow>()

        rows.add(DeviceInfoRow.Header("Sistema Android"))
        rows.add(DeviceInfoRow.Entry("Versione Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"))
        rows.add(DeviceInfoRow.Entry("Livello patch di sicurezza", Build.VERSION.SECURITY_PATCH.ifBlank { "non disponibile" }))
        rows.add(DeviceInfoRow.Entry("Build", Build.DISPLAY))
        rows.add(DeviceInfoRow.Entry("Kernel", System.getProperty("os.version") ?: "?"))
        rows.add(DeviceInfoRow.Entry("Tempo di attività", formatUptime(SystemClock.elapsedRealtime())))

        rows.add(DeviceInfoRow.Header("Dispositivo"))
        rows.add(DeviceInfoRow.Entry("Produttore", Build.MANUFACTURER))
        rows.add(DeviceInfoRow.Entry("Modello", Build.MODEL))
        rows.add(DeviceInfoRow.Entry("Nome commerciale", Build.PRODUCT))
        rows.add(DeviceInfoRow.Entry("Scheda / dispositivo", "${Build.BOARD} / ${Build.DEVICE}"))
        rows.add(DeviceInfoRow.Entry("Architetture CPU", Build.SUPPORTED_ABIS.joinToString(", ")))
        rows.add(DeviceInfoRow.Entry("Core CPU", Runtime.getRuntime().availableProcessors().toString()))
        rows.add(DeviceInfoRow.Entry("Schermo", screenInfo()))

        rows.add(DeviceInfoRow.Header("Memoria"))
        val mem = memoryInfo()
        rows.add(DeviceInfoRow.Entry("RAM totale", SecurityAnalyst.formatSize(mem.totalMem)))
        rows.add(DeviceInfoRow.Entry("RAM disponibile", SecurityAnalyst.formatSize(mem.availMem)))
        val data = StatFs(Environment.getDataDirectory().path)
        rows.add(DeviceInfoRow.Entry("Archiviazione totale", SecurityAnalyst.formatSize(data.totalBytes)))
        rows.add(DeviceInfoRow.Entry("Archiviazione libera", SecurityAnalyst.formatSize(data.availableBytes)))
        rows.add(DeviceInfoRow.Entry("Scheda SD/USB", externalVolumes()))

        rows.add(DeviceInfoRow.Header("Batteria"))
        val battery = BatteryMonitor(context).snapshot()
        rows.add(DeviceInfoRow.Entry("Livello", "${battery.levelPercent}% (${if (battery.isCharging) "in carica" else "in scarica"})"))
        rows.add(DeviceInfoRow.Entry("Temperatura", String.format(Locale.ITALY, "%.1f °C", battery.temperatureCelsius)))
        rows.add(DeviceInfoRow.Entry("Salute", battery.healthLabel))
        rows.add(DeviceInfoRow.Entry("Tensione", "${battery.voltageMillivolt} mV"))

        rows.add(DeviceInfoRow.Header("Sicurezza"))
        rows.add(DeviceInfoRow.Entry("Cifratura archiviazione", encryptionStatus()))
        rows.add(DeviceInfoRow.Entry("Bootloader", Build.BOOTLOADER))
        rows.add(DeviceInfoRow.Entry("Segni di root", if (rooted()) "⚠️ rilevati" else "nessuno"))
        rows.add(DeviceInfoRow.Entry("App installate", installedAppCount()))

        return rows
    }

    private fun memoryInfo(): ActivityManager.MemoryInfo {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
    }

    private fun screenInfo(): String {
        val m = context.resources.displayMetrics
        return "${m.widthPixels}×${m.heightPixels} px · ${m.densityDpi} dpi"
    }

    private fun externalVolumes(): String {
        val roots = context.getExternalFilesDirs(null)
            .filterNotNull()
            .drop(1) // il primo è la memoria interna
        return if (roots.isEmpty()) "nessuna" else "${roots.size} volume/i rimovibile/i"
    }

    private fun encryptionStatus(): String {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return when (dpm.storageEncryptionStatus) {
            DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER,
            DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE -> "attiva"
            DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE -> "non attiva"
            DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED -> "non supportata"
            else -> "sconosciuta"
        }
    }

    private fun rooted(): Boolean {
        if (Build.TAGS?.contains("test-keys") == true) return true
        val paths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/data/adb/magisk", "/sbin/.magisk"
        )
        return paths.any { runCatching { File(it).exists() }.getOrDefault(false) }
    }

    private fun installedAppCount(): String {
        val all = context.packageManager.getInstalledApplications(0)
        val user = all.count { (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
        return "${all.size} totali · $user utente"
    }

    private fun formatUptime(millis: Long): String {
        val totalMinutes = millis / 60000
        val days = totalMinutes / (60 * 24)
        val hours = (totalMinutes / 60) % 24
        val minutes = totalMinutes % 60
        return buildString {
            if (days > 0) append("${days}g ")
            append("${hours}h ${minutes}m")
        }
    }
}
