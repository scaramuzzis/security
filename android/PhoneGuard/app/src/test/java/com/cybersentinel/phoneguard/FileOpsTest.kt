package com.cybersentinel.phoneguard

import com.cybersentinel.phoneguard.monitor.FileOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Test reali su file system (cartelle temporanee JVM) per la logica di
 * copia/spostamento/eliminazione del file manager — nessuna dipendenza
 * Android, quindi eseguibili senza dispositivo o Robolectric.
 */
class FileOpsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `copia un file in una cartella vuota`() {
        val src = tmp.newFile("documento.txt").apply { writeText("ciao") }
        val destDir = tmp.newFolder("destinazione")

        val result = FileOps.copy(listOf(src), destDir)

        assertEquals(1, result.succeeded)
        assertEquals(0, result.failed)
        assertTrue(File(destDir, "documento.txt").exists())
        assertTrue(src.exists()) // copy non tocca l'originale
    }

    @Test
    fun `copia non sovrascrive mai un file esistente con lo stesso nome`() {
        val src = tmp.newFile("foto.jpg").apply { writeText("nuova") }
        val destDir = tmp.newFolder("dest")
        File(destDir, "foto.jpg").writeText("vecchia, non toccare")

        FileOps.copy(listOf(src), destDir)

        assertEquals("vecchia, non toccare", File(destDir, "foto.jpg").readText())
        assertTrue(File(destDir, "foto (2).jpg").exists())
        assertEquals("nuova", File(destDir, "foto (2).jpg").readText())
    }

    @Test
    fun `conflitti multipli incrementano il suffisso`() {
        val destDir = tmp.newFolder("dest")
        File(destDir, "a.txt").writeText("1")
        File(destDir, "a (2).txt").writeText("2")

        val target = FileOps.uniqueTarget(destDir, "a.txt")

        assertEquals("a (3).txt", target.name)
    }

    @Test
    fun `sposta un file cancella l'originale dopo la copia`() {
        val src = tmp.newFile("da_spostare.txt").apply { writeText("dati") }
        val destDir = tmp.newFolder("dest")

        val result = FileOps.move(listOf(src), destDir)

        assertEquals(1, result.succeeded)
        assertFalse(src.exists())
        assertTrue(File(destDir, "da_spostare.txt").exists())
    }

    @Test
    fun `copia ricorsiva di una cartella con sottocartelle`() {
        val srcDir = tmp.newFolder("cartella")
        File(srcDir, "file1.txt").writeText("uno")
        File(srcDir, "sotto").mkdirs()
        File(srcDir, "sotto/file2.txt").writeText("due")
        val destDir = tmp.newFolder("dest")

        val result = FileOps.copy(listOf(srcDir), destDir)

        assertEquals(1, result.succeeded)
        assertTrue(File(destDir, "cartella/file1.txt").exists())
        assertTrue(File(destDir, "cartella/sotto/file2.txt").exists())
    }

    @Test
    fun `elimina file e cartelle ricorsivamente`() {
        val dir = tmp.newFolder("da_eliminare")
        File(dir, "a.txt").writeText("x")
        val lonelyFile = tmp.newFile("solo.txt")

        val result = FileOps.delete(listOf(dir, lonelyFile))

        assertEquals(2, result.succeeded)
        assertFalse(dir.exists())
        assertFalse(lonelyFile.exists())
    }

    @Test
    fun `nuova cartella con nome gia' esistente prende un suffisso`() {
        val parent = tmp.newFolder("parent")
        File(parent, "Foto").mkdirs()

        val created = FileOps.createFolder(parent, "Foto")

        assertTrue(created)
        assertTrue(File(parent, "Foto (2)").isDirectory)
    }

    @Test
    fun `rinomina fallisce se il nome di destinazione esiste gia`() {
        val parent = tmp.newFolder("parent")
        val file = File(parent, "uno.txt").apply { writeText("1") }
        File(parent, "due.txt").writeText("2")

        val renamed = FileOps.rename(file, "due.txt")

        assertNull(renamed)
        assertTrue(file.exists()) // l'originale non e' stato toccato
    }

    @Test
    fun `rinomina riesce con un nome libero`() {
        val parent = tmp.newFolder("parent")
        val file = File(parent, "uno.txt").apply { writeText("1") }

        val renamed = FileOps.rename(file, "rinominato.txt")

        assertEquals("rinominato.txt", renamed?.name)
        assertFalse(file.exists())
        assertTrue(File(parent, "rinominato.txt").exists())
    }
}
