package com.cybersentinel.netshare

import com.cybersentinel.netshare.data.SmbEntry
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import java.io.File
import java.util.EnumSet

/**
 * Browser di una cartella condivisa SMB (Samba / Windows / NAS).
 *
 * Tiene aperta la connessione, la sessione e la condivisione, e permette di
 * navigare tra le cartelle, elencare i file e scaricarli in locale.
 *
 * Tutte le chiamate sono di rete: vanno eseguite fuori dal thread principale.
 */
class SmbBrowser {

    private var client: SMBClient? = null
    private var connection: Connection? = null
    private var session: Session? = null
    private var share: DiskShare? = null

    /** Percorso corrente relativo alla radice della condivisione. */
    var currentPath: String = ""
        private set

    /**
     * Si connette all'host, autentica e apre la condivisione indicata.
     * Con [guest] = true ignora utente/password.
     */
    fun connect(
        host: String,
        shareName: String,
        username: String,
        password: String,
        domain: String,
        guest: Boolean
    ) {
        close()
        val c = SMBClient()
        val conn = c.connect(host)
        val auth = when {
            guest -> AuthenticationContext.guest()
            username.isBlank() -> AuthenticationContext.anonymous()
            else -> AuthenticationContext(username, password.toCharArray(), domain.ifBlank { null })
        }
        val sess = conn.authenticate(auth)
        val dsk = sess.connectShare(shareName) as DiskShare

        client = c
        connection = conn
        session = sess
        share = dsk
        currentPath = ""
    }

    /** Elenca la cartella corrente (directory prima, poi file, per nome). */
    fun list(): List<SmbEntry> {
        val dsk = share ?: return emptyList()
        return dsk.list(currentPath)
            .asSequence()
            .filter { it.fileName != "." && it.fileName != ".." }
            .map { info ->
                val isDir = (info.fileAttributes and
                        FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
                SmbEntry(info.fileName, isDir, info.endOfFile)
            }
            .sortedWith(compareByDescending<SmbEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
            .toList()
    }

    fun enterDirectory(name: String) {
        currentPath = if (currentPath.isEmpty()) name else "$currentPath\\$name"
    }

    /** Risale alla cartella superiore; ritorna false se si è già alla radice. */
    fun goUp(): Boolean {
        if (currentPath.isEmpty()) return false
        currentPath = currentPath.substringBeforeLast('\\', "")
        return true
    }

    /** Scarica un file della cartella corrente in [destination]. */
    fun download(name: String, destination: File) {
        val dsk = share ?: return
        val path = if (currentPath.isEmpty()) name else "$currentPath\\$name"
        val remote = dsk.openFile(
            path,
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            EnumSet.noneOf(SMB2CreateOptions::class.java)
        )
        remote.use { r ->
            destination.outputStream().use { out ->
                r.inputStream.copyTo(out)
            }
        }
    }

    fun close() {
        runCatching { share?.close() }
        runCatching { session?.close() }
        runCatching { connection?.close() }
        runCatching { client?.close() }
        share = null; session = null; connection = null; client = null
        currentPath = ""
    }
}
