package io.github.fran2402.kderemote.kdeconnect

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLSocket
import kotlin.math.roundToInt

/**
 * Speaks the KDE Connect LAN protocol: announces this phone over UDP, accepts the desktop's TCP
 * connection, upgrades it to TLS, handles pairing and sends remote-control packets.
 */
class KdeConnectManager(
    private val identityStore: KeyValueStore,
    private val trustStore: KeyValueStore,
    private val settings: KeyValueStore,
    private val deviceName: String,
    private val log: (String) -> Unit = {},
) {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> log("Unexpected error: $e") },
    )
    private val started = AtomicBoolean(false)
    private val identity: Deferred<LocalIdentity> =
        scope.async(start = CoroutineStart.LAZY) { LocalIdentity.loadOrCreate(identityStore, deviceName) }

    private val links = ConcurrentHashMap<String, LanLink>()
    private val names = ConcurrentHashMap<String, Pair<String, String>>()
    private val deviceData = ConcurrentHashMap<String, DeviceData>()
    @Volatile private var tcpPort = 0
    private var pairTimeout: Job? = null

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    private val _selectedId = MutableStateFlow(settings.get(KEY_SELECTED))
    val selectedDeviceId: StateFlow<String?> = _selectedId.asStateFlow()

    private val _pairing = MutableStateFlow<PairingState?>(null)
    val pairing: StateFlow<PairingState?> = _pairing.asStateFlow()

    private val _volume = MutableStateFlow<VolumeState?>(null)
    val volume: StateFlow<VolumeState?> = _volume.asStateFlow()

    private val _audioOutputs = MutableStateFlow<List<AudioOutput>>(emptyList())
    val audioOutputs: StateFlow<List<AudioOutput>> = _audioOutputs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        publishDevices()
        scope.launch {
            val id = identity.await()
            log("Local device ${id.deviceName} (${id.deviceId})")
            if (!startTcpServer()) return@launch
            startUdpListener()
            broadcastIdentity()
        }
    }

    /** Announce ourselves again; desktops that hear it connect back to us. */
    fun refresh() {
        if (!started.get()) return
        scope.launch { broadcastIdentity() }
    }

    fun addDeviceByAddress(host: String) {
        scope.launch {
            try {
                broadcastIdentity(InetAddress.getByName(host.trim()))
                message("Sent a hello to ${host.trim()}")
            } catch (e: IOException) {
                message("Couldn't reach ${host.trim()}")
            }
        }
    }

    fun selectDevice(id: String) {
        _selectedId.value = id
        settings.put(KEY_SELECTED, id)
        publishSelected()
    }

    // region Networking

    private fun startTcpServer(): Boolean {
        val server = (MIN_TCP_PORT..MAX_TCP_PORT).firstNotNullOfOrNull { port ->
            try {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(port))
                }
            } catch (_: IOException) {
                null
            }
        }
        if (server == null) {
            message("No free port for KDE Connect (1716–1764)")
            return false
        }
        tcpPort = server.localPort
        log("Listening on TCP $tcpPort")
        scope.launch {
            while (isActive) {
                val socket = try {
                    server.accept()
                } catch (_: IOException) {
                    break
                }
                scope.launch { handleIncomingConnection(socket) }
            }
        }
        return true
    }

    private fun startUdpListener() {
        scope.launch {
            val socket = try {
                DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(UDP_PORT))
                }
            } catch (e: IOException) {
                log("UDP listener unavailable: $e")
                return@launch
            }
            val buffer = ByteArray(16 * 1024)
            val myId = identity.await().deviceId
            while (isActive) {
                val datagram = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(datagram)
                } catch (_: IOException) {
                    break
                }
                val text = String(datagram.data, 0, datagram.length, Charsets.UTF_8)
                val packet = runCatching { NetworkPacket.parse(text) }.getOrNull() ?: continue
                if (packet.type != PacketType.IDENTITY) continue
                val remoteId = packet.body.optString("deviceId")
                if (remoteId == myId || links.containsKey(remoteId)) continue
                // A desktop announced itself: answer directly so it opens a connection to us.
                broadcastIdentity(datagram.address)
            }
        }
    }

    private suspend fun broadcastIdentity(target: InetAddress? = null) {
        val port = tcpPort
        if (port == 0) return
        val data = identity.await().identityPacket(port).serialize().toByteArray(Charsets.UTF_8)
        // One global broadcast only: several targets make the desktop open duplicate connections.
        val targets = listOf(target ?: InetAddress.getByName("255.255.255.255"))
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                for (address in targets) {
                    try {
                        socket.send(DatagramPacket(data, data.size, address, UDP_PORT))
                    } catch (e: IOException) {
                        log("UDP send to $address failed: $e")
                    }
                }
            }
        } catch (e: IOException) {
            log("UDP socket failed: $e")
        }
    }

    private suspend fun handleIncomingConnection(socket: Socket) {
        try {
            socket.soTimeout = HANDSHAKE_TIMEOUT_MS
            val firstLine = readRawLine(socket.getInputStream()) ?: throw IOException("No identity")
            val identityPacket = NetworkPacket.parse(firstLine)
            if (identityPacket.type != PacketType.IDENTITY) throw IOException("Expected identity, got ${identityPacket.type}")
            val remoteId = identityPacket.body.optString("deviceId")
            val me = identity.await()
            if (!LocalIdentity.isValidDeviceId(remoteId) || remoteId == me.deviceId) throw IOException("Bad device id")

            // The side that opened the TCP connection acts as the TLS server, so we are the TLS client.
            val ssl = me.sslContext.socketFactory.createSocket(
                socket, socket.inetAddress.hostAddress, socket.port, true,
            ) as SSLSocket
            ssl.useClientMode = true
            ssl.soTimeout = HANDSHAKE_TIMEOUT_MS
            ssl.startHandshake()
            val peerCertificate = ssl.session.peerCertificates.first() as X509Certificate
            val reader = BufferedReader(InputStreamReader(ssl.inputStream, Charsets.UTF_8))

            var remoteIdentity = identityPacket
            if (identityPacket.body.optInt("protocolVersion") >= 8) {
                // Protocol v8 repeats the identity exchange inside the encrypted channel.
                ssl.outputStream.write(me.identityPacket(tcpPort).serialize().toByteArray(Charsets.UTF_8))
                ssl.outputStream.flush()
                val line = reader.readLine() ?: throw IOException("Closed during identity exchange")
                val secured = NetworkPacket.parse(line)
                if (secured.type != PacketType.IDENTITY || secured.body.optString("deviceId") != remoteId) {
                    throw IOException("Identity mismatch after TLS")
                }
                remoteIdentity = secured
            }
            ssl.soTimeout = 0
            onLinkEstablished(remoteIdentity, ssl, reader, peerCertificate)
        } catch (e: Exception) {
            log("Connection from ${socket.inetAddress} failed: $e")
            runCatching { socket.close() }
        }
    }

    private fun readRawLine(input: InputStream): String? {
        val bytes = java.io.ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return null
            if (b == '\n'.code) break
            bytes.write(b)
            if (bytes.size() > 64 * 1024) throw IOException("Identity too long")
        }
        return bytes.toString("UTF-8")
    }

    private fun onLinkEstablished(identityPacket: NetworkPacket, ssl: SSLSocket, reader: BufferedReader, cert: X509Certificate) {
        val body = identityPacket.body
        val id = body.getString("deviceId")
        val name = body.optString("deviceName", "Unknown device")
        val type = body.optString("deviceType", "desktop")
        names[id] = name to type

        val trusted = trustedCertificate(id)
        if (trusted != null && !trusted.contentEquals(cert.encoded)) {
            log("Certificate for $name changed; forgetting the pairing")
            trustStore.remove(id)
        } else if (trusted != null) {
            saveTrusted(id, name, type, cert)
        }

        val link = LanLink(id, cert, ssl, reader, scope, ::handlePacket, ::onLinkClosed)
        links.put(id, link)?.close()
        link.start()
        log("Connected to $name (${link.address})")

        if (_selectedId.value == null || (isPaired(id) && !isPaired(_selectedId.value!!))) {
            selectDevice(id)
        }
        publishDevices()
        if (isPaired(id)) requestDeviceState(id)
    }

    private fun onLinkClosed(link: LanLink) {
        if (links.remove(link.deviceId, link)) {
            log("Disconnected from ${link.deviceId}")
            if (_pairing.value?.deviceId == link.deviceId) _pairing.value = null
            publishDevices()
            // Wi-Fi blips and replaced connections are common; ask the desktop to reconnect.
            scope.launch {
                delay(RECONNECT_DELAY_MS)
                if (!links.containsKey(link.deviceId)) broadcastIdentity()
            }
        }
    }

    // endregion

    // region Packets

    private fun handlePacket(link: LanLink, packet: NetworkPacket) {
        val id = link.deviceId
        log("Received ${packet.toString().take(300)}")
        if (packet.type == PacketType.PAIR) {
            handlePair(link, packet.body)
            return
        }
        if (!isPaired(id)) {
            log("Ignoring ${packet.type} from unpaired $id")
            return
        }
        val data = deviceData.getOrPut(id) { DeviceData() }
        when (packet.type) {
            PacketType.SYSTEMVOLUME -> handleVolume(data, packet.body)
            PacketType.MPRIS -> handleMpris(link, data, packet.body)
            PacketType.RUNCOMMAND -> handleCommands(data, packet.body)
        }
        if (id == _selectedId.value) publishSelected()
    }

    private fun handleVolume(data: DeviceData, body: JSONObject) {
        val list = body.optJSONArray("sinkList")
        if (list != null) {
            data.sinks = (0 until list.length()).mapNotNull { i ->
                val s = list.optJSONObject(i) ?: return@mapNotNull null
                Sink(
                    name = s.optString("name"),
                    description = s.optString("description").ifEmpty { s.optString("name") },
                    volume = s.optInt("volume"),
                    maxVolume = s.optInt("maxVolume", Sink.NORMAL_VOLUME),
                    muted = s.optBoolean("muted"),
                    isDefault = s.optBoolean("enabled", false),
                )
            }
            return
        }
        val name = body.optString("name").ifEmpty { return }
        val becameDefault = body.optBoolean("enabled", false)
        data.sinks = data.sinks.map { sink ->
            if (sink.name != name) {
                // Only one output can be the default.
                if (becameDefault) sink.copy(isDefault = false) else sink
            } else {
                sink.copy(
                    volume = if (body.has("volume")) body.optInt("volume") else sink.volume,
                    muted = if (body.has("muted")) body.optBoolean("muted") else sink.muted,
                    isDefault = if (body.has("enabled")) body.optBoolean("enabled") else sink.isDefault,
                )
            }
        }
    }

    private fun handleMpris(link: LanLink, data: DeviceData, body: JSONObject) {
        body.optJSONArray("playerList")?.let { list ->
            val names = (0 until list.length()).map { list.optString(it) }.filter { it.isNotEmpty() }
            synchronized(data.players) {
                data.players.keys.retainAll(names.toSet())
                for (player in names) {
                    if (player !in data.players) {
                        data.players[player] = false
                        link.send(NetworkPacket(PacketType.MPRIS_REQUEST, JSONObject().put("player", player).put("requestNowPlaying", true)))
                    }
                }
            }
        }
        val player = body.optString("player")
        if (player.isNotEmpty() && body.has("isPlaying")) {
            synchronized(data.players) { data.players[player] = body.optBoolean("isPlaying") }
        }
    }

    private fun handleCommands(data: DeviceData, body: JSONObject) {
        val raw = body.opt("commandList") ?: return
        val json = when (raw) {
            is JSONObject -> raw
            is String -> runCatching { JSONObject(raw) }.getOrNull() ?: return
            else -> return
        }
        data.commands = json.keys().asSequence().mapNotNull { key ->
            val cmd = json.optJSONObject(key) ?: return@mapNotNull null
            RunCommand(key, cmd.optString("name"), cmd.optString("command"))
        }.toList()
    }

    private fun handlePair(link: LanLink, body: JSONObject) {
        val id = link.deviceId
        val name = names[id]?.first ?: id
        if (body.optBoolean("pair")) {
            val pending = _pairing.value
            when {
                isPaired(id) -> link.send(pairPacket(true))
                pending != null && pending.deviceId == id && !pending.incoming -> {
                    completePairing(link)
                    message("Paired with $name")
                }
                else -> scope.launch {
                    val key = verificationKey(identity.await().certificate, link.peerCertificate, body.optLong("timestamp", 0L))
                    _pairing.value = PairingState(id, name, key, incoming = true)
                }
            }
        } else {
            val pending = _pairing.value
            val wasPaired = isPaired(id)
            trustStore.remove(id)
            deviceData.remove(id)
            if (pending?.deviceId == id) {
                _pairing.value = null
                pairTimeout?.cancel()
                if (!pending.incoming) message("$name declined pairing")
            } else if (wasPaired) {
                message("$name unpaired this phone")
            }
            publishDevices()
            publishSelected()
        }
    }

    private fun completePairing(link: LanLink) {
        val id = link.deviceId
        val (name, type) = names[id] ?: (id to "desktop")
        pairTimeout?.cancel()
        saveTrusted(id, name, type, link.peerCertificate)
        _pairing.value = null
        selectDevice(id)
        publishDevices()
        requestDeviceState(id)
    }

    private fun requestDeviceState(id: String) {
        val link = links[id] ?: return
        link.send(NetworkPacket(PacketType.SYSTEMVOLUME_REQUEST, JSONObject().put("requestSinks", true)))
        link.send(NetworkPacket(PacketType.MPRIS_REQUEST, JSONObject().put("requestPlayerList", true)))
        link.send(NetworkPacket(PacketType.RUNCOMMAND_REQUEST, JSONObject().put("requestCommandList", true)))
    }

    private fun pairPacket(pair: Boolean, timestamp: Long? = null): NetworkPacket {
        val body = JSONObject().put("pair", pair)
        if (timestamp != null) body.put("timestamp", timestamp)
        return NetworkPacket(PacketType.PAIR, body)
    }

    // endregion

    // region Pairing API

    fun requestPairing(id: String) {
        val link = links[id]
        if (link == null) {
            message("${names[id]?.first ?: "Device"} isn't reachable")
            return
        }
        scope.launch {
            val timestamp = System.currentTimeMillis() / 1000
            val key = verificationKey(identity.await().certificate, link.peerCertificate, timestamp)
            _pairing.value = PairingState(id, names[id]?.first ?: id, key, incoming = false)
            link.send(pairPacket(true, timestamp))
            pairTimeout?.cancel()
            pairTimeout = scope.launch {
                delay(PAIR_TIMEOUT_MS)
                val pending = _pairing.value
                if (pending?.deviceId == id && !pending.incoming) {
                    _pairing.value = null
                    link.send(pairPacket(false))
                    message("Pairing timed out")
                }
            }
        }
    }

    fun acceptPairing() {
        val pending = _pairing.value ?: return
        val link = links[pending.deviceId] ?: run {
            _pairing.value = null
            return
        }
        link.send(pairPacket(true))
        completePairing(link)
    }

    /** Rejects an incoming request or cancels our own. */
    fun cancelPairing() {
        val pending = _pairing.value ?: return
        pairTimeout?.cancel()
        _pairing.value = null
        links[pending.deviceId]?.send(pairPacket(false))
    }

    fun unpair(id: String) {
        links[id]?.send(pairPacket(false))
        trustStore.remove(id)
        deviceData.remove(id)
        publishDevices()
        publishSelected()
    }

    // endregion

    // region Remote control API

    fun sendSpecialKey(code: Int, modifiers: KeyModifiers = KeyModifiers.NONE) =
        sendInput(JSONObject().put("specialKey", code).withModifiers(modifiers))

    fun sendKey(key: String, modifiers: KeyModifiers = KeyModifiers.NONE) =
        sendInput(JSONObject().put("key", key).withModifiers(modifiers))

    private fun JSONObject.withModifiers(modifiers: KeyModifiers): JSONObject {
        if (modifiers.ctrl) put("ctrl", true)
        if (modifiers.alt) put("alt", true)
        if (modifiers.shift) put("shift", true)
        if (modifiers.superKey) put("super", true)
        return this
    }

    fun click() = sendInput(JSONObject().put("singleclick", true))

    fun rightClick() = sendInput(JSONObject().put("rightclick", true))

    private val motionLock = Any()
    private var pendingDx = 0f
    private var pendingDy = 0f
    private var pendingScroll = 0f
    private var motionFlushScheduled = false

    fun moveCursor(dx: Float, dy: Float) = synchronized(motionLock) {
        pendingDx += dx
        pendingDy += dy
        scheduleMotionFlush()
    }

    fun scroll(dy: Float) = synchronized(motionLock) {
        pendingScroll += dy
        scheduleMotionFlush()
    }

    private fun scheduleMotionFlush() {
        if (motionFlushScheduled) return
        motionFlushScheduled = true
        scope.launch {
            delay(MOTION_INTERVAL_MS)
            val (dx, dy, scroll) = synchronized(motionLock) {
                motionFlushScheduled = false
                Triple(pendingDx, pendingDy, pendingScroll).also {
                    pendingDx = 0f
                    pendingDy = 0f
                    pendingScroll = 0f
                }
            }
            val link = selectedLink() ?: return@launch
            if (dx != 0f || dy != 0f) {
                link.send(NetworkPacket(PacketType.MOUSEPAD_REQUEST, JSONObject().put("dx", dx.toDouble()).put("dy", dy.toDouble())))
            }
            if (scroll != 0f) {
                link.send(
                    NetworkPacket(
                        PacketType.MOUSEPAD_REQUEST,
                        JSONObject().put("scroll", true).put("dx", 0.0).put("dy", scroll.toDouble()),
                    ),
                )
            }
        }
    }

    fun playPause() {
        val (link, data) = selectedLinkAndData() ?: return
        val player = synchronized(data.players) {
            data.players.entries.firstOrNull { it.value }?.key ?: data.players.keys.firstOrNull()
        }
        if (player == null) {
            message("No media player is running on the PC")
            link.send(NetworkPacket(PacketType.MPRIS_REQUEST, JSONObject().put("requestPlayerList", true)))
            return
        }
        link.send(NetworkPacket(PacketType.MPRIS_REQUEST, JSONObject().put("player", player).put("action", "PlayPause")))
        synchronized(data.players) { data.players[player] = !(data.players[player] ?: false) }
        publishSelected()
    }

    private val volumeLock = Any()
    private var pendingVolume: Float? = null
    private var volumeJob: Job? = null

    fun setVolume(level: Float) {
        val (link, data) = selectedLinkAndData(quiet = true) ?: return
        val sink = data.defaultSink ?: return
        val value = (level.coerceIn(0f, 1f) * sink.normalVolume).roundToInt()
        data.sinks = data.sinks.map { if (it.name == sink.name) it.copy(volume = value) else it }
        publishSelected()
        synchronized(volumeLock) {
            pendingVolume = level
            if (volumeJob?.isActive == true) return
            volumeJob = scope.launch {
                while (true) {
                    val next = synchronized(volumeLock) { pendingVolume.also { pendingVolume = null } } ?: break
                    val volume = (next.coerceIn(0f, 1f) * sink.normalVolume).roundToInt()
                    link.send(NetworkPacket(PacketType.SYSTEMVOLUME_REQUEST, JSONObject().put("name", sink.name).put("volume", volume)))
                    delay(VOLUME_INTERVAL_MS)
                }
            }
        }
    }

    fun toggleMute() {
        val (link, data) = selectedLinkAndData() ?: return
        val sink = data.defaultSink ?: run {
            message("No audio output reported by the PC")
            return
        }
        val muted = !sink.muted
        link.send(NetworkPacket(PacketType.SYSTEMVOLUME_REQUEST, JSONObject().put("name", sink.name).put("muted", muted)))
        data.sinks = data.sinks.map { if (it.name == sink.name) it.copy(muted = muted) else it }
        publishSelected()
    }

    /** Makes [name] the PC's default audio output. */
    fun setAudioOutput(name: String) {
        val (link, data) = selectedLinkAndData() ?: return
        link.send(NetworkPacket(PacketType.SYSTEMVOLUME_REQUEST, JSONObject().put("name", name).put("enabled", true)))
        data.sinks = data.sinks.map { it.copy(isDefault = it.name == name) }
        publishSelected()
    }

    /** Runs the desktop's "suspend" Run Command, which the user adds once in KDE Connect settings. */
    fun suspendComputer() {
        val (link, data) = selectedLinkAndData() ?: return
        val commands = data.commands
        val suspend = commands?.firstOrNull {
            it.name.contains("suspend", ignoreCase = true) || it.command.contains("suspend", ignoreCase = true)
        }
        if (suspend != null) {
            link.send(NetworkPacket(PacketType.RUNCOMMAND_REQUEST, JSONObject().put("key", suspend.key)))
            message("Suspending ${names[link.deviceId]?.first ?: "PC"}")
            return
        }
        link.send(NetworkPacket(PacketType.RUNCOMMAND_REQUEST, JSONObject().put("requestCommandList", true)))
        message(
            UiMessage(
                "Add a Run Command named \"Suspend\" (systemctl suspend) in KDE Connect on the PC",
                actionLabel = "Set up",
                action = { link.send(NetworkPacket(PacketType.RUNCOMMAND_REQUEST, JSONObject().put("setup", true))) },
            ),
        )
    }

    private fun sendInput(body: JSONObject) {
        selectedLink(quiet = false)?.send(NetworkPacket(PacketType.MOUSEPAD_REQUEST, body))
    }

    private fun selectedLink(quiet: Boolean = true): LanLink? {
        val id = _selectedId.value
        val link = id?.let { links[it] }
        if (link == null || !isPaired(link.deviceId)) {
            if (!quiet) message(if (id == null) "Choose a PC first" else "${names[id]?.first ?: "PC"} isn't connected")
            return null
        }
        return link
    }

    private fun selectedLinkAndData(quiet: Boolean = false): Pair<LanLink, DeviceData>? {
        val link = selectedLink(quiet) ?: return null
        return link to deviceData.getOrPut(link.deviceId) { DeviceData() }
    }

    // endregion

    // region State

    private fun isPaired(id: String) = trustStore.get(id) != null

    private fun trustedCertificate(id: String): ByteArray? {
        val json = trustStore.get(id) ?: return null
        return runCatching { Base64.getDecoder().decode(JSONObject(json).getString("certificate")) }.getOrNull()
    }

    private fun saveTrusted(id: String, name: String, type: String, cert: X509Certificate) {
        trustStore.put(
            id,
            JSONObject()
                .put("name", name)
                .put("type", type)
                .put("certificate", Base64.getEncoder().encodeToString(cert.encoded))
                .toString(),
        )
    }

    private fun publishDevices() {
        val ids = trustStore.keys() + links.keys
        _devices.value = ids.map { id ->
            val stored = trustStore.get(id)?.let { runCatching { JSONObject(it) }.getOrNull() }
            val (name, type) = names[id] ?: ((stored?.optString("name") ?: id) to (stored?.optString("type") ?: "desktop"))
            Device(id, name, type, paired = stored != null, reachable = links.containsKey(id), address = links[id]?.address)
        }.sortedWith(compareByDescending<Device> { it.paired }.thenByDescending { it.reachable }.thenBy { it.name.lowercase() })
        publishSelected()
    }

    private fun publishSelected() {
        val data = _selectedId.value?.let { deviceData[it] }
        val sink = data?.defaultSink
        _volume.value = sink?.let { VolumeState(it.volume.toFloat() / it.normalVolume, it.muted) }
        _audioOutputs.value = data?.sinks.orEmpty().map { AudioOutput(it.name, it.description, it.isDefault) }
        _isPlaying.value = data?.let { synchronized(it.players) { it.players.values.any { playing -> playing } } } ?: false
    }

    private fun message(text: String) = message(UiMessage(text))

    private fun message(msg: UiMessage) {
        log("Message: ${msg.text}")
        _messages.tryEmit(msg)
    }

    // endregion

    companion object {
        const val UDP_PORT = 1716
        const val MIN_TCP_PORT = 1716
        const val MAX_TCP_PORT = 1764
        private const val KEY_SELECTED = "selectedDevice"
        private const val HANDSHAKE_TIMEOUT_MS = 10_000
        private const val PAIR_TIMEOUT_MS = 30_000L
        private const val MOTION_INTERVAL_MS = 16L
        private const val VOLUME_INTERVAL_MS = 60L
        private const val RECONNECT_DELAY_MS = 2_000L
    }
}
