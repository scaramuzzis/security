package com.cybersentinel.phoneguard.monitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.cybersentinel.phoneguard.data.SuspiciousFile
import java.io.File
import java.util.ArrayDeque
import java.util.Locale

/**
 * Scansiona la memoria condivisa del telefono alla ricerca di file sospetti:
 *
 *  - file eseguibili o installabili (.apk, .dex, .sh, .exe, ...) fuori posto;
 *  - file con doppia estensione che si fingono documenti o foto
 *    (es. "fattura.pdf.apk", "foto.jpg.exe") — trucco classico del phishing;
 *  - file eseguibili nascosti (nome che inizia con ".").
 *
 * Su Android 11+ serve il permesso speciale "Accesso a tutti i file"
 * (MANAGE_EXTERNAL_STORAGE) per leggere l'intera memoria condivisa.
 */
class FileScanner(private val context: Context) {

    fun hasStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

    /**
     * Scansione ricorsiva della memoria condivisa (limitata in profondità e
     * numero di file per non bloccare il dispositivo). Ritorna i file sospetti
     * ordinati dal più recente.
     */
    fun scan(maxFiles: Int = MAX_FILES): List<SuspiciousFile> {
        if (!hasStorageAccess()) return emptyList()

        val root = Environment.getExternalStorageDirectory() ?: return emptyList()
        val found = ArrayList<SuspiciousFile>()
        val queue = ArrayDeque<Pair<File, Int>>()
        queue.add(root to 0)
        var visited = 0

        while (queue.isNotEmpty() && visited < maxFiles) {
            val (dir, depth) = queue.poll() ?: break
            val children = dir.listFiles() ?: continue
            for (file in children) {
                visited++
                if (visited >= maxFiles) break
                if (file.isDirectory) {
                    // Android/data e Android/obb non sono comunque leggibili
                    val name = file.name.lowercase(Locale.ROOT)
                    if (depth < MAX_DEPTH && name != "android") {
                        queue.add(file to depth + 1)
                    }
                } else {
                    inspect(file)?.let { found.add(it) }
                }
            }
        }

        return found.sortedByDescending { it.lastModified }
    }

    /** Analizza un singolo file e ritorna il motivo se è sospetto. */
    fun inspect(file: File): SuspiciousFile? {
        val name = file.name.lowercase(Locale.ROOT)
        val parts = name.split('.')
        val ext = parts.lastOrNull() ?: return null

        val reason = when {
            // doppia estensione: si finge documento/foto ma è eseguibile
            ext in RISKY_EXTENSIONS && parts.size >= 3 &&
                    parts[parts.size - 2] in DISGUISE_EXTENSIONS ->
                "Doppia estensione: si finge .${parts[parts.size - 2]} ma è .$ext"

            // eseguibile nascosto
            ext in RISKY_EXTENSIONS && name.startsWith(".") ->
                "File eseguibile nascosto"

            // APK: installabile arrivato fuori dallo store
            ext == "apk" ->
                "Pacchetto installabile (APK) in memoria: verifica la provenienza"

            // altri eseguibili/script
            ext in RISKY_EXTENSIONS ->
                "File eseguibile o script (.$ext)"

            else -> return null
        }

        return SuspiciousFile(
            path = file.absolutePath,
            reason = reason,
            sizeBytes = file.length(),
            lastModified = file.lastModified()
        )
    }

    companion object {
        private const val MAX_DEPTH = 6
        private const val MAX_FILES = 30_000

        /** Estensioni eseguibili/installabili considerate a rischio. */
        val RISKY_EXTENSIONS = setOf(
            "apk", "xapk", "apks", "dex", "sh", "bin", "exe",
            "bat", "cmd", "scr", "jar", "msi"
        )

        /** Estensioni "innocue" usate come esca nelle doppie estensioni. */
        val DISGUISE_EXTENSIONS = setOf(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "jpg", "jpeg", "png", "gif", "mp3", "mp4", "txt", "zip"
        )
    }
}
