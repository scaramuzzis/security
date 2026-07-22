package com.cybersentinel.phoneguard.data

import java.io.File

/** Voce mostrata nel file manager: una cartella o un file. */
data class FileEntry(
    val file: File,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long
)

/** Esito di un'operazione di copia/spostamento su più file. */
data class FileOpResult(val succeeded: Int, val failed: Int, val bytesCopied: Long)

enum class ViewMode { LIST, GRID }
