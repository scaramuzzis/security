package com.cybersentinel.phoneguard

import com.cybersentinel.phoneguard.monitor.FileScanner
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica la classificazione dei nomi file della scansione anti-malware.
 */
class FileClassificationTest {

    @Test
    fun `documenti e foto normali non sono segnalati`() {
        assertNull(FileScanner.classifyName("vacanze.jpg"))
        assertNull(FileScanner.classifyName("contratto.pdf"))
        assertNull(FileScanner.classifyName("musica.mp3"))
        assertNull(FileScanner.classifyName("archivio.zip"))
    }

    @Test
    fun `apk viene segnalato`() {
        assertNotNull(FileScanner.classifyName("app-release.apk"))
    }

    @Test
    fun `doppia estensione viene riconosciuta come tale`() {
        val reason = FileScanner.classifyName("fattura.pdf.apk")
        assertNotNull(reason)
        assertTrue(reason!!.contains("Doppia estensione"))
    }

    @Test
    fun `eseguibile nascosto viene riconosciuto`() {
        val reason = FileScanner.classifyName(".update.sh")
        assertNotNull(reason)
        assertTrue(reason!!.contains("nascosto"))
    }

    @Test
    fun `la classificazione ignora maiuscole e minuscole`() {
        assertNotNull(FileScanner.classifyName("Installer.APK"))
    }

    @Test
    fun `file senza estensione non viene segnalato`() {
        assertNull(FileScanner.classifyName("README"))
    }
}
