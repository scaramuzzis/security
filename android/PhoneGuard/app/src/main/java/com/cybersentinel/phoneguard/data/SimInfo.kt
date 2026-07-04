package com.cybersentinel.phoneguard.data

/**
 * Informazioni su una scheda SIM installata (fisica o eSIM).
 */
data class SimInfo(
    val slotIndex: Int,
    val subscriptionId: Int,
    val displayName: String,
    val carrierName: String,
    val phoneNumber: String,
    val countryIso: String,
    val isEmbedded: Boolean,
    val isOpportunistic: Boolean,
    val dataRoamingEnabled: Boolean,
    val isDefaultData: Boolean,
    val isDefaultVoice: Boolean,
    val isDefaultSms: Boolean,
    val networkTypeLabel: String
)
