package com.cybersentinel.netwatch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import androidx.core.app.NotificationCompat
import com.cybersentinel.netwatch.data.DomainLogEntry
import com.cybersentinel.netwatch.data.DomainLogStore
import com.cybersentinel.netwatch.net.DnsPacketParser
import com.cybersentinel.netwatch.net.IpPacketBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * VpnService "locale": non tunnellizza il traffico verso un server remoto,
 * instrada solo le interrogazioni DNS (porta 53) verso questa app per
 * registrare quale dominio ha chiesto di risolvere ciascuna app, poi le
 * inoltra a un resolver reale e scrive la risposta di ritorno nel TUN — le
 * app continuano a funzionare normalmente, solo le richieste DNS passano di
 * qui prima.
 *
 * Perché solo DNS e non tutto il traffico: instradare TUTTI i pacchetti
 * (0.0.0.0/0) richiederebbe reimplementare un proxy TCP/IP completo (stato
 * delle connessioni, riassemblaggio TCP...) per non rompere la connessione
 * a internet dell'utente. Instradando solo l'indirizzo DNS fittizio
 * configurato qui sotto, un bug in questo codice nel caso peggiore impedisce
 * la risoluzione di un nome — mai la connettività reale del telefono.
 */
class NetWatchVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var vpnInterface: ParcelFileDescriptor? = null
    private val writeLock = Any()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (vpnInterface == null) startVpn()
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        scope.cancel()
        runCatching { vpnInterface?.close() }
        vpnInterface = null
        super.onDestroy()
    }

    override fun onRevoke() {
        // L'utente ha revocato il consenso VPN da un'altra app o dalle Impostazioni.
        stopSelf()
    }

    private fun startVpn() {
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .addAddress(VPN_CLIENT_ADDRESS, 32)
            .addDnsServer(FAKE_DNS_ADDRESS)
            .addRoute(FAKE_DNS_ADDRESS, 32)

        vpnInterface = runCatching { builder.establish() }.getOrNull() ?: return stopSelf()
        isRunning = true
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
        scope.launch { readLoop() }
    }

    private fun readLoop() {
        val fd = vpnInterface ?: return
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buffer = ByteArray(MAX_PACKET_SIZE)

        while (scope.isActive) {
            val length = try {
                input.read(buffer)
            } catch (e: Exception) {
                break
            }
            if (length <= 0) continue

            val packetCopy = buffer.copyOf(length)
            scope.launch { handleDnsQuery(packetCopy, output) }
        }
    }

    private fun handleDnsQuery(packet: ByteArray, output: FileOutputStream) {
        val query = DnsPacketParser.parse(packet, packet.size) ?: return

        val (packageName, appLabel) = resolveApp(query)
        DomainLogStore.add(DomainLogEntry(packageName, appLabel, query.domain, System.currentTimeMillis()))

        val payload = packet.copyOfRange(query.udpPayloadOffset, packet.size)
        val response = forwardToRealDns(payload) ?: return
        val replyPacket = IpPacketBuilder.buildUdpReply(packet, response)
        synchronized(writeLock) {
            runCatching { output.write(replyPacket) }
        }
    }

    /** Inoltra la query a un resolver reale tramite un socket "protetto" (bypassa il tunnel: niente loop). */
    private fun forwardToRealDns(queryPayload: ByteArray): ByteArray? {
        val socket = DatagramSocket()
        return try {
            protect(socket)
            socket.soTimeout = DNS_TIMEOUT_MS
            val upstream = InetAddress.getByName(UPSTREAM_DNS)
            socket.send(DatagramPacket(queryPayload, queryPayload.size, upstream, 53))
            val responseBuffer = ByteArray(MAX_PACKET_SIZE)
            val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
            socket.receive(responsePacket)
            responseBuffer.copyOf(responsePacket.length)
        } catch (e: Exception) {
            null
        } finally {
            socket.close()
        }
    }

    /** Individua quale app ha fatto la richiesta tramite la tabella di connessione del sistema. */
    private fun resolveApp(query: com.cybersentinel.netwatch.net.DnsQueryPacket): Pair<String, String> {
        val uid = runCatching {
            val cm = getSystemService(ConnectivityManager::class.java)
            cm.getConnectionOwnerUid(
                OsConstants.IPPROTO_UDP,
                InetSocketAddress(InetAddress.getByAddress(query.sourceAddress), query.sourcePort),
                InetSocketAddress(InetAddress.getByAddress(query.destAddress), 53)
            )
        }.getOrDefault(-1)

        if (uid < 0) return "uid.sconosciuto" to getString(R.string.unknown_app)
        val packages = packageManager.getPackagesForUid(uid)
        val packageName = packages?.firstOrNull() ?: return "uid.$uid" to getString(R.string.uid_label, uid)
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageName to packageManager.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName to packageName
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_shield)
        .setContentTitle(getString(R.string.notification_title))
        .setContentText(getString(R.string.notification_text))
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .setOngoing(true)
        .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        const val ACTION_STOP = "com.cybersentinel.netwatch.STOP"

        @Volatile
        var isRunning: Boolean = false
            private set

        private const val CHANNEL_ID = "netwatch_status"
        private const val NOTIF_ID = 1
        private const val MAX_PACKET_SIZE = 32767
        private const val DNS_TIMEOUT_MS = 4000

        private const val VPN_CLIENT_ADDRESS = "10.111.222.1"
        private const val FAKE_DNS_ADDRESS = "10.111.222.2"

        /** Resolver pubblico usato per risolvere davvero le query intercettate. */
        private const val UPSTREAM_DNS = "1.1.1.1"
    }
}
