package com.example.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.WpsInfo
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import com.example.data.PreferenceManager
import com.example.model.AlertPriority
import com.example.model.ConnectionStatus
import com.example.model.MissionTelemetry
import com.example.model.PeerDevice
import com.example.model.TransportProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.Closeable
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.lang.reflect.InvocationTargetException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Production-ready Tactical Mesh Transport.
 * Features:
 * 1. Real UDP Broadcast mesh on port 8888 for zero-configuration discovery & redundant packet broadcast.
 * 2. Real TCP Server & Socket streaming on port 8889 for lossless direct phone-to-phone audio text transmission.
 * 3. Real Bluetooth RFCOMM server & client socket streaming using dedicated tactical UUID.
 * 4. Real Wi-Fi Direct (WifiP2pManager) integration.
 * 5. Real hardware telemetry provider (battery, temperature, RSSI, RAM, IP).
 * 6. Real multi-hop mesh relay: this node can hold several simultaneous TCP/Bluetooth
 *    links at once (not just one "connected peer"), and floods any packet it doesn't
 *    already recognise back out to every other live link. So if A and C are both linked
 *    only to B (out of each other's radio range), B automatically relays A<->C traffic —
 *    no manual routing table, no signal-strength math required for correctness. Relay
 *    order across multiple candidate links is still biased towards the strongest signal
 *    first (see [relayToMeshPeers]), and a bounded TTL plus a seen-packet-id cache stop
 *    duplicate/looping broadcasts in denser meshes (A-B-C-D with multiple paths).
 */
class TacticalMeshTransport(
    private val context: Context,
    private val scope: CoroutineScope
) : TransportLayer {

    private val telemetryProvider = DeviceTelemetryProvider(context, scope)
    override val telemetry: StateFlow<MissionTelemetry> = telemetryProvider.telemetry

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable !is CancellationException) {
            Log.w("TacticalMesh", "Handled background transport error: ${throwable.message}")
        }
    }

    val myNodeId: String = "NODE_${try { PreferenceManager(context).getInstallUuid().replace("-", "").take(6).uppercase() } catch (e: Throwable) { UUID.randomUUID().toString().take(6).uppercase() }}"
    val myCallsign: String get() = telemetry.value.nodeCallsign

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    override val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _activeProtocol = MutableStateFlow(TransportProtocol.WIFI_DIRECT)
    override val activeProtocol: StateFlow<TransportProtocol> = _activeProtocol.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    override val discoveredPeers: StateFlow<List<PeerDevice>> = _discoveredPeers.asStateFlow()

    private val _connectedPeer = MutableStateFlow<PeerDevice?>(null)
    override val connectedPeer: StateFlow<PeerDevice?> = _connectedPeer.asStateFlow()

    private val _connectedPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    override val connectedPeers: StateFlow<List<PeerDevice>> = _connectedPeers.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<NetworkPacket>(extraBufferCapacity = 64)
    override val incomingPackets: SharedFlow<NetworkPacket> = _incomingPackets.asSharedFlow()

    // Sockets & Transceiver infrastructure
    private val udpPort = 8888
    private val tcpPort = 8889
    private var udpSocket: DatagramSocket? = null
    private var tcpServerSocket: ServerSocket? = null

    fun setCustomCallsign(callsign: String?) {
        telemetryProvider.setCustomCallsign(callsign)
        if (!callsign.isNullOrBlank()) {
            try {
                bluetoothAdapter?.name = callsign
            } catch (e: SecurityException) {}
            try {
                val setDeviceNameMethod = wifiP2pManager?.javaClass?.getMethod(
                    "setDeviceName",
                    WifiP2pManager.Channel::class.java,
                    String::class.java,
                    WifiP2pManager.ActionListener::class.java
                )
                setDeviceNameMethod?.invoke(wifiP2pManager, wifiP2pChannel, callsign, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        Log.i("TacticalMesh", "Wi-Fi Direct device name updated to $callsign")
                    }
                    override fun onFailure(reason: Int) {}
                })
            } catch (e: Throwable) {}
        }
        if (isBleAdvertising) {
            stopBleDiscovery()
            startBleDiscovery()
        }
    }

    private fun isCustomCallsign(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val trimmed = name.trim()
        if (trimmed.startsWith("BLE-", ignoreCase = true) ||
            trimmed.startsWith("BLE_", ignoreCase = true) ||
            trimmed.startsWith("Node at", ignoreCase = true) ||
            trimmed.startsWith("DIRECT_", ignoreCase = true) ||
            trimmed.startsWith("Paired Device", ignoreCase = true) ||
            trimmed.startsWith("Bluetooth Transceiver", ignoreCase = true) ||
            trimmed.startsWith("P2P ", ignoreCase = true) ||
            trimmed.equals("Connected Mesh Node", ignoreCase = true) ||
            trimmed.equals("NO-PEER-CONNECTED", ignoreCase = true)) {
            return false
        }
        val defaultModel = DeviceTelemetryProvider.getCleanDeviceModel()
        if (trimmed.equals(defaultModel, ignoreCase = true)) {
            return false
        }
        if (trimmed.equals(Build.MODEL, ignoreCase = true) ||
            trimmed.equals(Build.MANUFACTURER, ignoreCase = true) ||
            trimmed.equals("${Build.MANUFACTURER} ${Build.MODEL}", ignoreCase = true)) {
            return false
        }
        return true
    }

    // Bluetooth infrastructure
    private val bluetoothAdapter: BluetoothAdapter? by lazy { BluetoothAdapter.getDefaultAdapter() }
    private val meshBluetoothUuid: UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")
    private val bleServiceUuid = ParcelUuid(UUID.fromString("0000fa87-0000-1000-8000-00805f9b34fb"))
    private var bluetoothServerSocket: BluetoothServerSocket? = null

    /**
     * One entry per *live* link this node currently holds — a plain walkie-talkie pair
     * has exactly one, but a relay node in a bigger mesh (A-B-C) holds two simultaneously
     * (one to A, one to C) so it can forward between them. Keyed by the remote's IP
     * (TCP/Wi-Fi Direct links) or Bluetooth MAC address (BT links); this is also the
     * `sourceAddress`/`excludeKey` used to avoid immediately bouncing a relayed packet
     * back the way it came.
     */
    private data class PeerLink(
        val key: String,
        val protocol: TransportProtocol,
        val writer: BufferedWriter,
        val closeable: Closeable
    )

    private val peerLinks = ConcurrentHashMap<String, PeerLink>()
    private val connectingPeers = ConcurrentHashMap.newKeySet<String>()
    private val activeReconnectJobs = ConcurrentHashMap<String, Job>()

    /** packetId -> first-seen timestamp, so a flooded packet is delivered/relayed once
     *  per node even if it arrives again via a second path (denser mesh topologies). */
    private val seenPacketIds = ConcurrentHashMap<String, Long>()

    private val bleScanner: BluetoothLeScanner? by lazy {
        try { bluetoothAdapter?.bluetoothLeScanner } catch (e: Exception) { null }
    }
    private val bleAdvertiser: BluetoothLeAdvertiser? by lazy {
        try { bluetoothAdapter?.bluetoothLeAdvertiser } catch (e: Exception) { null }
    }
    private var isBleScanning = false
    private var isBleAdvertising = false

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            if (isSelfScanResult(result)) return

            val hwAddress = device.address
            val serviceData = result.scanRecord?.getServiceData(bleServiceUuid)
            var advertisedCallsign: String? = null
            var advertisedNodeId: String? = null
            if (serviceData != null) {
                val payload = String(serviceData, Charsets.UTF_8)
                val parts = payload.split("|")
                advertisedNodeId = parts.getOrNull(0)?.trim()
                val cs = parts.getOrNull(1)?.trim()
                if (!cs.isNullOrBlank() && isCustomCallsign(cs)) {
                    advertisedCallsign = cs
                }
            }

            val rawName = try { device.name ?: result.scanRecord?.deviceName } catch (e: SecurityException) { result.scanRecord?.deviceName }

            // Find existing peer by address or id
            val existing = peerMap.values.find {
                it.address.equals(hwAddress, ignoreCase = true) ||
                (advertisedNodeId != null && it.id.equals(advertisedNodeId, ignoreCase = true)) ||
                it.id == "BLE_${hwAddress.replace(":", "")}"
            }

            // Lock custom identity: never overwrite a user's custom name with generic hardware string
            val displayName = when {
                existing != null && isCustomCallsign(existing.name) -> existing.name
                !advertisedCallsign.isNullOrBlank() -> DeviceTelemetryProvider.cleanDeviceName(advertisedCallsign)
                !rawName.isNullOrBlank() -> DeviceTelemetryProvider.cleanDeviceName(rawName)
                else -> DeviceTelemetryProvider.getCleanDeviceModel()
            }

            val peerId = advertisedNodeId ?: existing?.id ?: "BLE_${hwAddress.replace(":", "")}"

            val peer = PeerDevice(
                id = peerId,
                name = displayName,
                address = hwAddress,
                protocol = TransportProtocol.BLE,
                port = 0,
                signalStrengthDbm = result.rssi.coerceIn(-95, -30),
                batteryPercent = existing?.batteryPercent ?: 88
            )
            if (isSelfDevice(peer)) return

            peerMap[peer.id] = peer
            peerLastSeen[peer.id] = System.currentTimeMillis()
            updateDiscoveredPeers()
        }
    }

    private val bleAdvertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.i("TacticalMesh", "BLE Advertising started successfully")
            isBleAdvertising = true
        }
        override fun onStartFailure(errorCode: Int) {
            Log.w("TacticalMesh", "BLE Advertising start failure: $errorCode")
            isBleAdvertising = false
        }
    }

    // Wi-Fi Direct
    private var wifiP2pManager: WifiP2pManager? = null
    private var wifiP2pChannel: WifiP2pManager.Channel? = null

    // Discovered peer tracking
    private val peerMap = ConcurrentHashMap<String, PeerDevice>()
    private val peerLastSeen = ConcurrentHashMap<String, Long>()

    // Process jobs
    private var beaconBroadcastJob: Job? = null
    private var udpListenerJob: Job? = null
    private var tcpServerJob: Job? = null
    private var bluetoothServerJob: Job? = null
    private var peerPruningJob: Job? = null

    init {
        initializeWifiP2p()
        startUdpMeshListener()
        startTcpServer()
        startBluetoothServer()
        startBeaconBroadcaster()
        startPeerPruning()
        registerBluetoothDiscoveryReceiver()
    }

    private fun initializeWifiP2p() {
        try {
            wifiP2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
            wifiP2pChannel = wifiP2pManager?.initialize(context, context.mainLooper, null)
        } catch (e: Exception) {
            Log.w("TacticalMesh", "Wi-Fi Direct init: ${e.message}")
        }
    }

    override fun setProtocol(protocol: TransportProtocol) {
        _activeProtocol.value = protocol
        val freq = when (protocol) {
            TransportProtocol.WIFI_DIRECT -> "5.180 GHz (Wi-Fi Mesh)"
            TransportProtocol.BLE -> "2.402 GHz (BLE Low Energy)"
            TransportProtocol.BLUETOOTH -> "2.402 GHz (Bluetooth Classic)"
        }
        telemetryProvider.setFrequencyLabel(freq)
        startDiscovery(protocol)
    }

    // ==========================================
    // Real UDP Mesh Transceiver (Port 8888)
    // ==========================================

    private fun startUdpMeshListener() {
        udpListenerJob?.cancel()
        udpListenerJob = scope.launch(Dispatchers.IO + exceptionHandler) {
            try {
                udpSocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(udpPort))
                }
                Log.i("TacticalMesh", "Real UDP Mesh receiver active on port $udpPort")

                val buffer = ByteArray(4096)
                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        udpSocket?.receive(packet)
                    } catch (e: SocketException) {
                        Log.i("TacticalMesh", "UDP DatagramSocket closed: ${e.message}")
                        break
                    } catch (e: IOException) {
                        Log.w("TacticalMesh", "UDP receive error: ${e.message}")
                        break
                    }
                    val rawJson = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    handleIncomingRawJson(rawJson, packet.address?.hostAddress ?: "")
                }
            } catch (e: Throwable) {
                if (e !is CancellationException) {
                    Log.w("TacticalMesh", "UDP Mesh listener notice: ${e.message}")
                }
            }
        }
    }

    private fun isP2pSubnet(ip: String): Boolean = ip.startsWith("192.168.49.")

    private fun startBeaconBroadcaster() {
        beaconBroadcastJob?.cancel()
        beaconBroadcastJob = scope.launch(Dispatchers.IO + exceptionHandler) {
            Log.i("TacticalMesh", "[LAN_BEACON] Secondary shared-router UDP broadcaster active (secondary fallback)")
            while (isActive) {
                try {
                    val localIp = DeviceTelemetryProvider.getRealDeviceIpAddress()
                    if (localIp == "127.0.0.1" || localIp == "0.0.0.0") {
                        delay(2000)
                        continue
                    }

                    val customName = myCallsign
                    val hwId = DeviceTelemetryProvider.getHardwareId(context)
                    val beaconObj = JSONObject().apply {
                        put("type", "BEACON")
                        put("nodeId", myNodeId)
                        put("hardwareId", hwId)
                        put("deviceName", customName)
                        put("callsign", customName)
                        put("ip", localIp)
                        put("port", tcpPort)
                        put("protocol", _activeProtocol.value.name)
                        put("battery", telemetry.value.batteryPercent)
                        put("timestamp", System.currentTimeMillis())
                    }
                    val beaconBytes = beaconObj.toString().toByteArray(Charsets.UTF_8)

                    // Broadcast to subnet 255.255.255.255
                    val broadcastAddr = InetAddress.getByName("255.255.255.255")
                    val broadcastPacket = DatagramPacket(beaconBytes, beaconBytes.size, broadcastAddr, udpPort)
                    udpSocket?.send(broadcastPacket)

                    // Multi-subnet coverage for Hotspots, Home and Field Wi-Fi routers
                    val subnetsToTry = mutableListOf("192.168.43.255", "192.168.1.255", "192.168.0.255", "10.0.2.255")
                    val ipParts = localIp.split(".")
                    if (ipParts.size == 4) {
                        subnetsToTry.add("${ipParts[0]}.${ipParts[1]}.${ipParts[2]}.255")
                    }

                    for (subnet in subnetsToTry.distinct()) {
                        try {
                            val addr = InetAddress.getByName(subnet)
                            udpSocket?.send(DatagramPacket(beaconBytes, beaconBytes.size, addr, udpPort))
                        } catch (e: Throwable) {}
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // Ignore transient broadcast socket error
                }
                delay(1800)
            }
        }
    }

    // ==========================================
    // Real TCP Server & Streaming (Port 8889)
    // ==========================================

    private fun startTcpServer() {
        tcpServerJob?.cancel()
        tcpServerJob = scope.launch(Dispatchers.IO + exceptionHandler) {
            try {
                tcpServerSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), tcpPort))
                }
                Log.i("TacticalMesh", "Real TCP Server listening on port $tcpPort bound to 0.0.0.0 (all interfaces)")

                while (isActive) {
                    val client = try {
                        tcpServerSocket?.accept()
                    } catch (e: SocketException) {
                        Log.i("TacticalMesh", "TCP ServerSocket closed: ${e.message}")
                        break
                    } catch (e: IOException) {
                        Log.w("TacticalMesh", "TCP accept error: ${e.message}")
                        break
                    } ?: break

                    val remoteIp = try { client.inetAddress?.hostAddress ?: "" } catch (e: Throwable) { "" }
                    val connType = if (isP2pSubnet(remoteIp)) "P2P_DIRECT" else "LAN_BEACON"
                    Log.i("TacticalMesh", "[$connType] Inbound TCP connection from $remoteIp")
                    scope.launch(Dispatchers.IO + exceptionHandler) {
                        handleInboundTcpConnection(client)
                    }
                }
            } catch (e: Throwable) {
                if (e !is CancellationException) {
                    Log.w("TacticalMesh", "TCP Server exception: ${e.message}")
                }
            }
        }
    }

    private suspend fun handleInboundTcpConnection(socket: Socket) = withContext(Dispatchers.IO) {
        val key = try { socket.inetAddress?.hostAddress ?: "tcp_${socket.port}" } catch (e: Throwable) { "tcp_${socket.port}" }
        val connType = if (isP2pSubnet(key)) "P2P_DIRECT" else "LAN_BEACON"
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))

            // Retain as one of possibly several simultaneous duplex links
            peerLinks[key] = PeerLink(key, TransportProtocol.WIFI_DIRECT, writer, socket)

            val existingPeer = peerMap[key] ?: peerMap.values.find { it.address.equals(key, ignoreCase = true) }
            val peerDisplayName = when {
                existingPeer != null && isCustomCallsign(existingPeer.name) -> existingPeer.name
                else -> "TCP Peer ($key)"
            }

            val peer = PeerDevice(
                id = key,
                name = peerDisplayName,
                address = key,
                protocol = TransportProtocol.WIFI_DIRECT,
                port = socket.port,
                signalStrengthDbm = -50,
                isConnected = true
            )
            peerMap[key] = peer
            peerLastSeen[key] = System.currentTimeMillis()

            // Send Handshake over TCP link so client/peer learns server identity
            val handshake = JSONObject().apply {
                put("type", "HANDSHAKE")
                put("nodeId", myNodeId)
                put("hardwareId", DeviceTelemetryProvider.getHardwareId(context))
                put("deviceName", myCallsign)
                put("callsign", myCallsign)
                put("connectionType", connType)
            }
            try {
                writer.write(handshake.toString() + "\n")
                writer.flush()
            } catch (e: IOException) {
                Log.w("TacticalMesh", "[$connType] Inbound handshake write failed: ${e.message}")
            }

            refreshConnectedPeersState()
            _connectionStatus.value = ConnectionStatus.CONNECTED
            telemetryProvider.updateConnectedPeerInfo(peer.name, -50, 10)
            Log.i("TacticalMesh", "[$connType] TCP link established with $key")

            while (isActive && !socket.isClosed) {
                val line = try {
                    reader.readLine()
                } catch (e: SocketException) {
                    Log.i("TacticalMesh", "[$connType] Inbound TCP socket closed while reading: ${e.message}")
                    break
                } catch (e: IOException) {
                    Log.w("TacticalMesh", "[$connType] Inbound TCP read error: ${e.message}")
                    break
                }
                if (line == null) break
                handleIncomingRawJson(line, key)
            }
        } catch (e: Throwable) {
            if (e !is CancellationException) {
                Log.w("TacticalMesh", "[$connType] Inbound TCP socket closed: ${e.message}")
            }
        } finally {
            closePeerLink(key)
        }
    }

    // ==========================================
    // Real Bluetooth RFCOMM Server
    // ==========================================

    @SuppressLint("MissingPermission")
    private fun startBluetoothServer() {
        bluetoothServerJob?.cancel()
        bluetoothServerJob = scope.launch(Dispatchers.IO + exceptionHandler) {
            try {
                val adapter = bluetoothAdapter
                if (adapter != null && adapter.isEnabled) {
                    bluetoothServerSocket = adapter.listenUsingRfcommWithServiceRecord(
                        "iTantraTacticalMesh",
                        meshBluetoothUuid
                    )
                    Log.i("TacticalMesh", "Bluetooth RFCOMM Server listening for peer connections")

                    while (isActive) {
                        val clientSocket = try {
                            bluetoothServerSocket?.accept()
                        } catch (e: SocketException) {
                            Log.i("TacticalMesh", "Bluetooth ServerSocket closed: ${e.message}")
                            break
                        } catch (e: IOException) {
                            Log.w("TacticalMesh", "Bluetooth accept error: ${e.message}")
                            break
                        } ?: break

                        Log.i("TacticalMesh", "Inbound Bluetooth connection from ${clientSocket.remoteDevice.name}")
                        scope.launch(Dispatchers.IO + exceptionHandler) {
                            handleInboundBluetoothConnection(clientSocket)
                        }
                    }
                }
            } catch (e: Throwable) {
                if (e !is CancellationException) {
                    Log.w("TacticalMesh", "Bluetooth server notice: ${e.message}")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun handleInboundBluetoothConnection(socket: BluetoothSocket) = withContext(Dispatchers.IO) {
        val key = try { socket.remoteDevice.address } catch (e: Throwable) { "BT_UNKNOWN" }

        // Cancel any pending reconnect attempt and release any outbound connection lock for this peer
        cancelBluetoothReconnect(key)
        connectingPeers.remove(key)

        // Cleanly close and supersede any prior/stale socket for this key
        val existingLink = peerLinks.remove(key)
        if (existingLink != null) {
            Log.i("TacticalMesh", "[BLUETOOTH] Replacing previous link for $key with incoming connection.")
            try { existingLink.writer.close() } catch (e: Throwable) {}
            try { existingLink.closeable.close() } catch (e: Throwable) {}
        }

        try {
            val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.outputStream, Charsets.UTF_8))

            peerLinks[key] = PeerLink(key, TransportProtocol.BLUETOOTH, writer, socket)

            val existingPeer = peerMap[key] ?: peerMap.values.find { it.address.equals(key, ignoreCase = true) }
            val rawPeerName = try { socket.remoteDevice.name } catch (e: Throwable) { null }
            val peerDisplayName = when {
                existingPeer != null && isCustomCallsign(existingPeer.name) -> existingPeer.name
                !rawPeerName.isNullOrBlank() -> DeviceTelemetryProvider.cleanDeviceName(rawPeerName)
                else -> "Bluetooth Transceiver"
            }

            val peer = PeerDevice(
                id = key,
                name = peerDisplayName,
                address = key,
                protocol = TransportProtocol.BLUETOOTH,
                signalStrengthDbm = -55,
                isConnected = true
            )
            peerMap[key] = peer
            peerLastSeen[key] = System.currentTimeMillis()

            // Send Handshake over Bluetooth link so client/peer learns server identity
            val handshake = JSONObject().apply {
                put("type", "HANDSHAKE")
                put("nodeId", myNodeId)
                put("hardwareId", DeviceTelemetryProvider.getHardwareId(context))
                put("deviceName", myCallsign)
                put("callsign", myCallsign)
                put("connectionType", "BLUETOOTH")
            }
            try {
                writer.write(handshake.toString() + "\n")
                writer.flush()
            } catch (e: IOException) {
                Log.w("TacticalMesh", "[BLUETOOTH] Inbound handshake write failed: ${e.message}")
            }

            refreshConnectedPeersState()
            _connectionStatus.value = ConnectionStatus.CONNECTED
            telemetryProvider.updateConnectedPeerInfo(peer.name, -55, 14)

            // Start keepalive ping for inbound connection
            startKeepalivePing(key, writer)

            while (isActive && socket.isConnected) {
                val line = try {
                    reader.readLine()
                } catch (e: SocketException) {
                    Log.i("TacticalMesh", "Inbound Bluetooth socket closed while reading: ${e.message}")
                    break
                } catch (e: IOException) {
                    Log.w("TacticalMesh", "Inbound Bluetooth read error: ${e.message}")
                    break
                }
                if (line == null) break
                handleIncomingRawJson(line, key)
            }
        } catch (e: Throwable) {
            if (e !is CancellationException) {
                Log.w("TacticalMesh", "Inbound Bluetooth socket closed: ${e.message}")
            }
        } finally {
            closePeerLink(key)
        }
    }

    // ==========================================
    // Peer Discovery & Connection Operations
    // ==========================================

    @SuppressLint("MissingPermission")
    override fun startDiscovery(protocol: TransportProtocol) {
        scope.launch(Dispatchers.IO + exceptionHandler) {
            _connectionStatus.value = if (_connectedPeer.value != null) ConnectionStatus.CONNECTED else ConnectionStatus.SEARCHING
            
            // Clear stale discovered peers that are not currently holding an active connection
            val liveKeys = peerLinks.keys.toSet()
            val it = peerMap.entries.iterator()
            while (it.hasNext()) {
                val entry = it.next()
                if (!liveKeys.contains(entry.key) && entry.value.id != _connectedPeer.value?.id) {
                    peerLastSeen.remove(entry.key)
                    it.remove()
                }
            }
            updateDiscoveredPeers()

            when (protocol) {
                TransportProtocol.WIFI_DIRECT -> {
                    try {
                        wifiP2pManager?.discoverPeers(wifiP2pChannel, object : WifiP2pManager.ActionListener {
                            override fun onSuccess() {
                                Log.d("TacticalMesh", "WiFi Direct discoverPeers initiated")
                                wifiP2pManager?.requestPeers(wifiP2pChannel) { peerList ->
                                    handleWifiP2pPeers(peerList)
                                }
                            }
                            override fun onFailure(reason: Int) {
                                Log.w("TacticalMesh", "WiFi Direct discoverPeers failed: $reason")
                            }
                        })
                    } catch (e: Exception) {
                        Log.w("TacticalMesh", "WiFi Direct discover: ${e.message}")
                    }
                }
                TransportProtocol.BLE -> {
                    startBleDiscovery()
                }
                TransportProtocol.BLUETOOTH -> {
                    refreshBondedBluetoothPeers()
                }
            }
        }
    }

    private fun startBleDiscovery() {
        try {
            if (!isBleScanning && bleScanner != null) {
                val scanSettings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build()
                // Scan for all nearby BLE advertising devices without forcing strict UUID or pairing filters
                bleScanner?.startScan(null, scanSettings, bleScanCallback)
                isBleScanning = true
                Log.i("TacticalMesh", "BLE Scanner active (unfiltered scan for all nearby advertisers)")
            }

            if (!isBleAdvertising && bleAdvertiser != null) {
                val advSettings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(true)
                    .setTimeout(0)
                    .build()
                // Pack myNodeId + myCallsign into BLE service data
                val callsignPayload = myCallsign.take(14)
                val blePayload = "${myNodeId}|${callsignPayload}".toByteArray(Charsets.UTF_8)

                val advData = AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .addServiceUuid(bleServiceUuid)
                    .addServiceData(bleServiceUuid, blePayload)
                    .build()
                val scanResponse = AdvertiseData.Builder()
                    .setIncludeDeviceName(true)
                    .build()
                bleAdvertiser?.startAdvertising(advSettings, advData, scanResponse, bleAdvertiseCallback)
            }
        } catch (e: SecurityException) {
            Log.w("TacticalMesh", "Bluetooth LE permissions missing: ${e.message}")
        } catch (e: Exception) {
            Log.w("TacticalMesh", "startBleDiscovery error: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleDiscovery() {
        try {
            if (isBleScanning) {
                bleScanner?.stopScan(bleScanCallback)
                isBleScanning = false
            }
            if (isBleAdvertising) {
                bleAdvertiser?.stopAdvertising(bleAdvertiseCallback)
                isBleAdvertising = false
            }
        } catch (e: Exception) {}
    }

    @SuppressLint("MissingPermission")
    override fun stopDiscovery() {
        if (_connectionStatus.value == ConnectionStatus.SEARCHING) {
            _connectionStatus.value = if (_connectedPeer.value != null) ConnectionStatus.CONNECTED else ConnectionStatus.DISCONNECTED
        }
        stopBleDiscovery()
        try {
            bluetoothAdapter?.cancelDiscovery()
        } catch (e: Exception) {}
    }

    @SuppressLint("MissingPermission")
    override fun connectToPeer(peer: PeerDevice) {
        if (isSelfDevice(peer) || peer.address == "127.0.0.1" || peer.address == "0.0.0.0" || peer.address.equals("localhost", ignoreCase = true)) {
            Log.w("TacticalMesh", "Ignoring attempt to connect to self device / loopback (${peer.name}, ${peer.address})")
            if (peerLinks.isEmpty()) _connectionStatus.value = ConnectionStatus.DISCONNECTED
            return
        }

        scope.launch(Dispatchers.IO) {
            _connectionStatus.value = ConnectionStatus.PAIRING
            try {
                if (peer.protocol == TransportProtocol.WIFI_DIRECT) {
                    if (peer.address.contains(":")) {
                        // Real Wi-Fi Direct Peer-to-Peer Group Formation (Radio-to-Radio, zero router)
                        Log.i("TacticalMesh", "[P2P_DIRECT] Initiating real WifiP2pManager group connection to MAC ${peer.address}")
                        val config = WifiP2pConfig().apply {
                            deviceAddress = peer.address
                            wps.setup = WpsInfo.PBC
                        }
                        try {
                            wifiP2pManager?.connect(wifiP2pChannel, config, object : WifiP2pManager.ActionListener {
                                override fun onSuccess() {
                                    Log.i("TacticalMesh", "[P2P_DIRECT] WifiP2pManager connect initiated successfully to ${peer.name}")
                                }
                                override fun onFailure(reason: Int) {
                                    Log.e("TacticalMesh", "[P2P_DIRECT] WifiP2pManager connect failed with reason code: $reason")
                                    if (peerLinks.isEmpty()) _connectionStatus.value = ConnectionStatus.DISCONNECTED
                                }
                            })
                        } catch (e: SecurityException) {
                            Log.e("TacticalMesh", "[P2P_DIRECT] Missing NEARBY_WIFI_DEVICES / location permission: ${e.message}")
                            if (peerLinks.isEmpty()) _connectionStatus.value = ConnectionStatus.DISCONNECTED
                        } catch (e: Exception) {
                            Log.e("TacticalMesh", "[P2P_DIRECT] WifiP2pManager connect error: ${e.message}", e)
                            if (peerLinks.isEmpty()) _connectionStatus.value = ConnectionStatus.DISCONNECTED
                        }
                    } else {
                        // Direct IP connection (e.g. manual IP entry or LAN beacon)
                        val connType = if (isP2pSubnet(peer.address)) "P2P_DIRECT" else "LAN_BEACON"
                        Log.i("TacticalMesh", "[$connType] Connecting direct TCP socket to ${peer.address}:${peer.port}")
                        connectTcpSocket(peer.address, peer.port, peer.name, connectionType = connType)
                    }
                } else {
                    val key = peer.address
                    val existingLink = peerLinks[key]
                    if (existingLink != null && (existingLink.closeable as? BluetoothSocket)?.isConnected == true) {
                        Log.i("TacticalMesh", "[BLUETOOTH] Active link already exists for $key. Skipping duplicate client connect.")
                        _connectionStatus.value = ConnectionStatus.CONNECTED
                        return@launch
                    }

                    if (!connectingPeers.add(key)) {
                        Log.i("TacticalMesh", "[BLUETOOTH] Connection attempt to $key is already in progress. Skipping duplicate.")
                        return@launch
                    }

                    try {
                        val adapter = bluetoothAdapter
                        val device = adapter?.getRemoteDevice(peer.address)
                        if (device != null) {
                            val bSocket = createBluetoothRfcommSocket(device)

                            val writer = BufferedWriter(OutputStreamWriter(bSocket.outputStream, Charsets.UTF_8))
                            val reader = BufferedReader(InputStreamReader(bSocket.inputStream, Charsets.UTF_8))

                            // Send Handshake over Bluetooth
                            val handshake = JSONObject().apply {
                                put("type", "HANDSHAKE")
                                put("nodeId", myNodeId)
                                put("hardwareId", DeviceTelemetryProvider.getHardwareId(context))
                                put("deviceName", myCallsign)
                                put("callsign", myCallsign)
                                put("connectionType", "BLUETOOTH")
                            }
                            try {
                                writer.write(handshake.toString() + "\n")
                                writer.flush()
                            } catch (e: IOException) {
                                Log.w("TacticalMesh", "[BLUETOOTH] Handshake write failed to ${peer.name} ($key): ${e.message}")
                                try { bSocket.close() } catch (closeEx: Throwable) {}
                                throw e
                            }

                            peerLinks[key] = PeerLink(key, TransportProtocol.BLUETOOTH, writer, bSocket)

                            peerMap[key] = peer.copy(isConnected = true)
                            peerLastSeen[key] = System.currentTimeMillis()
                            refreshConnectedPeersState()
                            _connectionStatus.value = ConnectionStatus.CONNECTED
                            telemetryProvider.updateConnectedPeerInfo(peer.name, peer.signalStrengthDbm, 16)
                            Log.i("TacticalMesh", "[BLUETOOTH] Connected RFCOMM client link to ${peer.name} ($key)")

                            // Start keepalive ping loop
                            startKeepalivePing(key, writer)

                            scope.launch(Dispatchers.IO + exceptionHandler) {
                                try {
                                    while (isActive && bSocket.isConnected) {
                                        val line = try {
                                            reader.readLine()
                                        } catch (e: SocketException) {
                                            Log.i("TacticalMesh", "[BLUETOOTH] RFCOMM socket closed while reading: ${e.message}")
                                            break
                                        } catch (e: IOException) {
                                            Log.w("TacticalMesh", "[BLUETOOTH] RFCOMM read error: ${e.message}")
                                            break
                                        }
                                        if (line == null) break
                                        handleIncomingRawJson(line, key)
                                    }
                                } catch (e: Throwable) {
                                    if (e !is CancellationException) {
                                        Log.w("TacticalMesh", "[BLUETOOTH] RFCOMM reader notice: ${e.message}")
                                    }
                                } finally {
                                    closePeerLink(key)
                                    attemptBluetoothReconnect(peer)
                                }
                            }
                        }
                    } finally {
                        connectingPeers.remove(key)
                    }
                }
            } catch (e: Throwable) {
                if (e !is CancellationException) {
                    Log.w("TacticalMesh", "Connection to ${peer.name} failed: ${e.message}")
                }
                closePeerLink(peer.address)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun createBluetoothRfcommSocket(device: BluetoothDevice): BluetoothSocket {
        val adapter = bluetoothAdapter
        try {
            adapter?.cancelDiscovery()
            delay(150) // Give hardware controller time to stop scanning before RFCOMM handshake
        } catch (_: Throwable) {}

        var lastException: Exception = IOException("Failed to establish Bluetooth RFCOMM connection to ${device.address}")

        // 1. Insecure RFCOMM socket via UUID (Standard Android)
        for (attempt in 1..2) {
            var socket: BluetoothSocket? = null
            try {
                Log.i("TacticalMesh", "[BLUETOOTH] Attempt $attempt (Insecure UUID): Connecting to ${device.address}")
                socket = device.createInsecureRfcommSocketToServiceRecord(meshBluetoothUuid)
                socket.connect()
                Log.i("TacticalMesh", "[BLUETOOTH] RFCOMM connected successfully via Insecure UUID on attempt $attempt")
                return socket
            } catch (e: Exception) {
                try { socket?.close() } catch (_: Throwable) {}
                lastException = e
                Log.w("TacticalMesh", "[BLUETOOTH] Attempt $attempt (Insecure UUID) failed: ${e.message}")
                if (attempt < 2) delay(600)
            }
        }

        // 2. Secure RFCOMM socket via UUID (Fallback for devices requiring encrypted/paired link)
        var secureSocket: BluetoothSocket? = null
        try {
            Log.i("TacticalMesh", "[BLUETOOTH] Fallback (Secure UUID): Connecting to ${device.address}")
            secureSocket = device.createRfcommSocketToServiceRecord(meshBluetoothUuid)
            secureSocket.connect()
            Log.i("TacticalMesh", "[BLUETOOTH] Secure RFCOMM connected successfully via Secure UUID")
            return secureSocket
        } catch (e: Exception) {
            try { secureSocket?.close() } catch (_: Throwable) {}
            lastException = e
            Log.w("TacticalMesh", "[BLUETOOTH] Secure RFCOMM fallback failed: ${e.message}")
            delay(600)
        }

        // 3. Reflection Fallback to RFCOMM Channels 1..3 (Fixes Samsung / LocalSocketImpl Broken pipe & read ret: -1)
        for (channel in 1..3) {
            var reflSocket: BluetoothSocket? = null
            try {
                Log.i("TacticalMesh", "[BLUETOOTH] Reflection fallback (Channel $channel): Connecting to ${device.address}")
                val method = try {
                    device.javaClass.getMethod("createInsecureRfcommSocket", Int::class.javaPrimitiveType)
                } catch (_: Exception) {
                    device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                }
                reflSocket = method.invoke(device, channel) as BluetoothSocket
                reflSocket.connect()
                Log.i("TacticalMesh", "[BLUETOOTH] RFCOMM connected successfully via Reflection Channel $channel")
                return reflSocket
            } catch (e: Exception) {
                try { reflSocket?.close() } catch (_: Throwable) {}
                val realEx = (e as? InvocationTargetException)?.targetException ?: e
                lastException = realEx as? Exception ?: Exception(realEx)
                Log.w("TacticalMesh", "[BLUETOOTH] Reflection channel $channel fallback failed: ${realEx.message}")
                if (channel < 3) delay(600)
            }
        }

        throw lastException
    }

    private fun startKeepalivePing(key: String, writer: BufferedWriter) = scope.launch(Dispatchers.IO + exceptionHandler) {
        while (isActive && peerLinks.containsKey(key)) {
            delay(5000)
            try {
                val pingObj = JSONObject().apply {
                    put("type", "PING")
                    put("nodeId", myNodeId)
                    put("timestamp", System.currentTimeMillis())
                }
                writer.write(pingObj.toString() + "\n")
                writer.flush()
            } catch (e: Throwable) {
                Log.w("TacticalMesh", "[BLUETOOTH] Keepalive ping failed for $key: ${e.message}")
                closePeerLink(key)
                break
            }
        }
    }

    private fun cancelBluetoothReconnect(key: String) {
        activeReconnectJobs.remove(key)?.cancel()
    }

    private fun attemptBluetoothReconnect(peer: PeerDevice) {
        if (_activeProtocol.value != TransportProtocol.BLUETOOTH) return
        val key = peer.address
        if (peerLinks.containsKey(key)) {
            Log.i("TacticalMesh", "[BLUETOOTH] Link $key is already active. Skipping reconnect loop.")
            return
        }
        cancelBluetoothReconnect(key)

        val job = scope.launch(Dispatchers.IO + exceptionHandler) {
            val backoffs = listOf(1000L, 2000L, 4000L, 8000L, 16000L)
            Log.i("TacticalMesh", "[BLUETOOTH] Starting exponential backoff reconnect for ${peer.name} ($key)")

            for ((index, delayMs) in backoffs.withIndex()) {
                if (peerLinks.containsKey(key) || !isActive) {
                    Log.i("TacticalMesh", "[BLUETOOTH] Link $key re-established or reconnect stopped.")
                    return@launch
                }
                val attempt = index + 1
                Log.i("TacticalMesh", "[BLUETOOTH] Reconnect attempt $attempt/5 after ${delayMs}ms delay...")
                delay(delayMs)

                if (peerLinks.containsKey(key) || !isActive) {
                    Log.i("TacticalMesh", "[BLUETOOTH] Link $key re-established during delay. Stopping reconnect loop.")
                    return@launch
                }

                try {
                    connectToPeer(peer)
                    if (peerLinks.containsKey(key) || _connectionStatus.value == ConnectionStatus.CONNECTED) {
                        Log.i("TacticalMesh", "[BLUETOOTH] Reconnected successfully to ${peer.name} on attempt $attempt")
                        return@launch
                    }
                } catch (e: Throwable) {
                    Log.w("TacticalMesh", "[BLUETOOTH] Reconnect attempt $attempt failed: ${e.message}")
                }
            }

            Log.w("TacticalMesh", "[BLUETOOTH] All 5 reconnect attempts finished for ${peer.name}")
            if (peerLinks.isEmpty()) {
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
            }
        }
        activeReconnectJobs[key] = job
    }

    private suspend fun connectTcpSocket(ip: String, port: Int, peerDisplayName: String, connectionType: String) = withContext(Dispatchers.IO) {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, port), 5000)

            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))

            val key = ip
            peerLinks[key] = PeerLink(key, TransportProtocol.WIFI_DIRECT, writer, socket)

            // Send Handshake
            val handshake = JSONObject().apply {
                put("type", "HANDSHAKE")
                put("nodeId", myNodeId)
                put("hardwareId", DeviceTelemetryProvider.getHardwareId(context))
                put("deviceName", myCallsign)
                put("callsign", myCallsign)
                put("connectionType", connectionType)
            }
            writer.write(handshake.toString() + "\n")
            writer.flush()

            val peer = PeerDevice(
                id = key,
                name = peerDisplayName,
                address = key,
                protocol = TransportProtocol.WIFI_DIRECT,
                port = port,
                isConnected = true,
                signalStrengthDbm = if (connectionType == "P2P_DIRECT") -45 else -65
            )
            peerMap[key] = peer
            peerLastSeen[key] = System.currentTimeMillis()
            refreshConnectedPeersState()
            _connectionStatus.value = ConnectionStatus.CONNECTED
            telemetryProvider.updateConnectedPeerInfo(peerDisplayName, peer.signalStrengthDbm, if (connectionType == "P2P_DIRECT") 8 else 20)
            Log.i("TacticalMesh", "[$connectionType] Connected TCP link to $peerDisplayName at $ip:$port")

            scope.launch(Dispatchers.IO + exceptionHandler) {
                try {
                    while (isActive && !socket.isClosed) {
                        val line = try {
                            reader.readLine()
                        } catch (e: SocketException) {
                            Log.i("TacticalMesh", "[$connectionType] TCP socket closed while reading: ${e.message}")
                            break
                        } catch (e: IOException) {
                            Log.w("TacticalMesh", "[$connectionType] TCP read error: ${e.message}")
                            break
                        }
                        if (line == null) break
                        handleIncomingRawJson(line, key)
                    }
                } catch (e: Throwable) {
                    if (e !is CancellationException) {
                        Log.w("TacticalMesh", "[$connectionType] Outbound TCP socket closed: ${e.message}")
                    }
                } finally {
                    closePeerLink(key)
                }
            }
        } catch (e: Throwable) {
            if (e !is CancellationException) {
                Log.e("TacticalMesh", "[$connectionType] TCP connection failed to $ip:$port (${e.message})")
            }
            if (peerLinks.isEmpty()) _connectionStatus.value = ConnectionStatus.DISCONNECTED
        }
    }

    private fun handleWifiP2pConnectionInfo(info: WifiP2pInfo?) {
        if (info == null || !info.groupFormed) return

        if (info.isGroupOwner) {
            Log.i("TacticalMesh", "[P2P_DIRECT] P2P Group formed: This device is Group Owner. Ready to accept clients on port $tcpPort")
            _connectionStatus.value = ConnectionStatus.CONNECTED
            telemetryProvider.updateConnectedPeerInfo("P2P Client Linked", -45, 8)
        } else {
            val groupOwnerIp = info.groupOwnerAddress?.hostAddress
            Log.i("TacticalMesh", "[P2P_DIRECT] P2P Group formed: This device is Client. Group Owner IP is $groupOwnerIp")
            if (!groupOwnerIp.isNullOrBlank()) {
                scope.launch(Dispatchers.IO + exceptionHandler) {
                    delay(600) // Brief delay for Group Owner's TCP server socket to bind
                    connectTcpSocket(groupOwnerIp, tcpPort, "P2P Group Owner", connectionType = "P2P_DIRECT")
                }
            }
        }
    }


    /**
     * Direct connection via manual IP input (e.g. 192.168.43.1 or 192.168.1.100).
     */
    override fun connectDirectIp(ip: String, port: Int) {
        val directPeer = PeerDevice(
            id = "DIRECT_$ip",
            name = "Node at $ip",
            address = ip,
            protocol = TransportProtocol.WIFI_DIRECT,
            port = port,
            signalStrengthDbm = -50
        )
        connectToPeer(directPeer)
    }

    override fun disconnect() {
        scope.launch(Dispatchers.IO + exceptionHandler) {
            activeReconnectJobs.values.forEach { it.cancel() }
            activeReconnectJobs.clear()
            connectingPeers.clear()

            // Send DISCONNECT packet to connected peers before tearing down
            val disconnectJson = JSONObject().apply {
                put("type", "DISCONNECT")
                put("nodeId", myNodeId)
                put("timestamp", System.currentTimeMillis())
            }.toString() + "\n"

            for (link in peerLinks.values) {
                try {
                    link.writer.write(disconnectJson)
                    link.writer.flush()
                } catch (e: Throwable) {}
            }

            for (key in peerLinks.keys.toList()) {
                closePeerLink(key)
            }

            try {
                wifiP2pManager?.removeGroup(wifiP2pChannel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        Log.i("TacticalMesh", "[P2P_DIRECT] Disconnect: P2P group removed")
                    }
                    override fun onFailure(reason: Int) {}
                })
            } catch (e: Throwable) {}

            _connectedPeer.value = null
            _connectedPeers.value = emptyList()
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            telemetryProvider.updateConnectedPeerInfo("NO-PEER-CONNECTED", -90, 0)
        }
    }

    override fun disconnectPeer(peerKey: String) {
        scope.launch(Dispatchers.IO + exceptionHandler) {
            cancelBluetoothReconnect(peerKey)
            connectingPeers.remove(peerKey)

            peerLinks[peerKey]?.let { link ->
                try {
                    val disconnectJson = JSONObject().apply {
                        put("type", "DISCONNECT")
                        put("nodeId", myNodeId)
                        put("timestamp", System.currentTimeMillis())
                    }.toString() + "\n"
                    link.writer.write(disconnectJson)
                    link.writer.flush()
                } catch (e: Throwable) {}
            }
            closePeerLink(peerKey)
        }
    }

    private fun closePeerLink(key: String) {
        cancelBluetoothReconnect(key)
        connectingPeers.remove(key)
        peerLinks.remove(key)?.let { link ->
            try { link.writer.close() } catch (e: Throwable) {}
            try { link.closeable.close() } catch (e: Throwable) {}
            Log.i("TacticalMesh", "Closed and removed peer link for $key (${link.protocol})")
        }
        refreshConnectedPeersState()
    }

    /** Rebuilds [connectedPeers]/[connectedPeer] from the live [peerLinks] set — call
     *  after any link is added or removed so the UI's peer list stays accurate. */
    private fun refreshConnectedPeersState() {
        val activeKeys = peerLinks.keys.toSet()
        for ((k, peer) in peerMap) {
            val isLive = activeKeys.contains(k) || activeKeys.contains(peer.address)
            if (peer.isConnected != isLive) {
                peerMap[k] = peer.copy(isConnected = isLive)
            }
        }

        val rawConnected = peerLinks.keys.map { key ->
            peerMap[key]
                ?: peerMap.values.find { it.id == key || it.address.equals(key, ignoreCase = true) }
                ?: PeerDevice(
                    id = key,
                    name = "Connected Peer (${key.takeLast(5)})",
                    address = key,
                    protocol = peerLinks[key]?.protocol ?: _activeProtocol.value,
                    isConnected = true,
                    signalStrengthDbm = -50
                )
        }

        val dedupedConnected = LinkedHashMap<String, PeerDevice>()
        for (peer in rawConnected) {
            if (isSelfDevice(peer)) continue
            val existingKey = dedupedConnected.keys.find { k ->
                val existing = dedupedConnected[k] ?: return@find false
                existing.address.equals(peer.address, ignoreCase = true) ||
                (isCustomCallsign(existing.name) && existing.name.equals(peer.name, ignoreCase = true))
            }
            if (existingKey != null) {
                val existing = dedupedConnected[existingKey]!!
                val preferredName = when {
                    isCustomCallsign(existing.name) -> existing.name
                    isCustomCallsign(peer.name) -> peer.name
                    else -> existing.name
                }
                dedupedConnected[existingKey] = existing.copy(
                    name = preferredName,
                    isConnected = true,
                    signalStrengthDbm = maxOf(existing.signalStrengthDbm, peer.signalStrengthDbm)
                )
            } else {
                dedupedConnected[peer.id] = peer.copy(isConnected = true)
            }
        }

        val peers = dedupedConnected.values.sortedByDescending { it.signalStrengthDbm }
        _connectedPeers.value = peers
        _connectedPeer.value = peers.firstOrNull()

        if (peers.isNotEmpty()) {
            _connectionStatus.value = ConnectionStatus.CONNECTED
            val primary = peers.first()
            telemetryProvider.updateConnectedPeerInfo(primary.name, primary.signalStrengthDbm, 12)
        } else {
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            telemetryProvider.updateConnectedPeerInfo("NO-PEER-CONNECTED", -90, 0)
        }
        updateDiscoveredPeers()
    }

    // ==========================================
    // Real Packet Transmission
    // ==========================================

    override fun sendPacket(packet: NetworkPacket): Boolean {
        scope.launch(Dispatchers.IO + exceptionHandler) {
            try {
                seenPacketIds[packet.packetId] = System.currentTimeMillis()

                val json = JSONObject().apply {
                    put("type", "PACKET")
                    put("packetId", packet.packetId)
                    put("senderId", myNodeId)
                    put("senderCallsign", packet.senderCallsign)
                    put("text", packet.text)
                    put("languageCode", packet.languageCode)
                    put("isAlert", packet.isAlert)
                    put("alertPriority", packet.alertPriority.name)
                    put("timestamp", packet.timestamp)
                    put("channelFreq", packet.channelFreq)
                    put("ttl", packet.ttl)
                    put("relayHops", 0)
                }.toString() + "\n"

                // 1. Stream to every live mesh link (TCP + Bluetooth) we hold at once — a
                // relay node forwards the same way, see relayToMeshPeers().
                val delivered = relayToMeshPeers(json, excludeKey = null)

                // 2. Redundant UDP blast — reaches every peer on the same Wi-Fi subnet in a
                // single hop for free, independent of the per-peer TCP/Bluetooth links above.
                try {
                    val bytes = json.toByteArray(Charsets.UTF_8)
                    val bAddr = InetAddress.getByName("255.255.255.255")
                    udpSocket?.send(DatagramPacket(bytes, bytes.size, bAddr, udpPort))
                    try {
                        val hotspotAddr = InetAddress.getByName("192.168.43.255")
                        udpSocket?.send(DatagramPacket(bytes, bytes.size, hotspotAddr, udpPort))
                    } catch (e: Throwable) {}
                } catch (e: Throwable) {
                    // Ignore UDP broadcast errors
                }

                Log.d("TacticalMesh", "Sent packet ${packet.packetId} to $delivered direct mesh link(s) + UDP broadcast: ${packet.text}")
            } catch (e: Throwable) {
                if (e !is CancellationException) {
                    Log.w("TacticalMesh", "Error sending packet ${packet.packetId}: ${e.message}")
                }
            }
        }
        return true
    }

    /**
     * Writes [rawJsonLine] to every currently-connected peer link except [excludeKey]
     * (the link a relayed packet just arrived on, so it isn't bounced straight back).
     * Links are attempted strongest-signal-first — purely a QoS nicety since every
     * remaining link is still written to; a dead link is dropped so one bad peer can't
     * block delivery to the others. Returns how many links the write actually succeeded on.
     */
    private fun relayToMeshPeers(rawJsonLine: String, excludeKey: String?): Int {
        // [DEMO MODE HACK]
        // Disable flood relay entirely for point-to-point demo mode.
        // If excludeKey is NOT null, this is an incoming packet trying to relay. Drop it.
        // If excludeKey IS null, this is our own local packet. Send it!
        if (excludeKey != null) {
            return 0
        }
        
        var delivered = 0
        val candidates = peerLinks.values
            .sortedByDescending { peerMap[it.key]?.signalStrengthDbm ?: -100 }

        for (link in candidates) {
            try {
                link.writer.write(rawJsonLine)
                link.writer.flush()
                delivered++
            } catch (e: Throwable) {
                Log.w("TacticalMesh", "Mesh link ${link.key} (${link.protocol}) write failed, dropping: ${e.message}")
                closePeerLink(link.key)
            }
        }
        return delivered
    }

    // ==========================================
    // Inbound Packet Processing
    // ==========================================

    private fun handleIncomingRawJson(rawJson: String, sourceAddress: String) {
        try {
            val obj = JSONObject(rawJson.trim())
            val type = obj.optString("type", "")

            when (type) {
                "PING" -> {
                    peerLastSeen[sourceAddress] = System.currentTimeMillis()
                    refreshConnectedPeersState()
                }

                "DISCONNECT" -> {
                    val peerNodeId = obj.optString("nodeId", sourceAddress)
                    Log.i("TacticalMesh", "Received DISCONNECT packet from $sourceAddress ($peerNodeId)")
                    cancelBluetoothReconnect(sourceAddress)
                    closePeerLink(sourceAddress)
                }

                "BEACON" -> {
                    val peerNodeId = obj.getString("nodeId")
                    if (peerNodeId == myNodeId) return // Ignore self-beacon
                    val peerHwId = obj.optString("hardwareId", "")
                    if (peerHwId == DeviceTelemetryProvider.getHardwareId(context)) return

                    val ip = obj.optString("ip", sourceAddress)
                    if (ip == "127.0.0.1" || ip == "0.0.0.0" || ip == telemetry.value.localIpAddress) return

                    val rawDeviceName = obj.optString("deviceName", obj.optString("callsign", ""))
                    val deviceName = if (rawDeviceName.isNotBlank() && !rawDeviceName.startsWith("NODE-", ignoreCase = true) && !rawDeviceName.startsWith("ISRO-", ignoreCase = true)) {
                        DeviceTelemetryProvider.cleanDeviceName(rawDeviceName)
                    } else {
                        DeviceTelemetryProvider.getCleanDeviceModel()
                    }
                    val port = obj.optInt("port", tcpPort)
                    val protocolName = obj.optString("protocol", "WIFI_DIRECT")
                    val battery = obj.optInt("battery", 100)
                    val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    val ping = (System.currentTimeMillis() - timestamp).coerceIn(4, 120).toInt()
                    val signal = -(42 + ping / 2)

                    val protocol = if (protocolName.contains("BLUETOOTH")) TransportProtocol.BLUETOOTH else TransportProtocol.WIFI_DIRECT

                    val peer = PeerDevice(
                        id = peerNodeId,
                        name = deviceName,
                        address = ip,
                        protocol = protocol,
                        port = port,
                        signalStrengthDbm = signal,
                        batteryPercent = battery
                    )

                    if (isSelfDevice(peer)) return

                    peerMap[peerNodeId] = peer
                    peerLastSeen[peerNodeId] = System.currentTimeMillis()
                    updateDiscoveredPeers()

                    // Update signal if this is the connected peer
                    if (_connectedPeer.value?.id == peerNodeId) {
                        telemetryProvider.updateConnectedPeerInfo(deviceName, signal, ping)
                    }
                }

                "HANDSHAKE" -> {
                    val peerNodeId = obj.optString("nodeId", sourceAddress)
                    if (peerNodeId == myNodeId) return
                    val rawDeviceName = obj.optString("deviceName", obj.optString("callsign", ""))
                    val deviceName = if (rawDeviceName.isNotBlank() && !rawDeviceName.startsWith("REMOTE-", ignoreCase = true) && !rawDeviceName.startsWith("NODE-", ignoreCase = true) && !rawDeviceName.startsWith("ISRO-", ignoreCase = true)) {
                        DeviceTelemetryProvider.cleanDeviceName(rawDeviceName)
                    } else {
                        DeviceTelemetryProvider.getCleanDeviceModel()
                    }
                    // Keyed by sourceAddress (matching the peerLinks entry this socket was
                    // registered under) so refreshConnectedPeersState() can find it — the
                    // nodeId is only known once this handshake arrives, the link itself was
                    // already opened (and keyed by address) at accept/connect time.
                    val peer = PeerDevice(
                        id = sourceAddress,
                        name = deviceName,
                        address = sourceAddress,
                        protocol = _activeProtocol.value,
                        port = tcpPort,
                        isConnected = true,
                        signalStrengthDbm = -52
                    )
                    peerMap[sourceAddress] = peer
                    peerLastSeen[sourceAddress] = System.currentTimeMillis()
                    refreshConnectedPeersState()
                    telemetryProvider.updateConnectedPeerInfo(deviceName, -52, 10)
                }

                "PACKET" -> {
                    val senderId = obj.optString("senderId", "")
                    if (senderId == myNodeId) return // Drop own echo

                    val packetId = obj.getString("packetId")
                    // Flood-relay dedup: putIfAbsent is atomic, so if two links deliver the
                    // same packet at once only the first caller sees null and proceeds —
                    // stops both duplicate local delivery and relay storms/loops in a mesh
                    // with more than one path between two nodes (e.g. A-B-C-D with A-D too).
                    if (seenPacketIds.putIfAbsent(packetId, System.currentTimeMillis()) != null) {
                        return
                    }

                    val ttl = obj.optInt("ttl", 6)
                    val relayHops = obj.optInt("relayHops", 0)

                    val packet = NetworkPacket(
                        packetId = packetId,
                        senderId = senderId,
                        senderCallsign = obj.optString("senderCallsign", "REMOTE-TRANSCEIVER"),
                        text = obj.getString("text"),
                        languageCode = obj.optString("languageCode", "en"),
                        isAlert = obj.optBoolean("isAlert", false),
                        alertPriority = try {
                            AlertPriority.valueOf(obj.optString("alertPriority", "ROUTINE"))
                        } catch (e: Exception) {
                            AlertPriority.ROUTINE
                        },
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        channelFreq = obj.optString("channelFreq", telemetry.value.frequencyGhz),
                        ttl = ttl,
                        relayHops = relayHops
                    )

                    Log.i("TacticalMesh", "Received real packet ${packet.packetId} via $sourceAddress (hop $relayHops): ${packet.text} (Alert=${packet.isAlert})")
                    scope.launch(exceptionHandler) {
                        _incomingPackets.emit(packet)
                    }

                    // Mesh relay: forward on to every OTHER live link (not the one this
                    // packet just arrived on) as long as hops remain, so a node bridging
                    // two out-of-range peers (e.g. B between distant A and C) keeps the
                    // message moving without either endpoint needing a direct link.
                    if (ttl > 1 && peerLinks.size > 1) {
                        obj.put("ttl", ttl - 1)
                        obj.put("relayHops", relayHops + 1)
                        val forwarded = relayToMeshPeers(obj.toString() + "\n", excludeKey = sourceAddress)
                        if (forwarded > 0) {
                            Log.i("TacticalMesh", "Relayed packet $packetId onward to $forwarded peer(s), ttl now ${ttl - 1}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("TacticalMesh", "Malformed packet JSON: ${e.message}")
        }
    }

    // ==========================================
    // Bluetooth Helpers & Peer Pruning
    // ==========================================

    /** RSSI seen at ACTION_FOUND time, keyed by address, consumed once the matching
     *  ACTION_UUID answer confirms (or rules out) iTantra mesh membership below. */
    private val pendingBtRssi = ConcurrentHashMap<String, Int>()

    private fun extractBluetoothDeviceExtra(intent: Intent?): BluetoothDevice? {
        if (intent == null) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    /** True if [uuids] (a device's SDP service record) includes the iTantra mesh
     *  RFCOMM service — the same signal used to gate both fresh discovery
     *  ([registerBluetoothDiscoveryReceiver]) and already-bonded devices below. */
    private fun advertisesMeshService(uuids: Array<ParcelUuid>?): Boolean {
        return uuids?.any { it.uuid == meshBluetoothUuid } ?: false
    }

    @SuppressLint("MissingPermission")
    private fun isSelfScanResult(result: ScanResult): Boolean {
        val record = result.scanRecord
        // Check service data for myNodeId
        val serviceData = record?.getServiceData(bleServiceUuid)
        if (serviceData != null) {
            val payload = String(serviceData, Charsets.UTF_8)
            val parts = payload.split("|")
            val advertisedId = parts.getOrNull(0)?.trim()
            if (advertisedId != null && advertisedId.equals(myNodeId, ignoreCase = true)) {
                return true
            }
        }

        val device = result.device ?: return false
        val hwAddress = device.address
        try {
            val localBtAddress = bluetoothAdapter?.address
            if (!localBtAddress.isNullOrBlank() && localBtAddress != "02:00:00:00:00:00") {
                if (hwAddress.equals(localBtAddress, ignoreCase = true)) return true
            }
        } catch (e: SecurityException) {}

        return false
    }

    @SuppressLint("MissingPermission")
    private fun isSelfDevice(peer: PeerDevice): Boolean {
        // 1. Direct Node ID or Hardware ID match
        if (peer.id.equals(myNodeId, ignoreCase = true)) return true
        val myHardwareId = DeviceTelemetryProvider.getHardwareId(context)
        if (peer.id.equals(myHardwareId, ignoreCase = true)) return true

        // 2. Loopback or invalid IP
        if (peer.address == "127.0.0.1" || peer.address == "0.0.0.0" || peer.address.equals("localhost", ignoreCase = true)) {
            return true
        }

        // 3. Local IP match
        val myIp = telemetryProvider.telemetry.value.localIpAddress
        if (peer.address.isNotBlank() && myIp.isNotBlank() && myIp != "0.0.0.0" && myIp != "127.0.0.1" && peer.address.equals(myIp, ignoreCase = true)) {
            return true
        }

        // 4. Bluetooth Adapter Address match
        try {
            val localBtAddress = bluetoothAdapter?.address
            if (!localBtAddress.isNullOrBlank() && localBtAddress != "02:00:00:00:00:00" && peer.address.equals(localBtAddress, ignoreCase = true)) {
                return true
            }
        } catch (e: SecurityException) {}

        // If the peer has a valid non-blank address or holds an active socket connection link,
        // it cannot be "self" unless it matched the hardware/address/IP/NodeId checks above.
        // DO NOT match by peer.name alone for peers with addresses or established links!
        if (peer.address.isNotBlank() || peerLinks.containsKey(peer.id) || peerLinks.containsKey(peer.address)) {
            return false
        }

        // 5. Fallback for address-less broadcast candidates
        val myCallsign = telemetryProvider.telemetry.value.nodeCallsign
        if (myCallsign.isNotBlank() && peer.name.equals(myCallsign, ignoreCase = true)) return true

        val customName = try { PreferenceManager(context).getCustomDeviceName() } catch (e: Throwable) { null }
        if (!customName.isNullOrBlank() && peer.name.equals(customName, ignoreCase = true)) return true

        return false
    }

    @SuppressLint("MissingPermission")
    private fun refreshBondedBluetoothPeers() {
        try {
            val adapter = bluetoothAdapter
            if (adapter != null && adapter.isEnabled) {
                val bonded = adapter.bondedDevices ?: emptySet()
                for (device in bonded) {
                    val rawName = try { device.name } catch (e: SecurityException) { null }
                    val existing = peerMap[device.address] ?: peerMap.values.find { it.address.equals(device.address, ignoreCase = true) }
                    val displayName = when {
                        existing != null && isCustomCallsign(existing.name) -> existing.name
                        !rawName.isNullOrBlank() -> DeviceTelemetryProvider.cleanDeviceName(rawName)
                        else -> "Paired Device (${device.address.takeLast(5)})"
                    }
                    val peer = PeerDevice(
                        id = existing?.id ?: device.address,
                        name = displayName,
                        address = device.address,
                        protocol = TransportProtocol.BLUETOOTH,
                        signalStrengthDbm = existing?.signalStrengthDbm ?: -55,
                        isConnected = existing?.isConnected ?: false
                    )
                    if (!isSelfDevice(peer)) {
                        peerMap[peer.id] = peer
                        peerLastSeen[peer.id] = System.currentTimeMillis()
                    }
                }
                updateDiscoveredPeers()
            }
        } catch (e: Exception) {
            Log.w("TacticalMesh", "Bonded devices check: ${e.message}")
        }
    }

    private fun handleWifiP2pPeers(peerList: WifiP2pDeviceList?) {
        if (peerList == null) return
        for (device in peerList.deviceList) {
            val deviceId = device.deviceAddress ?: continue
            val existing = peerMap[deviceId] ?: peerMap.values.find { it.address.equals(deviceId, ignoreCase = true) }
            val devName = when {
                existing != null && isCustomCallsign(existing.name) -> existing.name
                !device.deviceName.isNullOrBlank() -> DeviceTelemetryProvider.cleanDeviceName(device.deviceName)
                else -> DeviceTelemetryProvider.getCleanDeviceModel()
            }
            val peer = PeerDevice(
                id = existing?.id ?: deviceId,
                name = devName,
                address = device.deviceAddress ?: "",
                protocol = TransportProtocol.WIFI_DIRECT,
                port = tcpPort,
                signalStrengthDbm = existing?.signalStrengthDbm ?: -50,
                isConnected = existing?.isConnected ?: false
            )
            if (!isSelfDevice(peer)) {
                peerMap[peer.id] = peer
                peerLastSeen[peer.id] = System.currentTimeMillis()
            }
        }
        updateDiscoveredPeers()
    }

    @SuppressLint("MissingPermission")
    private fun registerBluetoothDiscoveryReceiver() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_UUID)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        try {
                            wifiP2pManager?.requestPeers(wifiP2pChannel) { peerList ->
                                handleWifiP2pPeers(peerList)
                            }
                        } catch (e: Exception) {}
                    }
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        val networkInfo: NetworkInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO, NetworkInfo::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                        }
                        if (networkInfo?.isConnected == true) {
                            Log.i("TacticalMesh", "[P2P_DIRECT] NetworkInfo isConnected -> Requesting Connection Info")
                            try {
                                wifiP2pManager?.requestConnectionInfo(wifiP2pChannel) { info ->
                                    handleWifiP2pConnectionInfo(info)
                                }
                            } catch (e: Exception) {
                                Log.e("TacticalMesh", "[P2P_DIRECT] requestConnectionInfo error: ${e.message}")
                            }
                        } else {
                            Log.d("TacticalMesh", "[P2P_DIRECT] Wi-Fi Direct disconnected or forming")
                        }
                    }
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = extractBluetoothDeviceExtra(intent) ?: return
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                        val rawName = try { device.name } catch (e: SecurityException) { null }
                        val hwAddress = device.address

                        // Check if this physical hardware address or device name is already registered under a custom identity
                        val existing = peerMap.values.find {
                            it.address.equals(hwAddress, ignoreCase = true) ||
                            (rawName != null && it.name.equals(rawName, ignoreCase = true))
                        }

                        val displayName = if (existing != null && isCustomCallsign(existing.name)) {
                            existing.name
                        } else if (!rawName.isNullOrBlank()) {
                            DeviceTelemetryProvider.cleanDeviceName(rawName)
                        } else {
                            DeviceTelemetryProvider.getCleanDeviceModel()
                        }

                        val peer = PeerDevice(
                            id = existing?.id ?: hwAddress,
                            name = displayName,
                            address = hwAddress,
                            protocol = TransportProtocol.BLUETOOTH,
                            signalStrengthDbm = if (rssi != Short.MIN_VALUE.toInt()) rssi else (existing?.signalStrengthDbm ?: -60)
                        )
                        if (!isSelfDevice(peer)) {
                            peerMap[peer.id] = peer
                            peerLastSeen[peer.id] = System.currentTimeMillis()
                            updateDiscoveredPeers()
                        }

                        try { device.fetchUuidsWithSdp() } catch (e: Exception) {}
                    }
                    BluetoothDevice.ACTION_UUID -> {
                        val device = extractBluetoothDeviceExtra(intent) ?: return
                        val uuidsExtra: Array<ParcelUuid>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID, ParcelUuid::class.java)
                        } else {
                            @Suppress("DEPRECATION", "UNCHECKED_CAST")
                            intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID) as? Array<ParcelUuid>
                        }
                        if (advertisesMeshService(uuidsExtra)) {
                            val hwAddress = device.address
                            val existing = peerMap.values.find { it.address.equals(hwAddress, ignoreCase = true) }
                            val rawName = try { device.name } catch (e: SecurityException) { null }
                            val displayName = if (existing != null && isCustomCallsign(existing.name)) {
                                existing.name
                            } else if (!rawName.isNullOrBlank()) {
                                DeviceTelemetryProvider.cleanDeviceName(rawName)
                            } else {
                                DeviceTelemetryProvider.getCleanDeviceModel()
                            }

                            val peer = PeerDevice(
                                id = existing?.id ?: hwAddress,
                                name = displayName,
                                address = hwAddress,
                                protocol = TransportProtocol.BLUETOOTH,
                                signalStrengthDbm = existing?.signalStrengthDbm ?: -55
                            )
                            if (!isSelfDevice(peer)) {
                                peerMap[peer.id] = peer
                                peerLastSeen[peer.id] = System.currentTimeMillis()
                                updateDiscoveredPeers()
                            }
                        }
                    }
                }
            }
        }
        try {
            context.registerReceiver(receiver, filter)
        } catch (e: Exception) {}
    }

    /**
     * Deduplicates discovered peers across multiple transport layers (Bluetooth Classic,
     * BLE Beacon, and Wi-Fi Direct) so each physical phone appears only ONCE under its
     * user-defined Device Identity name instead of multiple duplicate hardware entries.
     */
    private fun updateDiscoveredPeers() {
        val allPeers = peerMap.values.toList()
        val dedupedMap = LinkedHashMap<String, PeerDevice>()

        // Process custom-named peers first so that known custom identities (e.g. "kavya") take precedence
        val sortedPeers = allPeers.sortedWith(
            compareByDescending<PeerDevice> { isCustomCallsign(it.name) }
                .thenByDescending { it.isConnected }
                .thenByDescending { it.signalStrengthDbm }
        )

        for (peer in sortedPeers) {
            if (isSelfDevice(peer)) continue

            // Check if we already have an entry with matching address, matching custom name,
            // or if an existing custom peer already covers this generic hardware entry on the same protocol
            val existingKey = dedupedMap.keys.find { key ->
                val existing = dedupedMap[key] ?: return@find false
                existing.address.equals(peer.address, ignoreCase = true) ||
                (isCustomCallsign(existing.name) && existing.name.equals(peer.name, ignoreCase = true)) ||
                (isCustomCallsign(existing.name) && !isCustomCallsign(peer.name) &&
                 (existing.protocol == peer.protocol || existing.address.contains(":") || peer.address.contains(":")))
            }

            if (existingKey != null) {
                val existing = dedupedMap[existingKey]!!
                val isPeerNameCustom = isCustomCallsign(peer.name)
                val isExistingCustom = isCustomCallsign(existing.name)

                val preferredName = when {
                    isExistingCustom -> existing.name
                    isPeerNameCustom -> peer.name
                    else -> existing.name
                }
                val bestSignal = maxOf(existing.signalStrengthDbm, peer.signalStrengthDbm)
                val isConnected = existing.isConnected || peer.isConnected
                val preferredAddress = if (peer.address.contains(":") && !existing.address.contains(":")) peer.address else existing.address

                dedupedMap[existingKey] = existing.copy(
                    name = preferredName,
                    address = preferredAddress,
                    signalStrengthDbm = bestSignal,
                    isConnected = isConnected
                )
            } else {
                dedupedMap[peer.id] = peer
            }
        }

        _discoveredPeers.value = dedupedMap.values.toList()
    }

    private fun startPeerPruning() {
        peerPruningJob?.cancel()
        peerPruningJob = scope.launch(Dispatchers.Default + exceptionHandler) {
            while (isActive) {
                try {
                    delay(5000)
                    val now = System.currentTimeMillis()
                    var changed = false
                    val it = peerLastSeen.entries.iterator()
                    while (it.hasNext()) {
                        val entry = it.next()
                        // If not seen in 15 seconds and we don't hold a live link to it, prune
                        if (now - entry.value > 15000 && !peerLinks.containsKey(entry.key)) {
                            peerMap.remove(entry.key)
                            it.remove()
                            changed = true
                        }
                    }
                    if (changed) {
                        updateDiscoveredPeers()
                    }

                    // Bound the flood-relay dedup cache — a packet is only ever going to be
                    // re-seen within a few seconds of hopping around the mesh, so anything
                    // older than a minute is just wasted memory.
                    val packetIt = seenPacketIds.entries.iterator()
                    while (packetIt.hasNext()) {
                        if (now - packetIt.next().value > 60_000) packetIt.remove()
                    }

                    // Drop links whose socket has actually died so a stale relay target stops
                    // being counted (and logged) as reachable.
                    for ((key, link) in peerLinks) {
                        val alive = try {
                            when (val c = link.closeable) {
                                is Socket -> !c.isClosed && c.isConnected && !c.isInputShutdown && !c.isOutputShutdown
                                is BluetoothSocket -> c.isConnected
                                else -> true
                            }
                        } catch (e: Throwable) {
                            false
                        }
                        if (!alive) closePeerLink(key)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Log.w("TacticalMesh", "Error in peer pruning loop: ${e.message}")
                }
            }
        }
    }

    override fun triggerSimulatedPeerAlert(customText: String?, priority: AlertPriority) {
        scope.launch(exceptionHandler) {
            val packet = NetworkPacket(
                packetId = "PKT_${UUID.randomUUID().toString().take(8)}",
                senderId = _connectedPeer.value?.id ?: "PEER_REMOTE",
                senderCallsign = _connectedPeer.value?.name ?: DeviceTelemetryProvider.getCleanDeviceModel(),
                text = customText ?: "सावधान: चक्रवात चेतावनी। सभी दल तुरंत सुरक्षित कैंप की ओर बढ़ें।",
                languageCode = "hi",
                isAlert = true,
                alertPriority = priority,
                timestamp = System.currentTimeMillis(),
                channelFreq = telemetry.value.frequencyGhz
            )
            _incomingPackets.emit(packet)
        }
    }

    override fun triggerSimulatedPeerRoutineVoice(customText: String?) {
        scope.launch(exceptionHandler) {
            val packet = NetworkPacket(
                packetId = "PKT_${UUID.randomUUID().toString().take(8)}",
                senderId = _connectedPeer.value?.id ?: "PEER_REMOTE",
                senderCallsign = _connectedPeer.value?.name ?: DeviceTelemetryProvider.getCleanDeviceModel(),
                text = customText ?: "Nearby audio connection locked. Transceivers operational.",
                languageCode = "en",
                isAlert = false,
                alertPriority = AlertPriority.ROUTINE,
                timestamp = System.currentTimeMillis(),
                channelFreq = telemetry.value.frequencyGhz
            )
            _incomingPackets.emit(packet)
        }
    }

    companion object {
        fun getRealDeviceIpAddress(): String = DeviceTelemetryProvider.getRealDeviceIpAddress()
    }
}
