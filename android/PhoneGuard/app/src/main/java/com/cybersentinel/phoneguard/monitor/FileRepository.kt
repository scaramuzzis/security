package com.cybersentinel.phoneguard.monitor

import com.cybersentinel.phoneguard.data.FileEntry
import com.cybersentinel.phoneguard.data.FileOpResult
import java.io.File

/**
 * Operazioni del file manager: elenco cartelle/file, copia, spostamento
 * (taglia+incolla) ed eliminazione. Riusa [FileScanner.storageRoots] per
 * elencare memoria interna e ogni volume rimovibile (microSD/USB).
 *
 * Sicurezza: copia/spostamento risolvono i conflitti di nome aggiungendo
 * un suffisso "(2)", "(3)", ... invece di sovrascrivere silenziosamente
 * un file esistente — evita perdite di dati accidentali.
 */
class FileRepository(context: android.content.Context) {

    private val fileScanner = FileScanner(context)

    fun hasStorageAccess(): Boolean = fileScanner.hasStorageAccess()

    /** Radici disponibili: memoria interna + ogni volume rimovibile. */
    fun roots(): List<File> = fileScanner.storageRoots()

    /** Contenuto di una cartella, ordinato: cartelle prima, poi file, per nome. */
    fun list(dir: File): List<FileEntry> =
        dir.listFiles()
            ?.map { FileEntry(it, it.isDirectory, if (it.isDirectory) 0 else it.length(), it.lastModified()) }
            ?.sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.file.name.lowercase() })
            ?: emptyList()

    /** Copia i file/cartelle indicati dentro [destDir]. Non sovrascrive mai. */
    fun copy(sources: List<File>, destDir: File): FileOpResult {
        var ok = 0
        var failed = 0
        var bytes = 0L
        for (src in sources) {
            val target = uniqueTarget(destDir, src.name)
            val copiedBytes = runCatching { copyRecursively(src, target) }.getOrNull()
            if (copiedBytes != null) { ok++; bytes += copiedBytes } else failed++
        }
        return FileOpResult(ok, failed, bytes)
    }

    /** Sposta i file/cartelle: copia e poi elimina l'originale (sicuro tra volumi diversi). */
    fun move(sources: List<File>, destDir: File): FileOpResult {
        var ok = 0
        var failed = 0
        var bytes = 0L
        for (src in sources) {
            val target = uniqueTarget(destDir, src.name)
            val copiedBytes = runCatching { copyRecursively(src, target) }.getOrNull()
            if (copiedBytes != null && deleteRecursively(src)) {
                ok++; bytes += copiedBytes
            } else failed++
        }
        return FileOpResult(ok, failed, bytes)
    }

    fun delete(files: List<File>): FileOpResult {
        var ok = 0
        var failed = 0
        for (f in files) {
            if (deleteRecursively(f)) ok++ else failed++
        }
        return FileOpResult(ok, failed, 0)
    }

    fun createFolder(parent: File, name: String): Boolean {
        val target = uniqueTarget(parent, name)
        return target.mkdirs()
    }

    fun rename(file: File, newName: String): File? {
        val target = File(file.parentFile, newName)
        if (target.exists()) return null
        return if (file.renameTo(target)) target else null
    }

    /** Nome libero in [dir]: aggiunge " (2)", " (3)"... se esiste già. */
    private fun uniqueTarget(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dotIndex = name.lastIndexOf('.')
        val base = if (dotIndex > 0) name.substring(0, dotIndex) else name
        val ext = if (dotIndex > 0) name.substring(dotIndex) else ""
        var i = 2
        do {
            candidate = File(dir, "$base ($i)$ext")
            i++
        } while (candidate.exists())
        return candidate
    }

    /** Copia ricorsiva; ritorna i byte copiati o lancia in caso di errore. */
    private fun copyRecursively(src: File, dest: File): Long {
        if (src.isDirectory) {
            if (!dest.mkdirs() && !dest.isDirectory) throw java.io.IOException("mkdir fallita: $dest")
            var total = 0L
            src.listFiles()?.forEach { child ->
                total += copyRecursively(child, File(dest, child.name))
            }
            return total
        }
        src.copyTo(dest, overwrite = false)
        return dest.length()
    }

    private fun deleteRecursively(file: File): Boolean = runCatching {
        if (file.isDirectory) file.listFiles()?.forEach { deleteRecursively(it) }
        file.delete()
    }.getOrDefault(false)
}
