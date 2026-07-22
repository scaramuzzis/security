package com.cybersentinel.netwatch.data

/** Una richiesta DNS osservata: quale app l'ha fatta, per quale dominio, quando. */
data class DomainLogEntry(
    val packageName: String,
    val appLabel: String,
    val domain: String,
    val timestamp: Long
)
