package com.cybersentinel.phoneguard.monitor

import android.content.Context
import com.cybersentinel.phoneguard.data.FileEntry
import com.cybersentinel.phoneguard.data.FileOpResult
import java.io.File

/**
 * Facciata usata dal file manager: aggiunge alle operazioni pure di
 * [FileOps] ciò che richiede un `Context` Android — il controllo del
 * permesso di archiviazione e l'elenco dei volumi disponibili
 * (memoria interna + microSD/USB, via [FileScanner]).
 */
class FileRepository(context: Context) {

    private val fileScanner = FileScanner(context)

    fun hasStorageAccess(): Boolean = fileScanner.hasStorageAccess()

    /** Radici disponibili: memoria interna + ogni volume rimovibile. */
    fun roots(): List<File> = fileScanner.storageRoots()

    fun list(dir: File): List<FileEntry> = FileOps.list(dir)

    fun copy(sources: List<File>, destDir: File): FileOpResult = FileOps.copy(sources, destDir)

    fun move(sources: List<File>, destDir: File): FileOpResult = FileOps.move(sources, destDir)

    fun delete(files: List<File>): FileOpResult = FileOps.delete(files)

    fun createFolder(parent: File, name: String): Boolean = FileOps.createFolder(parent, name)

    fun rename(file: File, newName: String): File? = FileOps.rename(file, newName)
}
