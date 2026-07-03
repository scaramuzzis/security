package com.cybersentinel.phoneguard.data

/**
 * Occupazione di spazio di un'app: codice (APK), dati utente e cache.
 * La cache è ciò che si può liberare senza perdere dati.
 */
data class AppStorageInfo(
    val packageName: String,
    val appLabel: String,
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
    val isSystemApp: Boolean
) {
    val totalBytes: Long get() = appBytes + dataBytes + cacheBytes
}
