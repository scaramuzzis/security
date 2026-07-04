package com.cybersentinel.netwatch.net

/**
 * Costruisce il pacchetto IPv4/UDP di risposta da scrivere nel TUN, invertendo
 * mittente/destinatario della richiesta originale — l'unico modo per far
 * arrivare la risposta DNS reale all'app che l'ha chiesta, dato che il TUN
 * lavora a livello di pacchetto grezzo, non di socket.
 */
object IpPacketBuilder {

    /** Checksum UDP a 0 = "non calcolato", esplicitamente valido su IPv4 (RFC 768): evita il calcolo dello pseudo-header. */
    private const val UDP_CHECKSUM_NOT_COMPUTED = 0

    fun buildUdpReply(originalPacket: ByteArray, udpPayload: ByteArray): ByteArray {
        val totalLength = 20 + 8 + udpPayload.size
        val packet = ByteArray(totalLength)

        packet[0] = 0x45 // versione 4, IHL 5 (20 byte, nessuna opzione)
        packet[1] = 0
        writeUShort(packet, 2, totalLength)
        packet[4] = 0; packet[5] = 0 // identification
        packet[6] = 0x40; packet[7] = 0 // flag "don't fragment"
        packet[8] = 64 // TTL
        packet[9] = 17 // protocollo UDP
        packet[10] = 0; packet[11] = 0 // checksum, scritto dopo

        // La risposta torna al mittente originale: indirizzi invertiti.
        System.arraycopy(originalPacket, 16, packet, 12, 4)
        System.arraycopy(originalPacket, 12, packet, 16, 4)
        writeUShort(packet, 10, checksum(packet, 0, 20))

        val ihl = (originalPacket[0].toInt() and 0x0F) * 4
        val udpOffset = 20
        writeUShort(packet, udpOffset, DnsPacketParser.readUShort(originalPacket, ihl + 2)) // porta sorgente = porta dest. originale
        writeUShort(packet, udpOffset + 2, DnsPacketParser.readUShort(originalPacket, ihl)) // porta dest. = porta sorgente originale
        writeUShort(packet, udpOffset + 4, 8 + udpPayload.size)
        writeUShort(packet, udpOffset + 6, UDP_CHECKSUM_NOT_COMPUTED)

        System.arraycopy(udpPayload, 0, packet, udpOffset + 8, udpPayload.size)
        return packet
    }

    private fun writeUShort(data: ByteArray, offset: Int, value: Int) {
        data[offset] = ((value shr 8) and 0xFF).toByte()
        data[offset + 1] = (value and 0xFF).toByte()
    }

    /** Checksum IPv4 standard: complemento a uno a 16 bit con riporto. */
    private fun checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        val end = offset + length
        while (i < end - 1) {
            sum += DnsPacketParser.readUShort(data, i)
            i += 2
        }
        if (i < end) sum += (data[i].toInt() and 0xFF) shl 8
        while (sum ushr 16 != 0) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum.inv() and 0xFFFF
    }
}
