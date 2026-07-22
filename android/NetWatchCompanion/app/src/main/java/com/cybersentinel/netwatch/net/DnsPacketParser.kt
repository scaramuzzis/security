package com.cybersentinel.netwatch.net

/** Una richiesta DNS intercettata: da dove viene e quale dominio chiede di risolvere. */
data class DnsQueryPacket(
    val sourceAddress: ByteArray,
    val sourcePort: Int,
    val destAddress: ByteArray,
    val domain: String,
    val udpPayloadOffset: Int
)

/**
 * Parser minimale IPv4 + UDP + DNS, limitato a riconoscere un pacchetto UDP
 * verso la porta 53 ed estrarne il dominio richiesto (prima domanda del
 * messaggio DNS). Non instrada altri protocolli: intercettare solo le
 * interrogazioni DNS — instradate qui perché la VpnService instrada solo il
 * traffico verso l'indirizzo DNS fittizio configurato — evita di dover
 * reimplementare un intero stack TCP/IP per vedere "quale dominio ha chiesto
 * di risolvere quest'app".
 *
 * Limite onesto: le app che usano DNS-over-HTTPS/TLS proprio (bypassando il
 * resolver di sistema) non passano da qui, quindi non compaiono nel log.
 */
object DnsPacketParser {

    private const val PROTOCOL_UDP = 17
    private const val DNS_PORT = 53
    private const val IPV4_HEADER_MIN_LENGTH = 20
    private const val UDP_HEADER_LENGTH = 8
    private const val DNS_HEADER_LENGTH = 12

    fun parse(packet: ByteArray, length: Int): DnsQueryPacket? {
        if (length < IPV4_HEADER_MIN_LENGTH) return null
        val versionAndIhl = packet[0].toInt() and 0xFF
        if (versionAndIhl shr 4 != 4) return null // solo IPv4
        val ihl = (versionAndIhl and 0x0F) * 4
        if (length < ihl + UDP_HEADER_LENGTH) return null

        val protocol = packet[9].toInt() and 0xFF
        if (protocol != PROTOCOL_UDP) return null

        val udpOffset = ihl
        val srcPort = readUShort(packet, udpOffset)
        val dstPort = readUShort(packet, udpOffset + 2)
        if (dstPort != DNS_PORT) return null

        val dnsOffset = udpOffset + UDP_HEADER_LENGTH
        val domain = parseDnsQuestionName(packet, dnsOffset, length) ?: return null

        return DnsQueryPacket(
            sourceAddress = packet.copyOfRange(12, 16),
            sourcePort = srcPort,
            destAddress = packet.copyOfRange(16, 20),
            domain = domain,
            udpPayloadOffset = dnsOffset
        )
    }

    fun readUShort(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    /** Legge il QNAME della prima domanda DNS, es. "www.example.com". */
    private fun parseDnsQuestionName(data: ByteArray, dnsOffset: Int, length: Int): String? {
        if (dnsOffset + DNS_HEADER_LENGTH > length) return null
        val qdCount = readUShort(data, dnsOffset + 4)
        if (qdCount < 1) return null

        var pos = dnsOffset + DNS_HEADER_LENGTH
        val labels = StringBuilder()
        while (pos < length) {
            val len = data[pos].toInt() and 0xFF
            if (len == 0) break
            if (len and 0xC0 == 0xC0) return null // compressione DNS: non prevista in una domanda iniziale valida
            if (pos + 1 + len > length) return null
            if (labels.isNotEmpty()) labels.append('.')
            labels.append(String(data, pos + 1, len, Charsets.US_ASCII))
            pos += 1 + len
        }
        return labels.toString().takeIf { it.isNotEmpty() }
    }
}
