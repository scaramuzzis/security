package com.cybersentinel.phoneguard

import com.cybersentinel.phoneguard.data.ThreatSignals
import com.cybersentinel.phoneguard.monitor.ThreatScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica che i pesi dello scoring anti-spyware non generino
 * falsi positivi su app legittime e non perdano i casi reali.
 */
class ThreatScoringTest {

    @Test
    fun `app pulita non viene segnalata`() {
        assertEquals(0, ThreatScanner.score(ThreatSignals()))
    }

    @Test
    fun `stalkerware noto supera sempre la soglia di allerta`() {
        val score = ThreatScanner.score(ThreatSignals(knownStalkerware = true))
        assertTrue(score >= ThreatScanner.ALERT_THRESHOLD)
    }

    @Test
    fun `tastiera legittima dal Play Store non viene segnalata`() {
        // nascosta dal launcher (normale per una tastiera) + microfono
        val signals = ThreatSignals(
            hiddenFromLauncher = true,
            surveillancePermissions = listOf("RECORD_AUDIO")
        )
        assertTrue(ThreatScanner.score(signals) < ThreatScanner.REPORT_THRESHOLD)
    }

    @Test
    fun `app nascosta e sideload viene segnalata`() {
        val signals = ThreatSignals(hiddenFromLauncher = true, sideloaded = true)
        assertTrue(ThreatScanner.score(signals) >= ThreatScanner.REPORT_THRESHOLD)
    }

    @Test
    fun `profilo spyware tipico raggiunge il rischio critico`() {
        // nascosta, sideload, accessibilita', admin, legge notifiche, molti permessi
        val signals = ThreatSignals(
            hiddenFromLauncher = true,
            sideloaded = true,
            accessibilityEnabled = true,
            deviceAdmin = true,
            notificationListener = true,
            surveillancePermissions = listOf(
                "RECORD_AUDIO", "ACCESS_FINE_LOCATION", "READ_SMS",
                "READ_CALL_LOG", "CAMERA"
            )
        )
        assertTrue(ThreatScanner.score(signals) >= 80)
    }

    @Test
    fun `il punteggio e limitato a 100`() {
        val signals = ThreatSignals(
            knownStalkerware = true,
            suspiciousName = true,
            hiddenFromLauncher = true,
            sideloaded = true,
            accessibilityEnabled = true,
            deviceAdmin = true,
            notificationListener = true,
            surveillancePermissions = List(10) { "P$it" }
        )
        assertEquals(100, ThreatScanner.score(signals))
    }
}
