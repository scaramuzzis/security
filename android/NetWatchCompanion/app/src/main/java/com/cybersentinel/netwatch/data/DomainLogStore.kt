package com.cybersentinel.netwatch.data

/**
 * Registro in memoria delle richieste DNS osservate mentre la protezione è
 * attiva. Solo in memoria (nessuna persistenza su disco): questa app mostra
 * solo l'attività recente/dal vivo, per restare semplice — si azzera se
 * l'app o il servizio vengono terminati.
 */
object DomainLogStore {
    private const val MAX_ENTRIES = 500
    private val entries = ArrayList<DomainLogEntry>()

    fun add(entry: DomainLogEntry) {
        synchronized(entries) {
            entries.add(0, entry)
            while (entries.size > MAX_ENTRIES) entries.removeAt(entries.size - 1)
        }
    }

    fun snapshot(): List<DomainLogEntry> = synchronized(entries) { entries.toList() }

    fun clear() = synchronized(entries) { entries.clear() }
}
