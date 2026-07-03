package com.cybersentinel.phoneguard.data

/**
 * Elemento potenzialmente inutile trovato nella memoria condivisa.
 */
data class JunkItem(
    val path: String,
    val category: JunkCategory,
    val sizeBytes: Long,
    val isDirectory: Boolean
)

enum class JunkCategory(val label: String) {
    EMPTY_DIR("Cartella vuota"),
    EMPTY_FILE("File vuoto (0 byte)"),
    TEMP_FILE("File temporaneo/residuo"),
    THUMBNAIL_CACHE("Cache miniature"),
    LOG_FILE("File di log/backup")
}
