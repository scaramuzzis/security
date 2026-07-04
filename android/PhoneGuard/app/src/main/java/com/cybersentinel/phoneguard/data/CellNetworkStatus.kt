package com.cybersentinel.phoneguard.data

/**
 * Stato della rete cellulare corrente, con eventuali segnali di anomalia
 * (es. downgrade a 2G, tecnica classica di un IMSI-catcher/Stingray).
 */
data class CellNetworkStatus(
    val hasReadPermission: Boolean,
    val networkTypeLabel: String,
    val isInsecure2g: Boolean,
    val detail: String
)
