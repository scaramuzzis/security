package com.cybersentinel.phoneguard.monitor

import android.content.Context
import com.cybersentinel.phoneguard.data.JunkCategory
import com.cybersentinel.phoneguard.data.JunkItem
import java.io.File
import java.util.ArrayDeque
import java.util.Locale

/**
 * Trova file e cartelle inutili nella memoria condivisa (interna + microSD)
 * e permette di eliminarli in sicurezza.
 *
 * Cosa considera "inutile" (conservativo, per non cancellare dati veri):
 *  - cartelle completamente vuote;
 *  - file di 0 byte;
 *  - file temporanei/residui per estensione (.tmp, .temp, .part, .crdownload…);
 *  - cache di miniature (.thumbnails);
 *  - file di log/backup (.log, .bak, .old).
 *
 * NON tocca mai: documenti, foto, video, audio, archivi, APK (gli APK sono
 * gestiti dalla scansione di sicurezza, non da qui).
 */
class JunkScanner(private val context: Context) {

    private val fileScanner = FileScanner(context)

    fun hasStorageAccess(): Boolean = fileScanner.hasStorageAccess()

    fun scan(maxFiles: Int = MAX_FILES): List<JunkItem> {
        val found = ArrayList<JunkItem>()

        // Cache della nostra app: sempre disponibile, non richiede permessi.
        cacheDirs().forEach { dir ->
            val size = dirSize(dir)
            if (size > 0) found.add(JunkItem(dir.absolutePath, JunkCategory.APP_CACHE, size, true))
        }

        // File inutili nella memoria condivisa (interna + microSD).
        if (hasStorageAccess()) {
            var budget = maxFiles
            for (root in fileScanner.storageRoots()) {
                if (budget <= 0) break
                budget -= scanRoot(root, found, budget)
            }
        }
        return found.sortedByDescending { it.sizeBytes }
    }

    /** Directory di cache proprie dell'app (interna + esterna). */
    private fun cacheDirs(): List<File> =
        listOfNotNull(context.cacheDir, context.externalCacheDir).filter { it.exists() }

    private fun scanRoot(root: File, found: MutableList<JunkItem>, budget: Int): Int {
        val queue = ArrayDeque<Pair<File, Int>>()
        queue.add(root to 0)
        var visited = 0

        while (queue.isNotEmpty() && visited < budget) {
            val (dir, depth) = queue.poll() ?: break
            val children = dir.listFiles()
            if (children == null) { visited++; continue }

            // Cartella vuota = candidata alla rimozione (ma non le radici)
            if (children.isEmpty() && depth > 0) {
                found.add(JunkItem(dir.absolutePath, JunkCategory.EMPTY_DIR, 0, true))
            }

            for (file in children) {
                visited++
                if (visited >= budget) break
                if (file.isDirectory) {
                    val name = file.name.lowercase(Locale.ROOT)
                    if (name == ".thumbnails") {
                        found.add(
                            JunkItem(
                                file.absolutePath,
                                JunkCategory.THUMBNAIL_CACHE,
                                dirSize(file),
                                true
                            )
                        )
                    } else if (depth < MAX_DEPTH && name != "android") {
                        queue.add(file to depth + 1)
                    }
                } else {
                    classify(file)?.let { found.add(it) }
                }
            }
        }
        return visited
    }

    /** Classifica un file; null se non è considerato inutile. */
    fun classify(file: File): JunkItem? {
        val category = classifyName(file.name, file.length()) ?: return null
        return JunkItem(file.absolutePath, category, file.length(), false)
    }

    /**
     * Elimina gli elementi indicati. Ritorna i byte effettivamente liberati.
     * Sicuro: opera solo su percorsi dentro i volumi di archiviazione noti.
     */
    fun clean(items: List<JunkItem>): Long {
        // Percorsi consentiti: volumi condivisi + cache proprie dell'app.
        val allowed = fileScanner.storageRoots().map { it.absolutePath } +
                cacheDirs().map { it.absolutePath }
        var freed = 0L
        for (item in items) {
            if (allowed.none { item.path.startsWith(it) }) continue
            val file = File(item.path)
            val size = if (item.isDirectory) dirSize(file) else file.length()
            // Per la cache dell'app svuotiamo il contenuto, non la cartella stessa.
            if (item.category == JunkCategory.APP_CACHE) {
                clearContents(file)
                freed += size
            } else if (deleteRecursively(file)) {
                freed += size
            }
        }
        return freed
    }

    private fun clearContents(dir: File) {
        dir.listFiles()?.forEach { deleteRecursively(it) }
    }

    private fun deleteRecursively(file: File): Boolean = runCatching {
        if (file.isDirectory) file.listFiles()?.forEach { deleteRecursively(it) }
        file.delete()
    }.getOrDefault(false)

    private fun dirSize(dir: File): Long = runCatching {
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }.getOrDefault(0)

    companion object {
        private const val MAX_DEPTH = 6
        private const val MAX_FILES = 40_000

        val TEMP_EXTENSIONS = setOf(
            "tmp", "temp", "part", "crdownload", "download", "partial"
        )
        val LOG_EXTENSIONS = setOf("log", "bak", "old", "dmp")

        /**
         * Classificazione pura (testabile) per nome + dimensione.
         */
        fun classifyName(fileName: String, sizeBytes: Long): JunkCategory? {
            val name = fileName.lowercase(Locale.ROOT)
            val ext = name.substringAfterLast('.', "")
            return when {
                sizeBytes == 0L -> JunkCategory.EMPTY_FILE
                ext in TEMP_EXTENSIONS -> JunkCategory.TEMP_FILE
                ext in LOG_EXTENSIONS -> JunkCategory.LOG_FILE
                name == "thumbs.db" || name == ".ds_store" -> JunkCategory.TEMP_FILE
                else -> null
            }
        }
    }
}
