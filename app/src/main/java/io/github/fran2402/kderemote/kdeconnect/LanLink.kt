package io.github.fran2402.kderemote.kdeconnect

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.IOException
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLSocket

/** An established, encrypted connection to one desktop. */
class LanLink(
    val deviceId: String,
    val peerCertificate: X509Certificate,
    private val socket: SSLSocket,
    private val reader: BufferedReader,
    private val scope: CoroutineScope,
    private val onPacket: (LanLink, NetworkPacket) -> Unit,
    private val onClosed: (LanLink) -> Unit,
) {
    private val outgoing = Channel<NetworkPacket>(Channel.UNLIMITED)
    private val closed = AtomicBoolean(false)

    val address: String? get() = socket.inetAddress?.hostAddress

    fun start() {
        scope.launch {
            try {
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    val packet = runCatching { NetworkPacket.parse(line) }.getOrNull() ?: continue
                    onPacket(this@LanLink, packet)
                }
            } catch (_: IOException) {
            } finally {
                close()
            }
        }
        scope.launch {
            try {
                val out = socket.outputStream
                for (packet in outgoing) {
                    out.write(packet.serialize().toByteArray(Charsets.UTF_8))
                    out.flush()
                }
            } catch (_: IOException) {
                close()
            }
        }
    }

    fun send(packet: NetworkPacket) {
        outgoing.trySend(packet)
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        outgoing.close()
        runCatching { socket.close() }
        onClosed(this)
    }
}
