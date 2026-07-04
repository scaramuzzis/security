package com.cybersentinel.aidiag.analysis

import com.cybersentinel.aidiag.data.BatterySample
import com.cybersentinel.aidiag.data.NetworkSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticAnalyzerTest {

    private fun battery(ts: Long, level: Int, temp: Float, charging: Boolean = false, screenOn: Boolean = false) =
        BatterySample(ts, level, temp, charging, screenOn)

    @Test
    fun `un singolo campione caldo isolato non forma un episodio`() {
        val samples = listOf(battery(0, 80, 40f))
        assertTrue(DiagnosticAnalyzer.findScreenOffHeatEpisodes(samples).isEmpty())
    }

    @Test
    fun `campioni caldi consecutivi formano un episodio`() {
        val samples = listOf(
            battery(0, 80, 39f),
            battery(15 * 60_000L, 79, 40f),
            battery(30 * 60_000L, 78, 41f)
        )
        val episodes = DiagnosticAnalyzer.findScreenOffHeatEpisodes(samples)
        assertEquals(1, episodes.size)
        assertEquals(3, episodes[0].sampleCount)
        assertEquals(41f, episodes[0].maxTempCelsius)
    }

    @Test
    fun `campioni caldi con schermo acceso non contano`() {
        val samples = listOf(
            battery(0, 80, 40f, screenOn = true),
            battery(15 * 60_000L, 79, 41f, screenOn = true)
        )
        assertTrue(DiagnosticAnalyzer.findScreenOffHeatEpisodes(samples).isEmpty())
    }

    @Test
    fun `temperatura sotto soglia non segnala nulla`() {
        val samples = listOf(battery(0, 80, 30f), battery(900_000L, 79, 31f))
        assertTrue(DiagnosticAnalyzer.findScreenOffHeatEpisodes(samples).isEmpty())
    }

    @Test
    fun `un mittente notturno occasionale non viene segnalato`() {
        val samples = listOf(
            NetworkSample(0, "com.example.app", "Esempio", 1000, 0, screenOn = false)
        )
        assertTrue(DiagnosticAnalyzer.findOvernightSenders(samples).isEmpty())
    }

    @Test
    fun `un mittente notturno ripetuto viene segnalato`() {
        val samples = (0 until 4).map {
            NetworkSample(it * 900_000L, "com.example.app", "Esempio", 1000, 0, screenOn = false)
        }
        val senders = DiagnosticAnalyzer.findOvernightSenders(samples)
        assertEquals(1, senders.size)
        assertEquals(4000L, senders[0].totalBytes)
    }

    @Test
    fun `traffico a schermo acceso non conta come notturno`() {
        val samples = (0 until 5).map {
            NetworkSample(it * 900_000L, "com.example.app", "Esempio", 1000, 0, screenOn = true)
        }
        assertTrue(DiagnosticAnalyzer.findOvernightSenders(samples).isEmpty())
    }

    @Test
    fun `scarica inattiva calcolata correttamente`() {
        val samples = listOf(
            battery(0, 100, 25f),
            battery(3_600_000L, 95, 25f) // 1 ora, -5%
        )
        val drain = DiagnosticAnalyzer.estimateIdleDrainPerHour(samples)
        assertEquals(5f, drain)
    }

    @Test
    fun `senza campioni sufficienti la scarica inattiva è nulla`() {
        assertNull(DiagnosticAnalyzer.estimateIdleDrainPerHour(listOf(battery(0, 100, 25f))))
    }
}
