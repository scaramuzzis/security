package com.cybersentinel.phoneguard.monitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.cybersentinel.phoneguard.data.SimInfo

/**
 * Legge le schede SIM installate (fisiche ed eSIM) tramite
 * [SubscriptionManager]/[TelephonyManager].
 *
 * Nota di piattaforma: un'app normale (senza privilegi di sistema/operatore)
 * non può *modificare* le impostazioni della SIM — roaming dati, SIM
 * predefinita per dati/chiamate/SMS sono policy di sistema protette
 * (richiedono `MODIFY_PHONE_STATE`, riservato alle app di sistema). Questa
 * schermata mostra quindi lo stato reale e apre le schermate di sistema
 * corrette per modificarlo.
 */
class SimMonitor(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

    fun hasPhoneNumberPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_NUMBERS
        ) == PackageManager.PERMISSION_GRANTED

    fun list(): List<SimInfo> {
        if (!hasPermission()) return emptyList()
        val sm = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        val subs = runCatching { sm.activeSubscriptionInfoList }.getOrNull() ?: return emptyList()

        val defaultData = runCatching { SubscriptionManager.getDefaultDataSubscriptionId() }.getOrDefault(-1)
        val defaultVoice = runCatching { SubscriptionManager.getDefaultVoiceSubscriptionId() }.getOrDefault(-1)
        val defaultSms = runCatching { SubscriptionManager.getDefaultSmsSubscriptionId() }.getOrDefault(-1)

        return subs.map { info ->
            val tm = runCatching {
                (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)
                    .createForSubscriptionId(info.subscriptionId)
            }.getOrNull()

            val number = if (hasPhoneNumberPermission()) {
                // Sia SubscriptionInfo.number (deprecato dall'API 33) sia
                // TelephonyManager.line1Number (deprecato dall'API 29) restano
                // le uniche vie per leggere il numero: Android non offre un
                // sostituto non deprecato, quindi si usa il migliore per versione.
                @Suppress("DEPRECATION")
                val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    runCatching { tm?.line1Number }.getOrNull()
                } else {
                    runCatching { info.number }.getOrNull()
                }
                raw?.takeIf { it.isNotBlank() } ?: "non disponibile"
            } else "richiede permesso numero"

            SimInfo(
                slotIndex = info.simSlotIndex,
                subscriptionId = info.subscriptionId,
                displayName = info.displayName?.toString() ?: "SIM ${info.simSlotIndex + 1}",
                carrierName = info.carrierName?.toString() ?: "sconosciuto",
                phoneNumber = number,
                countryIso = info.countryIso?.uppercase().orEmpty(),
                isEmbedded = runCatching { info.isEmbedded }.getOrDefault(false),
                isOpportunistic = runCatching { info.isOpportunistic }.getOrDefault(false),
                dataRoamingEnabled = runCatching {
                    info.dataRoaming == SubscriptionManager.DATA_ROAMING_ENABLE
                }.getOrDefault(false),
                isDefaultData = info.subscriptionId == defaultData,
                isDefaultVoice = info.subscriptionId == defaultVoice,
                isDefaultSms = info.subscriptionId == defaultSms,
                networkTypeLabel = networkTypeLabel(tm)
            )
        }
    }

    private fun networkTypeLabel(tm: TelephonyManager?): String {
        if (tm == null) return "sconosciuta"
        val type = runCatching { tm.dataNetworkType }.getOrDefault(TelephonyManager.NETWORK_TYPE_UNKNOWN)
        return when (type) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G/LTE"
            TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
            TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
            else -> "sconosciuta"
        }
    }
}
