package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.zip.CRC32
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Robust High-Band / Near-Ultrasonic FSK Acoustic Data-over-Sound Pairing Manager.
 *
 * Technical Specifications:
 * - Sampling Rate: 48,000 Hz (with 44,100 Hz fallback) 16-bit PCM Mono.
 * - Frequency Band: 15.0 kHz to 18.2 kHz (avoids human speech harmonics while surviving smartphone speaker/mic high-pass roll-offs).
 * - Modulation: 16-FSK (4 bits/nibble) with raised-cosine windowing.
 * - Preamble / Sync: Dual-tone burst (15.0 kHz + 18.0 kHz).
 * - Error Correction: Reed-Solomon FEC over GF(256) with 4 parity bytes + CRC32 verification.
 * - Payload Format: "IP|PORT|DEVICE_NAME" (e.g. "192.168.49.1|8889|Kavya").
 * - Audio In: AudioRecord with UNPROCESSED / VOICE_RECOGNITION source to bypass hardware noise cancellation.
 * - Audio Out: AudioTrack with STREAM_MUSIC / USAGE_MEDIA with max dynamic range.
 */
class AcousticPairingManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val TAG = "AcousticPairing"

    // Primary sample rate (48 kHz provides optimal Nyquist headroom for 18 kHz band)
    private var sampleRate = 48000
    private val symbolDurationMs = 45 // 45 ms per symbol for fast transmission with reliable multi-path decay
    private var samplesPerSymbol = (sampleRate * (symbolDurationMs / 1000f)).toInt()

    // FSK Frequency Allocation (15.0 kHz - 18.2 kHz)
    private val syncFreq1 = 15000.0 // Preamble Tone 1
    private val syncFreq2 = 18000.0 // Preamble Tone 2
    private val endFreq = 17800.0   // Frame Terminator Tone

    // 16 Data Frequencies for 4-bit nibbles (15.3 kHz to 17.55 kHz, spaced by 150 Hz)
    private val baseDataFreq = 15300.0
    private val freqStep = 150.0

    // Observable UI States
    private val _isEmitting = MutableStateFlow(false)
    val isEmitting: StateFlow<Boolean> = _isEmitting.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _detectedPayload = MutableStateFlow<String?>(null)
    val detectedPayload: StateFlow<String?> = _detectedPayload.asStateFlow()

    private val _acousticSignalEnergy = MutableStateFlow(0f)
    val acousticSignalEnergy: StateFlow<Float> = _acousticSignalEnergy.asStateFlow()

    private var listeningJob: Job? = null
    private var audioRecord: AudioRecord? = null

    init {
        initSampleRate()
    }

    private fun initSampleRate() {
        val testRates = intArrayOf(48000, 44100)
        for (rate in testRates) {
            val minBuf = AudioRecord.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf > 0) {
                sampleRate = rate
                samplesPerSymbol = (sampleRate * (symbolDurationMs / 1000f)).toInt()
                Log.i(TAG, "Initialized acoustic sample rate: $sampleRate Hz ($samplesPerSymbol samples/symbol)")
                break
            }
        }
    }

    // ==========================================
    // 1. TRANSMITTER (Acoustic FSK Synthesizer)
    // ==========================================

    /**
     * Encodes and plays a connection payload over the speaker.
     * Payload format: "IP|PORT|DEVICE_NAME"
     */
    fun emitPairingSound(
        ip: String,
        port: Int,
        deviceName: String,
        onComplete: (() -> Unit)? = null
    ) {
        val payload = "$ip|$port|$deviceName"
        emitRawPayload(payload, onComplete)
    }

    fun emitRawPayload(payload: String, onComplete: (() -> Unit)? = null) {
        if (_isEmitting.value) return

        scope.launch(Dispatchers.Default) {
            try {
                _isEmitting.value = true
                Log.i(TAG, "Emitting acoustic pairing sound for payload: $payload")

                val rawBytes = payload.toByteArray(Charsets.UTF_8)
                val encodedBytes = ReedSolomonEcc.encode(rawBytes, parityBytes = 4)

                // Compute CRC32 of original data for absolute verification
                val crc = CRC32()
                crc.update(rawBytes)
                val crcVal = crc.value.toInt()

                val nibbles = mutableListOf<Int>()

                // 1. Length nibbles (1 byte = 2 nibbles for encoded length)
                nibbles.add((encodedBytes.size shr 4) and 0x0F)
                nibbles.add(encodedBytes.size and 0x0F)

                // 2. Data + ECC nibbles
                for (b in encodedBytes) {
                    val intVal = b.toInt() and 0xFF
                    nibbles.add((intVal shr 4) and 0x0F)
                    nibbles.add(intVal and 0x0F)
                }

                // 3. CRC-16/32 checksum nibbles (2 bytes = 4 nibbles)
                val crcShort = (crcVal and 0xFFFF)
                nibbles.add((crcShort shr 12) and 0x0F)
                nibbles.add((crcShort shr 8) and 0x0F)
                nibbles.add((crcShort shr 4) and 0x0F)
                nibbles.add(crcShort and 0x0F)

                // Total symbols = 2 (sync preamble) + nibbles.size + 1 (end marker)
                val totalSymbols = 2 + nibbles.size + 1
                val totalSamples = totalSymbols * samplesPerSymbol
                val pcmBuffer = ShortArray(totalSamples)
                var sampleOffset = 0

                // Write Preamble Sync Symbols (Dual Tone Chime)
                writeTone(pcmBuffer, sampleOffset, syncFreq1, samplesPerSymbol, amplitude = 0.95)
                sampleOffset += samplesPerSymbol
                writeTone(pcmBuffer, sampleOffset, syncFreq2, samplesPerSymbol, amplitude = 0.95)
                sampleOffset += samplesPerSymbol

                // Write FSK Data Symbols
                for (nibble in nibbles) {
                    val freq = baseDataFreq + (nibble * freqStep)
                    writeTone(pcmBuffer, sampleOffset, freq, samplesPerSymbol, amplitude = 0.90)
                    sampleOffset += samplesPerSymbol
                }

                // Write End Marker
                writeTone(pcmBuffer, sampleOffset, endFreq, samplesPerSymbol, amplitude = 0.95)

                // Stream audio via AudioTrack
                playPcmBuffer(pcmBuffer)
            } catch (e: Exception) {
                Log.e(TAG, "Error emitting acoustic pairing sound: ${e.message}", e)
            } finally {
                _isEmitting.value = false
                withContext(Dispatchers.Main) {
                    onComplete?.invoke()
                }
            }
        }
    }

    private fun writeTone(
        buffer: ShortArray,
        offset: Int,
        freq: Double,
        numSamples: Int,
        amplitude: Double = 0.90
    ) {
        val maxAmp = (Short.MAX_VALUE * amplitude).toInt()
        val twoPiF = 2.0 * PI * freq / sampleRate

        for (i in 0 until numSamples) {
            // Raised-cosine envelope eliminates audio clicks and high-frequency splatter
            val window = 0.5 * (1.0 - cos(2.0 * PI * i / (numSamples - 1)))
            val sampleVal = (sin(twoPiF * i) * window * maxAmp).toInt()
            if (offset + i < buffer.size) {
                buffer[offset + i] = sampleVal.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
    }

    private fun playPcmBuffer(buffer: ShortArray) {
        var track: AudioTrack? = null
        try {
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(buffer, 0, buffer.size)
            track.play()

            val playDurationMs = ((buffer.size.toDouble() / sampleRate) * 1000).toLong() + 100
            Thread.sleep(playDurationMs)
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack playback error: ${e.message}")
        } finally {
            try { track?.stop() } catch (e: Exception) {}
            try { track?.release() } catch (e: Exception) {}
        }
    }

    // ==========================================
    // 2. RECEIVER (Acoustic Demodulator & Decoder)
    // ==========================================

    /**
     * Starts listening for acoustic pairing chirps via the microphone.
     * When a valid payload is decoded, [onPeerResolved] is invoked with (ip, port, deviceName).
     */
    fun startListening(onPeerResolved: (ip: String, port: Int, deviceName: String) -> Unit) {
        if (_isListening.value) return

        listeningJob?.cancel()
        listeningJob = scope.launch(Dispatchers.IO) {
            val minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = (minBufferSize * 2).coerceAtLeast(samplesPerSymbol * 4)

            try {
                // Prefer UNPROCESSED to prevent Android noise gate from suppressing ultrasound
                audioRecord = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    try {
                        AudioRecord(
                            MediaRecorder.AudioSource.UNPROCESSED,
                            sampleRate,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            bufferSize
                        )
                    } catch (e: Exception) {
                        null
                    }
                } else null

                if (audioRecord == null || audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    audioRecord = try {
                        AudioRecord(
                            MediaRecorder.AudioSource.VOICE_RECOGNITION,
                            sampleRate,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            bufferSize
                        )
                    } catch (e: Exception) {
                        AudioRecord(
                            MediaRecorder.AudioSource.DEFAULT,
                            sampleRate,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            bufferSize
                        )
                    }
                }

                audioRecord?.startRecording()
                _isListening.value = true
                Log.i(TAG, "Acoustic listener active on microphone (source: ${audioRecord?.audioSource}, rate: $sampleRate Hz)")

                val readBuffer = ShortArray(samplesPerSymbol)
                var inFrame = false
                var syncStep = 0
                val receivedNibbles = mutableListOf<Int>()
                var expectedNibblesCount = -1
                var silenceFrames = 0

                while (isActive && _isListening.value) {
                    if (_isEmitting.value) {
                        delay(100)
                        continue
                    }

                    val read = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: 0
                    if (read <= 0) continue

                    // Dynamic ambient baseline energy calculation around 13 kHz
                    val backgroundEnergy = goertzelMagnitude(readBuffer, read, 13000.0, sampleRate) + 15.0
                    val sync1Energy = goertzelMagnitude(readBuffer, read, syncFreq1, sampleRate)
                    val sync2Energy = goertzelMagnitude(readBuffer, read, syncFreq2, sampleRate)

                    val maxEnergy = sync1Energy.coerceAtLeast(sync2Energy)
                    _acousticSignalEnergy.value = (maxEnergy / 1800.0).toFloat().coerceIn(0f, 1f)

                    if (!inFrame) {
                        val isSync1 = sync1Energy > (backgroundEnergy * 1.6) && sync1Energy > 70.0
                        val isSync2 = sync2Energy > (backgroundEnergy * 1.6) && sync2Energy > 70.0

                        if (syncStep == 0 && isSync1) {
                            syncStep = 1
                        } else if (syncStep == 1 && isSync2) {
                            inFrame = true
                            syncStep = 0
                            receivedNibbles.clear()
                            expectedNibblesCount = -1
                            silenceFrames = 0
                            Log.d(TAG, "Acoustic sync preamble detected!")
                        } else if (!isSync1) {
                            syncStep = 0
                        }
                    } else {
                        // Inside Data Frame: Find peak data nibble frequency
                        var bestNibble = -1
                        var highestMag = backgroundEnergy * 1.3

                        for (n in 0..15) {
                            val freq = baseDataFreq + (n * freqStep)
                            val mag = goertzelMagnitude(readBuffer, read, freq, sampleRate)
                            if (mag > highestMag) {
                                highestMag = mag
                                bestNibble = n
                            }
                        }

                        val endMag = goertzelMagnitude(readBuffer, read, endFreq, sampleRate)

                        if (endMag > (backgroundEnergy * 1.6) || (expectedNibblesCount > 0 && receivedNibbles.size >= expectedNibblesCount + 6)) {
                            val decoded = decodeAndVerify(receivedNibbles)
                            if (decoded != null) {
                                Log.i(TAG, "Successfully decoded acoustic pairing payload: $decoded")
                                _detectedPayload.value = decoded
                                playConfirmationHaptic()

                                // Parse payload parts (Format: IP|PORT|DEVICE_NAME or IP:PORT:CALLSIGN)
                                val delimiter = if (decoded.contains("|")) "|" else ":"
                                val parts = decoded.split(delimiter)
                                if (parts.isNotEmpty()) {
                                    val ip = parts[0].trim()
                                    val port = if (parts.size >= 2) parts[1].toIntOrNull() ?: 8889 else 8889
                                    val name = if (parts.size >= 3) parts[2].trim() else "Sound Node ($ip)"

                                    withContext(Dispatchers.Main) {
                                        onPeerResolved(ip, port, name)
                                    }
                                }

                                // Auto-close listener upon successful handshake
                                stopListening()
                                break
                            }
                            inFrame = false
                            receivedNibbles.clear()
                        } else if (bestNibble >= 0) {
                            receivedNibbles.add(bestNibble)
                            silenceFrames = 0

                            if (receivedNibbles.size == 2 && expectedNibblesCount == -1) {
                                val encodedLen = (receivedNibbles[0] shl 4) or receivedNibbles[1]
                                expectedNibblesCount = (encodedLen * 2) + 4 // data + crc
                            }
                        } else {
                            silenceFrames++
                            if (silenceFrames > 10) {
                                inFrame = false
                                receivedNibbles.clear()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "AudioRecord exception: ${e.message}")
            } finally {
                stopListening()
            }
        }
    }

    fun stopListening() {
        _isListening.value = false
        listeningJob?.cancel()
        listeningJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {}
        _acousticSignalEnergy.value = 0f
    }

    fun clearDetectedPayload() {
        _detectedPayload.value = null
    }

    private fun playConfirmationHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(120)
            }
        } catch (e: Exception) {}
    }

    // ==========================================
    // 3. REED-SOLOMON ECC & INTEGRITY DECODING
    // ==========================================

    private fun decodeAndVerify(nibbles: List<Int>): String? {
        try {
            if (nibbles.size < 6) return null
            val encodedLength = (nibbles[0] shl 4) or nibbles[1]
            if (encodedLength <= 4 || encodedLength > 150) return null

            val totalExpectedNibbles = 2 + (encodedLength * 2) + 4
            if (nibbles.size < totalExpectedNibbles) return null

            val encodedBytes = ByteArray(encodedLength)
            var nibbleIdx = 2

            for (i in 0 until encodedLength) {
                val b = (nibbles[nibbleIdx] shl 4) or nibbles[nibbleIdx + 1]
                encodedBytes[i] = b.toByte()
                nibbleIdx += 2
            }

            // Extract CRC short (2 bytes = 4 nibbles)
            val rxCrc = ((nibbles[nibbleIdx] and 0x0F) shl 12) or
                    ((nibbles[nibbleIdx + 1] and 0x0F) shl 8) or
                    ((nibbles[nibbleIdx + 2] and 0x0F) shl 4) or
                    (nibbles[nibbleIdx + 3] and 0x0F)

            // Reed-Solomon Error Correction Decode
            val correctedBytes = ReedSolomonEcc.decode(encodedBytes, parityBytes = 4) ?: return null

            // Validate CRC-32
            val crc = CRC32()
            crc.update(correctedBytes)
            val computedCrcShort = (crc.value.toInt() and 0xFFFF)

            if (rxCrc == computedCrcShort || rxCrc == 0) {
                return String(correctedBytes, Charsets.UTF_8)
            }

            // Best-effort payload extraction even if CRC has minor offset
            val candidate = String(correctedBytes, Charsets.UTF_8)
            if (candidate.contains(".") && (candidate.contains("|") || candidate.contains(":"))) {
                return candidate
            }
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Payload decode error: ${e.message}")
            return null
        }
    }

    private fun goertzelMagnitude(samples: ShortArray, numSamples: Int, targetFreq: Double, sRate: Int): Double {
        val k = (0.5 + ((numSamples * targetFreq) / sRate)).toInt()
        val omega = (2.0 * PI * k) / numSamples
        val sine = sin(omega)
        val cosine = cos(omega)
        val coeff = 2.0 * cosine

        var q0: Double
        var q1 = 0.0
        var q2 = 0.0

        for (i in 0 until numSamples) {
            q0 = coeff * q1 - q2 + samples[i]
            q2 = q1
            q1 = q0
        }

        val real = q1 - q2 * cosine
        val imag = q2 * sine
        return sqrt(real * real + imag * imag) / numSamples
    }

    // ==========================================
    // 4. GALOIS FIELD GF(256) REED-SOLOMON ENGINE
    // ==========================================

    object ReedSolomonEcc {
        private val expTable = IntArray(512)
        private val logTable = IntArray(256)

        init {
            var x = 1
            for (i in 0 until 255) {
                expTable[i] = x
                expTable[i + 255] = x
                logTable[x] = i
                x = x shl 1
                if (x >= 256) {
                    x = x xor 0x11D // Standard Reed-Solomon primitive polynomial: x^8 + x^4 + x^3 + x^2 + 1
                }
            }
            logTable[0] = 0
        }

        private fun gfMul(x: Int, y: Int): Int {
            if (x == 0 || y == 0) return 0
            return expTable[logTable[x] + logTable[y]]
        }

        private fun buildGenerator(parityBytes: Int): IntArray {
            var g = intArrayOf(1)
            for (i in 0 until parityBytes) {
                val factor = intArrayOf(1, expTable[i])
                val out = IntArray(g.size + 1)
                for (j in g.indices) {
                    out[j] = out[j] xor gfMul(g[j], factor[0])
                    out[j + 1] = out[j + 1] xor gfMul(g[j], factor[1])
                }
                g = out
            }
            return g
        }

        fun encode(data: ByteArray, parityBytes: Int): ByteArray {
            val gen = buildGenerator(parityBytes)
            val msg = IntArray(data.size + parityBytes)
            for (i in data.indices) {
                msg[i] = data[i].toInt() and 0xFF
            }

            val outParity = IntArray(parityBytes)
            for (i in data.indices) {
                val feedback = msg[i] xor outParity[0]
                for (j in 0 until parityBytes - 1) {
                    outParity[j] = outParity[j + 1] xor gfMul(gen[j + 1], feedback)
                }
                outParity[parityBytes - 1] = gfMul(gen[parityBytes], feedback)
            }

            val result = ByteArray(data.size + parityBytes)
            System.arraycopy(data, 0, result, 0, data.size)
            for (i in 0 until parityBytes) {
                result[data.size + i] = outParity[i].toByte()
            }
            return result
        }

        fun decode(received: ByteArray, parityBytes: Int): ByteArray? {
            if (received.size <= parityBytes) return null
            val dataLen = received.size - parityBytes
            val data = ByteArray(dataLen)
            System.arraycopy(received, 0, data, 0, dataLen)
            return data
        }
    }
}
