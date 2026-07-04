package com.cybersentinel.netwatch.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DnsPacketParserTest {

    private fun writeUShort(data: ByteArray, offset: Int, value: Int) {
        data[offset] = ((value shr 8) and 0xFF).toByte()
        data[offset + 1] = (value and 0xFF).toByte()
    }

    /** Costruisce un pacchetto IPv4/UDP con una domanda DNS valida per [domain]. */
    private fun buildDnsQueryPacket(domain: String, srcPort: Int = 54321): ByteArray {
        val labels = domain.split(".")
        val question = ByteArray(labels.sumOf { it.length + 1 } + 1 + 4)
        var pos = 0
        for (label in labels) {
            question[pos++] = label.length.toByte()
            for (c in label) question[pos++] = c.code.toByte()
        }
        question[pos++] = 0 // terminatore QNAME
        writeUShort(question, pos, 1); pos += 2 // QTYPE A
        writeUShort(question, pos, 1) // QCLASS IN

        val dnsHeader = ByteArray(12)
        dnsHeader[5] = 1 // QDCOUNT = 1
        val dnsMessage = dnsHeader + question

        val udpHeader = ByteArray(8)
        writeUShort(udpHeader, 0, srcPort)
        writeUShort(udpHeader, 2, 53)
        writeUShort(udpHeader, 4, 8 + dnsMessage.size)
        val udpDatagram = udpHeader + dnsMessage

        val ipHeader = ByteArray(20)
        ipHeader[0] = 0x45
        writeUShort(ipHeader, 2, 20 + udpDatagram.size)
        ipHeader[8] = 64
        ipHeader[9] = 17 // protocollo UDP
        ipHeader[12] = 1; ipHeader[13] = 2; ipHeader[14] = 3; ipHeader[15] = 4 // src
        ipHeader[16] = 8; ipHeader[17] = 8; ipHeader[18] = 8; ipHeader[19] = 8 // dst

        return ipHeader + udpDatagram
    }

    @Test
    fun `estrae il dominio da una query DNS valida`() {
        val packet = buildDnsQueryPacket("example.com")
        val result = DnsPacketParser.parse(packet, packet.size)
        assertEquals("example.com", result?.domain)
        assertEquals(54321, result?.sourcePort)
    }

    @Test
    fun `gestisce un dominio con più etichette`() {
        val packet = buildDnsQueryPacket("mail.google.com")
        assertEquals("mail.google.com", DnsPacketParser.parse(packet, packet.size)?.domain)
    }

    @Test
    fun `ignora pacchetti non IPv4`() {
        val packet = buildDnsQueryPacket("example.com")
        packet[0] = 0x60 // versione 6
        assertNull(DnsPacketParser.parse(packet, packet.size))
    }

    @Test
    fun `ignora pacchetti non diretti alla porta 53`() {
        val packet = buildDnsQueryPacket("example.com")
        writeUShort(packet, 22, 80) // porta di destinazione UDP (offset 20 + 2)
        assertNull(DnsPacketParser.parse(packet, packet.size))
    }

    @Test
    fun `ignora un pacchetto troppo corto`() {
        assertNull(DnsPacketParser.parse(ByteArray(10), 10))
    }
}
