package com.cybersentinel.aidiag.analysis

import com.cybersentinel.aidiag.data.BatterySample
import com.cybersentinel.aidiag.data.NetworkSample
import com.cybersentinel.aidiag.data.PrivilegedGrant

/**
 * Logica pura di analisi (nessuna dipendenza Android, testabile con JUnit
 * puro): incrocia i campionamenti storici in pattern rilevanti, calibrata
 * per non segnalare comportamenti normali.
 *
 * Principio guida (no falsi positivi): ogni soglia richiede persistenza nel
 * tempo (più campioni consecutivi), non un singolo picco isolato — un
 * telefono può scaldare per un attimo mentre l'utente lo mette in tasca a
 * schermo appena spento, o un'app può fare un burst di rete legittimo
 * (sync, notifica push). Solo pattern ripetuti nel tempo sono segnalati.
 */
object DiagnosticAnalyzer {

    /** Sotto questa temperatura, a schermo spento, è normale: non segnalare. */
    const val HEAT_THRESHOLD_CELSIUS = 38f

    /** Un'app compare come "mittente notturno" solo se attiva in almeno questi campioni a schermo spento. */
    const val MIN_OVERNIGHT_SAMPLES = 3

    /** Un permesso concesso da meno di questi giorni è considerato "recente". */
    const val RECENT_GRANT_DAYS = 14

    fun findScreenOffHeatEpisodes(
        samples: List<BatterySample>,
        tempThreshold: Float = HEAT_THRESHOLD_CELSIUS,
        maxGapMs: Long = 30L * 60 * 1000
    ): List<HeatEpisode> {
        val hot = samples.filter { !it.screenOn && !it.isCharging && it.temperatureCelsius >= tempThreshold }
            .sortedBy { it.timestamp }
        if (hot.isEmpty()) return emptyList()

        val episodes = ArrayList<HeatEpisode>()
        var episodeStart = hot.first()
        var episodeEnd = hot.first()
        var maxTemp = hot.first().temperatureCelsius
        var count = 1

        for (i in 1 until hot.size) {
            val sample = hot[i]
            if (sample.timestamp - episodeEnd.timestamp <= maxGapMs) {
                episodeEnd = sample
                maxTemp = maxOf(maxTemp, sample.temperatureCelsius)
                count++
            } else {
                if (count >= 2) episodes.add(HeatEpisode(episodeStart.timestamp, episodeEnd.timestamp, maxTemp, count))
                episodeStart = sample
                episodeEnd = sample
                maxTemp = sample.temperatureCelsius
                count = 1
            }
        }
        if (count >= 2) episodes.add(HeatEpisode(episodeStart.timestamp, episodeEnd.timestamp, maxTemp, count))
        return episodes
    }

    fun findOvernightSenders(
        samples: List<NetworkSample>,
        minSamples: Int = MIN_OVERNIGHT_SAMPLES
    ): List<OvernightSender> {
        val screenOff = samples.filter { !it.screenOn && it.txBytes > 0 }
        return screenOff.groupBy { it.packageName }
            .mapNotNull { (pkg, group) ->
                if (group.size < minSamples) return@mapNotNull null
                OvernightSender(
                    packageName = pkg,
                    appLabel = group.first().appLabel,
                    totalBytes = group.sumOf { it.txBytes },
                    sampleCount = group.size
                )
            }
            .sortedByDescending { it.totalBytes }
    }

    /** Scarica media (%/ora) nei periodi a schermo spento, non in carica — il consumo "che nessuno vede". */
    fun estimateIdleDrainPerHour(samples: List<BatterySample>): Float? {
        val idle = samples.filter { !it.screenOn && !it.isCharging }.sortedBy { it.timestamp }
        if (idle.size < 2) return null

        var totalDropPercent = 0f
        var totalHours = 0f
        for (i in 1 until idle.size) {
            val prev = idle[i - 1]
            val curr = idle[i]
            val gapMs = curr.timestamp - prev.timestamp
            if (gapMs <= 0 || gapMs > 60L * 60 * 1000) continue // scarta buchi (schermo riacceso nel mezzo)
            val drop = prev.levelPercent - curr.levelPercent
            if (drop < 0) continue // ricarica avvenuta nel mezzo nonostante il filtro
            totalDropPercent += drop
            totalHours += gapMs / 3_600_000f
        }
        return if (totalHours > 0) totalDropPercent / totalHours else null
    }

    fun recentGrants(grants: List<PrivilegedGrant>, now: Long, withinDays: Int = RECENT_GRANT_DAYS): List<PrivilegedGrant> {
        val cutoff = now - withinDays * 24L * 60 * 60 * 1000
        return grants.filter { it.firstSeen >= cutoff }.sortedByDescending { it.firstSeen }
    }

    /**
     * Combinazioni di segnali più significative della somma delle parti: un
     * singolo indizio è spesso spiegabile, la stessa app che ricorre in più
     * indizi diversi lo è meno.
     */
    fun crossReference(
        overnightSenders: List<OvernightSender>,
        recentGrants: List<PrivilegedGrant>,
        heatEpisodes: List<HeatEpisode>
    ): List<String> {
        val findings = ArrayList<String>()
        val overnightPackages = overnightSenders.map { it.packageName }.toSet()
        val grantPackages = recentGrants.map { it.packageName }.toSet()
        val overlap = overnightPackages.intersect(grantPackages)

        overlap.forEach { pkg ->
            val sender = overnightSenders.first { it.packageName == pkg }
            val grant = recentGrants.first { it.packageName == pkg }
            findings.add(
                "${sender.appLabel}: ha ottenuto un permesso privilegiato (${grant.kind}) di recente " +
                        "E invia dati con lo schermo spento (${sender.sampleCount} rilevazioni) — la combinazione " +
                        "è più significativa dei due segnali presi singolarmente."
            )
        }

        if (heatEpisodes.isNotEmpty() && overnightSenders.isNotEmpty()) {
            findings.add(
                "Rilevati sia surriscaldamento a schermo spento (${heatEpisodes.size} episodi) sia invio dati " +
                        "notturno (${overnightSenders.size} app): può indicare un'attività di sottofondo " +
                        "insolitamente intensa, oppure semplicemente sincronizzazioni/backup schedulati."
            )
        }

        return findings
    }
}
