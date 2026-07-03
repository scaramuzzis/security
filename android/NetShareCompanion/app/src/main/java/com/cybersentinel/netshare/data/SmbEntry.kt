package com.cybersentinel.netshare.data

/**
 * Voce di una cartella condivisa: file o sottocartella.
 */
data class SmbEntry(
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long
)
