package com.example.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import com.example.model.AlertPriority
import kotlinx.coroutines.CancellationException
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bluetooth LE Bridge for External ESP32 LoRa Gateway Node (SX1278 Ra-02 Transceiver).
 *
 * BLE Specs:
 * - Service UUID: 0000ffe0-0000-1000-8000-00805f9b34fb
 * - Characteristic UUID: 0000ffe1-0000-1000-8000-00805f9b34fb (Read/Write/Notify)
 * - CCCD Descriptor: 00002902-0000-1000-8000-00805f9b34fb
 *
 * Binary Payload Format (sub-250 bytes max):
 * [Header: 1B Type | 2B MsgID | 1B HopLimit | 2B SourceID] + [GPS: 8B Lat/Long (Float32/Float32)] + [Text Payload UTF-8]
 * - Type 0x01: SOS_ALERT (High Priority)
 * - Type 0x02: TEXT_DISPATCH
 * - Type 0x03: TELEMETRY_PING
 * - Type 0x04: ACK
 */
class LoraGatewayManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onPacketReceived: (NetworkPacket) -> Unit
) {
    companion object {
        private const val TAG = "LoraGateway"

        val LORA_SERVICE_UUID: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        val LORA_CHAR_UUID: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
        val CCCD_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        const val TYPE_SOS_ALERT: Byte = 0x01
        const val TYPE_TEXT_DISPATCH: Byte = 0x02
        const val TYPE_TELEMETRY_PING: Byte = 0x03
        const val TYPE_ACK: Byte = 0x04

        const val MAX_LORA_PAYLOAD_SIZE = 248 // bytes
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val bleScanner: BluetoothLeScanner? get() = bluetoothAdapter?.bluetoothLeScanner

    private var bluetoothGatt: BluetoothGatt? = null
    private var loraCharacteristic: BluetoothGattCharacteristic? = null
    private var targetDevice: BluetoothDevice? = null

    private val _isGatewayConnected = MutableStateFlow(false)
    val isGatewayConnected: StateFlow<Boolean> = _isGatewayConnected.asStateFlow()

    private val _gatewayDeviceName = MutableStateFlow<String?>(null)
    val gatewayDeviceName: StateFlow<String?> = _gatewayDeviceName.asStateFlow()

    private val _gatewayRssi = MutableStateFlow(-100)
    val gatewayRssi: StateFlow<Int> = _gatewayRssi.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val messageSequence = AtomicInteger(1)
    private var scanJob: Job? = null
    private var autoReconnectJob: Job? = null

    private val writeQueue = ConcurrentLinkedQueue<ByteArray>()
    private var isWriting = false

    // BLE Scan Callback
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                val name = device.name ?: ""
                val hasService = result.scanRecord?.serviceUuids?.any { it.uuid == LORA_SERVICE_UUID } == true
                val isLoraMatch = hasService ||
                        name.contains("ESP32", ignoreCase = true) ||
                        name.contains("LoRa", ignoreCase = true) ||
                        name.contains("Gateway", ignoreCase = true) ||
                        name.contains("Ra-02", ignoreCase = true) ||
                        name.contains("SX1278", ignoreCase = true) ||
                        name.contains("HMSoft", ignoreCase = true) ||
                        name.contains("VoxBridge", ignoreCase = true)

                if (isLoraMatch) {
                    Log.i(TAG, "Found candidate ESP32 LoRa Gateway: ${device.name ?: "Unknown"} [${device.address}] RSSI: ${result.rssi}")
                    _gatewayRssi.value = result.rssi
                    stopScan()
                    connectToDevice(device)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "BLE scan failed with error code: $errorCode")
            _isScanning.value = false
        }
    }

    // GATT Callback for ESP32 LoRa Gateway
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "Connected to ESP32 LoRa Gateway GATT server. Discovering services...")
                    _isGatewayConnected.value = true
                    _gatewayDeviceName.value = gatt?.device?.name ?: "ESP32-LoRa-Gateway"
                    
                    // Request high MTU for efficient binary transport
                    gatt?.requestMtu(256)
                    gatt?.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.w(TAG, "Disconnected from ESP32 LoRa Gateway (status $status)")
                    _isGatewayConnected.value = false
                    _gatewayDeviceName.value = null
                    loraCharacteristic = null
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                    scheduleReconnect()
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                val service = gatt.getService(LORA_SERVICE_UUID)
                if (service != null) {
                    val characteristic = service.getCharacteristic(LORA_CHAR_UUID)
                    if (characteristic != null) {
                        loraCharacteristic = characteristic
                        Log.i(TAG, "Discovered LoRa UART characteristic: $LORA_CHAR_UUID. Subscribing to notifications...")

                        // Enable local notification
                        gatt.setCharacteristicNotification(characteristic, true)

                        // Enable remote notification on CCCD
                        val descriptor = characteristic.getDescriptor(CCCD_DESCRIPTOR_UUID)
                        if (descriptor != null) {
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(descriptor)
                            Log.i(TAG, "LoRa Gateway BLE notifications activated successfully.")
                        }
                    } else {
                        Log.w(TAG, "Service FFE0 found but FFE1 characteristic missing on LoRa Gateway.")
                    }
                } else {
                    Log.w(TAG, "LoRa Service FFE0 not found in discovered services.")
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            characteristic?.value?.let { data ->
                handleIncomingBleBytes(data)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncomingBleBytes(value)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            isWriting = false
            processNextWrite()
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _gatewayRssi.value = rssi
            }
        }
    }

    /**
     * Starts BLE scanning for ESP32 LoRa Gateway nodes.
     */
    @SuppressLint("MissingPermission")
    fun startScan() {
        if (_isScanning.value || bluetoothAdapter?.isEnabled != true) return
        try {
            _isScanning.value = true
            val filters = listOf(
                ScanFilter.Builder().setServiceUuid(ParcelUuid(LORA_SERVICE_UUID)).build()
            )
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            bleScanner?.startScan(null, settings, scanCallback)
            Log.i(TAG, "Started scanning for ESP32 LoRa Gateway...")

            scanJob?.cancel()
            scanJob = scope.launch(Dispatchers.IO) {
                delay(15000L) // 15-second timeout for discovery scan
                if (_isScanning.value && !_isGatewayConnected.value) {
                    stopScan()
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing BLE permissions for scan: ${e.message}")
            _isScanning.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BLE scan: ${e.message}")
            _isScanning.value = false
        }
    }

    /**
     * Stops active BLE scanning.
     */
    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value) return
        try {
            bleScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping BLE scan: ${e.message}")
        }
        _isScanning.value = false
        scanJob?.cancel()
    }

    /**
     * Connects to a specific BLE peripheral device.
     */
    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        targetDevice = device
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null

            Log.i(TAG, "Connecting to GATT on ${device.name ?: "Unknown"} [${device.address}]")
            bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, gattCallback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to device ${device.address}: ${e.message}")
            scheduleReconnect()
        }
    }

    /**
     * Disconnects from the LoRa Gateway.
     */
    @SuppressLint("MissingPermission")
    fun disconnect() {
        autoReconnectJob?.cancel()
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error disconnecting GATT: ${e.message}")
        }
        bluetoothGatt = null
        loraCharacteristic = null
        _isGatewayConnected.value = false
        _gatewayDeviceName.value = null
    }

    /**
     * Serializes and transmits an outgoing NetworkPacket over the ESP32 LoRa Gateway BLE bridge.
     * Binary Format:
     * [Header: 1B Type | 2B MsgID | 1B HopLimit | 2B SourceID] + [GPS: 8B Lat/Long] + [Text Payload]
     */
    fun sendPacket(packet: NetworkPacket, lat: Double = 0.0, lon: Double = 0.0): Boolean {
        if (!_isGatewayConnected.value || loraCharacteristic == null || bluetoothGatt == null) {
            Log.w(TAG, "LoRa Gateway not connected. Cannot transmit packet ${packet.packetId}")
            return false
        }

        try {
            val type = if (packet.isAlert || packet.alertPriority == AlertPriority.CRITICAL_DISTRESS) {
                TYPE_SOS_ALERT
            } else {
                TYPE_TEXT_DISPATCH
            }

            val msgId = (packet.packetId.hashCode() and 0xFFFF).toShort()
            val hopLimit: Byte = packet.ttl.coerceIn(1, 10).toByte()
            val sourceId = (packet.senderId.hashCode() and 0xFFFF).toShort()

            val textBytes = packet.text.toByteArray(Charsets.UTF_8)
            val maxTextBytes = (MAX_LORA_PAYLOAD_SIZE - 14).coerceAtLeast(0)
            val trimmedTextBytes = if (textBytes.size > maxTextBytes) textBytes.copyOf(maxTextBytes) else textBytes

            val totalSize = 6 + 8 + trimmedTextBytes.size
            val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)

            // Header (6 Bytes)
            buffer.put(type)
            buffer.putShort(msgId)
            buffer.put(hopLimit)
            buffer.putShort(sourceId)

            // GPS Coordinates (8 Bytes: Float32 Lat + Float32 Lon)
            buffer.putFloat(lat.toFloat())
            buffer.putFloat(lon.toFloat())

            // Text Payload
            buffer.put(trimmedTextBytes)

            val payload = buffer.array()
            enqueueWrite(payload)
            Log.i(TAG, "Enqueued LoRa payload for ${packet.packetId} ($totalSize bytes, Type=$type, Lat=$lat, Lon=$lon)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to serialize LoRa packet: ${e.message}")
            return false
        }
    }

    /**
     * Sends a 1-byte raw SOS distress trigger directly to the LoRa Gateway for instantaneous transmission.
     */
    fun sendFastSosByte(sourceIdShort: Short = 0x01): Boolean {
        if (!_isGatewayConnected.value) return false
        val buffer = ByteBuffer.allocate(6).order(ByteOrder.BIG_ENDIAN)
        buffer.put(TYPE_SOS_ALERT)
        buffer.putShort(messageSequence.getAndIncrement().toShort())
        buffer.put(0x05.toByte()) // Hop limit 5
        buffer.putShort(sourceIdShort)
        return enqueueWrite(buffer.array())
    }

    private fun enqueueWrite(data: ByteArray): Boolean {
        writeQueue.offer(data)
        processNextWrite()
        return true
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun processNextWrite() {
        if (isWriting) return
        val gatt = bluetoothGatt ?: return
        val characteristic = loraCharacteristic ?: return

        val nextPayload = writeQueue.poll() ?: return
        isWriting = true

        try {
            characteristic.value = nextPayload
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            val success = gatt.writeCharacteristic(characteristic)
            if (!success) {
                Log.w(TAG, "GATT writeCharacteristic returned false, retrying with DEFAULT write type")
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                gatt.writeCharacteristic(characteristic)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to LoRa characteristic: ${e.message}")
            isWriting = false
        }
    }

    /**
     * Parses incoming LoRa binary packets received via BLE notification from the ESP32.
     * [Header: 1B Type | 2B MsgID | 1B HopLimit | 2B SourceID] + [GPS: 8B Lat/Long] + [Text Payload]
     */
    private fun handleIncomingBleBytes(data: ByteArray) {
        if (data.isEmpty()) return

        try {
            if (data.size < 6) {
                // Short/1-byte SOS signal packet
                if (data[0] == TYPE_SOS_ALERT) {
                    val alertPacket = NetworkPacket(
                        packetId = "LORA_SOS_${System.currentTimeMillis()}",
                        senderId = "LORA_NODE_EXT",
                        senderCallsign = "LORA-SOS-GATEWAY",
                        text = "आपातकालीन चेतावनी: LoRa गेटवे से सीधा संकट संकेत प्राप्त हुआ!",
                        languageCode = "hi",
                        isAlert = true,
                        alertPriority = AlertPriority.CRITICAL_DISTRESS,
                        channelFreq = "868.0 MHz (LoRa SX1278)"
                    )
                    Log.i(TAG, "Received 1-byte raw LoRa SOS alert!")
                    onPacketReceived(alertPacket)
                }
                return
            }

            val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
            val type = buffer.get()
            val msgId = buffer.short.toInt() and 0xFFFF
            val hopLimit = buffer.get().toInt() and 0xFF
            val sourceId = buffer.short.toInt() and 0xFFFF

            var lat = 0.0
            var lon = 0.0
            var text = ""

            if (data.size >= 14) {
                lat = buffer.float.toDouble()
                lon = buffer.float.toDouble()

                val textLength = data.size - 14
                if (textLength > 0) {
                    val textBytes = ByteArray(textLength)
                    buffer.get(textBytes)
                    text = String(textBytes, Charsets.UTF_8).trim()
                }
            } else {
                val remaining = data.size - 6
                if (remaining > 0) {
                    val textBytes = ByteArray(remaining)
                    buffer.get(textBytes)
                    text = String(textBytes, Charsets.UTF_8).trim()
                }
            }

            val isSos = (type == TYPE_SOS_ALERT) ||
                    text.contains("SOS", ignoreCase = true) ||
                    text.contains("आपातकाल") ||
                    text.contains("Emergency", ignoreCase = true)

            val displayText = if (text.isNotBlank()) {
                if ((lat != 0.0 || lon != 0.0) && !text.contains("GPS:")) {
                    "$text [GPS: ${String.format("%.5f, %.5f", lat, lon)}]"
                } else {
                    text
                }
            } else {
                if (isSos) "Emergency LoRa SOS Beacon [Node #$sourceId]" else "LoRa Mesh Ping"
            }

            val packet = NetworkPacket(
                packetId = "LORA_${sourceId}_${msgId}",
                senderId = "LORA_NODE_$sourceId",
                senderCallsign = if (isSos) "LORA-SOS-$sourceId" else "LORA-$sourceId",
                text = displayText,
                languageCode = "en",
                isAlert = isSos,
                alertPriority = if (isSos) AlertPriority.CRITICAL_DISTRESS else AlertPriority.ROUTINE,
                channelFreq = "868.0 MHz (LoRa SX1278)",
                ttl = hopLimit,
                relayHops = (3 - hopLimit).coerceAtLeast(0)
            )

            Log.i(TAG, "Parsed incoming LoRa packet: ${packet.packetId} (Type=$type, Source=$sourceId, Text='${packet.text}')")
            onPacketReceived(packet)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse incoming LoRa binary payload: ${e.message}")
        }
    }

    private fun scheduleReconnect() {
        val device = targetDevice ?: return
        autoReconnectJob?.cancel()
        autoReconnectJob = scope.launch(Dispatchers.IO) {
            delay(4000L)
            if (!_isGatewayConnected.value && isActive) {
                Log.i(TAG, "Attempting auto-reconnect to ESP32 LoRa Gateway at ${device.address}...")
                connectToDevice(device)
            }
        }
    }
}
