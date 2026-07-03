package com.cybersentinel.phoneguard

import com.cybersentinel.phoneguard.data.JunkCategory
import com.cybersentinel.phoneguard.data.RiskLevel
import com.cybersentinel.phoneguard.data.SecurityCheck
import com.cybersentinel.phoneguard.monitor.JunkScanner
import com.cybersentinel.phoneguard.monitor.SecurityAnalyst
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JunkAndAnalystTest {

    // --- JunkScanner.classifyName ---

    @Test
    fun `file vuoto e junk`() {
        assertEquals(JunkCategory.EMPTY_FILE, JunkScanner.classifyName("qualsiasi.dat", 0))
    }

    @Test
    fun `file temporaneo riconosciuto`() {
        assertEquals(JunkCategory.TEMP_FILE, JunkScanner.classifyName("video.part", 1000))
        assertEquals(JunkCategory.TEMP_FILE, JunkScanner.classifyName("a.tmp", 50))
    }

    @Test
    fun `log e backup riconosciuti`() {
        assertEquals(JunkCategory.LOG_FILE, JunkScanner.classifyName("app.log", 200))
        assertEquals(JunkCategory.LOG_FILE, JunkScanner.classifyName("db.bak", 200))
    }

    @Test
    fun `documento normale non e junk`() {
        assertNull(JunkScanner.classifyName("tesi.pdf", 500_000))
        assertNull(JunkScanner.classifyName("foto.jpg", 2_000_000))
    }

    // --- SecurityAnalyst ---

    private fun okCheck() = SecurityCheck("X", true, "ok")
    private fun failCheck() = SecurityCheck("Y", false, "problema")

    @Test
    fun `telefono pulito da verdetto sicuro`() {
        val report = SecurityAnalyst.analyze(
            threats = emptyList(),
            systemChecks = listOf(okCheck(), okCheck()),
            suspiciousFiles = 0,
            reclaimableBytes = 0
        )
        assertEquals(RiskLevel.SICURO, report.verdict)
        assertTrue(report.recommendations.isNotEmpty())
    }

    @Test
    fun `molti controlli falliti alzano il verdetto`() {
        val report = SecurityAnalyst.analyze(
            threats = emptyList(),
            systemChecks = listOf(failCheck(), failCheck(), failCheck()),
            suspiciousFiles = 0,
            reclaimableBytes = 0
        )
        assertEquals(RiskLevel.ALTO, report.verdict)
    }

    @Test
    fun `spazio recuperabile genera raccomandazione di pulizia`() {
        val report = SecurityAnalyst.analyze(
            threats = emptyList(),
            systemChecks = listOf(okCheck()),
            suspiciousFiles = 0,
            reclaimableBytes = 200L * 1024 * 1024
        )
        assertTrue(report.recommendations.any { it.contains("liberare") })
    }

    @Test
    fun `formatSize e leggibile`() {
        assertEquals("1.0 MB", SecurityAnalyst.formatSize(1024L * 1024))
    }
}
