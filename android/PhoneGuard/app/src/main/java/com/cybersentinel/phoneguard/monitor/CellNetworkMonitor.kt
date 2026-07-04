package com.cybersentinel.phoneguard.monitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.cybersentinel.phoneguard.data.CellNetworkStatus

/**
 * Analizza il tipo di rete cellulare corrente per rilevare un segnale
 * classico di sorveglianza attiva: il **downgrade forzato a 2G**.
 *
 * Un IMSI-catcher (Stingray/finta stazione radio base) spesso costringe il
 * telefono a passare al 2G perché quella rete, più vecchia, ha una
 * cifratura debole o assente ed è più facile da intercettare. Se il
 * telefono è improvvisamente su 2G in una zona dove di solito prendi
 * 4G/5G, è un indizio da non ignorare — anche se può avere anche cause
 * innocue (zona con scarsa copertura).
 *
 * Limite onesto di piattaforma: un'app normale non ha accesso al livello
 * di segnalazione radio/baseband, quindi non può rilevare con certezza un
 * IMSI-catcher. Questo è un indizio euristico, non una prova.
 */
class CellNetworkMonitor(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

    fun analyze(): CellNetworkStatus {
        if (!hasPermission()) {
            return CellNetworkStatus(false, "sconosciuta", false, "")
        }
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val type = runCatching { tm.dataNetworkType }.getOrDefault(TelephonyManager.NETWORK_TYPE_UNKNOWN)

        val is2g = type in setOf(
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_1xRTT
        )
        val label = networkTypeLabel(type)
        val detail = if (is2g)
            "La connessione cellulare è su rete 2G ($label), che usa una cifratura debole o assente ed è il bersaglio classico degli IMSI-catcher (finte stazioni radio base). Se di solito qui prendi 4G/5G, verifica: potrebbe essere solo scarsa copertura, ma vale la pena controllare."
        else
            "Rete cellulare: $label. Nessun downgrade a 2G rilevato."

        return CellNetworkStatus(true, label, is2g, detail)
    }

    private fun networkTypeLabel(type: Int): String = when (type) {
        TelephonyManager.NETWORK_TYPE_NR -> "5G"
        TelephonyManager.NETWORK_TYPE_LTE -> "4G/LTE"
        TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_HSPAP,
        TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
        TelephonyManager.NETWORK_TYPE_GPRS -> "2G (GPRS)"
        TelephonyManager.NETWORK_TYPE_EDGE -> "2G (EDGE)"
        TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT -> "2G (CDMA)"
        else -> "sconosciuta o nessun dato mobile"
    }
}
