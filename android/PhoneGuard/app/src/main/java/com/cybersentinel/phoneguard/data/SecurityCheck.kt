package com.cybersentinel.phoneguard.data

/**
 * Risultato di un singolo controllo dell'analisi di sistema.
 */
data class SecurityCheck(
    val title: String,
    val ok: Boolean,
    val detail: String
)

/**
 * File individuato come sospetto dalla scansione dell'archiviazione.
 */
data class SuspiciousFile(
    val path: String,
    val reason: String,
    val sizeBytes: Long,
    val lastModified: Long
)
