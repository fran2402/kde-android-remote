package io.github.fran2402.kderemote.kdeconnect

import java.security.MessageDigest
import java.security.cert.Certificate

data class Device(
    val id: String,
    val name: String,
    val type: String,
    val paired: Boolean,
    val reachable: Boolean,
    val address: String?,
) {
    val connected: Boolean get() = paired && reachable
}

data class PairingState(
    val deviceId: String,
    val deviceName: String,
    val verificationKey: String,
    /** True when the desktop asked to pair with us, false when we are waiting on the desktop. */
    val incoming: Boolean,
)

data class VolumeState(
    /** 0f..1f where 1f is 100% on the desktop. */
    val level: Float,
    val muted: Boolean,
)

data class UiMessage(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

internal data class Sink(
    val name: String,
    val volume: Int,
    val maxVolume: Int,
    val muted: Boolean,
    val isDefault: Boolean,
) {
    /** PulseAudio's "100%"; maxVolume is usually higher to allow amplification. */
    val normalVolume: Int get() = if (maxVolume in 1..NORMAL_VOLUME) maxVolume else NORMAL_VOLUME

    companion object {
        const val NORMAL_VOLUME = 65536
    }
}

internal data class RunCommand(val key: String, val name: String, val command: String)

internal class DeviceData {
    var sinks: List<Sink> = emptyList()
    val players = LinkedHashMap<String, Boolean>()
    var commands: List<RunCommand>? = null

    val defaultSink: Sink? get() = sinks.firstOrNull { it.isDefault } ?: sinks.firstOrNull()
}

/**
 * The 8-character code both sides show while pairing (protocol v8): SHA-256 over both public keys,
 * larger one first, followed by the pairing timestamp.
 */
fun verificationKey(ours: Certificate, theirs: Certificate, timestamp: Long): String {
    var a = ours.publicKey.encoded
    var b = theirs.publicKey.encoded
    if (compareUnsigned(a, b) < 0) {
        val t = a
        a = b
        b = t
    }
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(a)
    digest.update(b)
    if (timestamp != 0L) digest.update(timestamp.toString().toByteArray())
    return digest.digest().joinToString("") { "%02x".format(it) }.take(8).uppercase()
}

private fun compareUnsigned(a: ByteArray, b: ByteArray): Int {
    for (i in 0 until minOf(a.size, b.size)) {
        val cmp = (a[i].toInt() and 0xff) - (b[i].toInt() and 0xff)
        if (cmp != 0) return cmp
    }
    return a.size - b.size
}
