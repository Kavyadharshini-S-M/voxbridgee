package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-frequency near-ultrasonic acoustic chirp transceiver (16.5 kHz - 17.5 kHz).
 *
 * Transmits and receives compact connection strings (e.g. IP, Port, Callsign) through
 * standard device speakers and microphones without requiring prior Bluetooth or Wi-Fi pairing.
 *
 * Encoding:
 * - Sampling rate: 44,100 Hz (16-bit PCM mono)
 * - Frequency band: 16,500 Hz to 17,500 Hz
 * - Preamble / Sync tone: 16,500 Hz + 17,500 Hz
 * - 16 data frequencies representing 4-bit nibbles (0 to 15) spaced by 50 Hz
 * - Tapered raised-cosine windowing to prevent audible speaker clicks
 * - Framing: SYNC -> LENGTH -> NIBBLE_DATA -> CHECKSUM -> END_SYNC
 */
class UltrasonicChirpTransceiver(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val TAG = "UltrasonicChirp"

    // FSK Parameters: 14.0 kHz to 16.5 kHz band (optimal acoustic transmission across all phone speakers & mics)
    private val sampleRate = 44100
    private val symbolDurationMs = 55 // Duration of each nibble symbol
    private val samplesPerSymbol = (sampleRate * (symbolDurationMs / 1000f)).toInt()

    private val syncFreq1 = 14000.0 // Sync burst 1
    private val syncFreq2 = 16500.0 // Sync burst 2
    private val endFreq = 16200.0   // Frame end marker

    // 16 Data Frequencies for 4-bit nibbles (14,500 Hz to 16,000 Hz in 100 Hz increments)
    private val baseDataFreq = 14500.0
    private val freqStep = 100.0

    private val _isEmitting = MutableStateFlow(false)
    val isEmitting: StateFlow<Boolean> = _isEmitting.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _detectedPayload = MutableStateFlow<String?>(null)
    val detectedPayload: StateFlow<String?> = _detectedPayload.asStateFlow()

    private val _ultrasonicSignalEnergy = MutableStateFlow(0f)
    val ultrasonicSignalEnergy: StateFlow<Float> = _ultrasonicSignalEnergy.asStateFlow()

    private var listeningJob: Job? = null
    private var audioRecord: AudioRecord? = null

    /**
     * Emits a near-inaudible data chirp over the speaker containing the connection string.
     */
    fun emitChirp(payload: String, onComplete: (() -> Unit)? = null) {
        if (_isEmitting.value) return

        scope.launch(Dispatchers.Default) {
            try {
                _isEmitting.value = true
                Log.i(TAG, "Emitting acoustic pairing chirp payload: $payload")

                val bytes = payload.toByteArray(Charsets.UTF_8)
                val nibbles = mutableListOf<Int>()

                // Payload length nibbles (1 byte = 2 nibbles)
                nibbles.add((bytes.size shr 4) and 0x0F)
                nibbles.add(bytes.size and 0x0F)

                // Data nibbles
                var checksum = bytes.size
                for (b in bytes) {
                    val intVal = b.toInt() and 0xFF
                    val highNibble = (intVal shr 4) and 0x0F
                    val lowNibble = intVal and 0x0F
                    nibbles.add(highNibble)
                    nibbles.add(lowNibble)
                    checksum = (checksum + intVal) and 0xFF
                }

                // Checksum nibbles
                nibbles.add((checksum shr 4) and 0x0F)
                nibbles.add(checksum and 0x0F)

                // Total symbols = 2 (sync) + nibbles.size + 1 (end)
                val totalSymbols = 2 + nibbles.size + 1
                val totalSamples = totalSymbols * samplesPerSymbol
                val pcmBuffer = ShortArray(totalSamples)
                var sampleOffset = 0

                // 1. Write Preamble Sync Symbol (Dual Tone Burst)
                writeTone(pcmBuffer, sampleOffset, syncFreq1, samplesPerSymbol, amplitude = 0.90)
                sampleOffset += samplesPerSymbol
                writeTone(pcmBuffer, sampleOffset, syncFreq2, samplesPerSymbol, amplitude = 0.90)
                sampleOffset += samplesPerSymbol

                // 2. Write Data Nibbles
                for (nibble in nibbles) {
                    val freq = baseDataFreq + (nibble * freqStep)
                    writeTone(pcmBuffer, sampleOffset, freq, samplesPerSymbol, amplitude = 0.85)
                    sampleOffset += samplesPerSymbol
                }

                // 3. Write End Marker
                writeTone(pcmBuffer, sampleOffset, endFreq, samplesPerSymbol, amplitude = 0.90)

                // Play buffer via AudioTrack
                playAudioTrack(pcmBuffer)
            } catch (e: Exception) {
                Log.e(TAG, "Error emitting chirp: ${e.message}", e)
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
        amplitude: Double = 0.85
    ) {
        val maxAmp = (Short.MAX_VALUE * amplitude).toInt()
        val twoPiF = 2.0 * PI * freq / sampleRate

        for (i in 0 until numSamples) {
            // Raised-cosine window for smooth envelope to prevent speaker pop
            val window = 0.5 * (1.0 - cos(2.0 * PI * i / (numSamples - 1)))
            val sampleVal = (sin(twoPiF * i) * window * maxAmp).toInt()
            if (offset + i < buffer.size) {
                buffer[offset + i] = sampleVal.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
    }

    private fun playAudioTrack(buffer: ShortArray) {
        try {
            val audioTrack = AudioTrack.Builder()
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

            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()

            val playDurationMs = ((buffer.size.toDouble() / sampleRate) * 1000).toLong() + 150
            Thread.sleep(playDurationMs)

            audioTrack.stop()
            audioTrack.release()
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack play exception: ${e.message}")
        }
    }

    /**
     * Starts listening for nearby acoustic pairing chirps via the microphone.
     */
    fun startListening(onPayloadDetected: (String) -> Unit) {
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
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.UNPROCESSED,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    audioRecord = AudioRecord(
                        MediaRecorder.AudioSource.DEFAULT,
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize
                    )
                }

                audioRecord?.startRecording()
                _isListening.value = true
                Log.i(TAG, "Acoustic Chirp receiver listening on mic...")

                val readBuffer = ShortArray(samplesPerSymbol)
                var inFrame = false
                var syncStep = 0
                val receivedNibbles = mutableListOf<Int>()
                var expectedNibblesCount = -1
                var silenceFrames = 0

                while (isActive && _isListening.value) {
                    // Suppress receiver during local emission
                    if (_isEmitting.value) {
                        delay(100)
                        continue
                    }

                    val read = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: 0
                    if (read <= 0) continue

                    val backgroundEnergy = goertzelMagnitude(readBuffer, read, 12000.0, sampleRate) + 20.0
                    val sync1Energy = goertzelMagnitude(readBuffer, read, syncFreq1, sampleRate)
                    val sync2Energy = goertzelMagnitude(readBuffer, read, syncFreq2, sampleRate)
                    val maxEnergy = sync1Energy.coerceAtLeast(sync2Energy)
                    _ultrasonicSignalEnergy.value = (maxEnergy / 2000.0).toFloat().coerceIn(0f, 1f)

                    if (!inFrame) {
                        val isSync1 = sync1Energy > (backgroundEnergy * 1.8) && sync1Energy > 80.0
                        val isSync2 = sync2Energy > (backgroundEnergy * 1.8) && sync2Energy > 80.0

                        if (syncStep == 0 && isSync1) {
                            syncStep = 1
                        } else if (syncStep == 1 && isSync2) {
                            inFrame = true
                            syncStep = 0
                            receivedNibbles.clear()
                            expectedNibblesCount = -1
                            silenceFrames = 0
                            Log.d(TAG, "Acoustic pairing preamble sync detected!")
                        } else if (!isSync1) {
                            syncStep = 0
                        }
                    } else {
                        // Inside Frame: Detect dominant data frequency
                        var bestNibble = -1
                        var highestMag = backgroundEnergy * 1.4

                        for (n in 0..15) {
                            val freq = baseDataFreq + (n * freqStep)
                            val mag = goertzelMagnitude(readBuffer, read, freq, sampleRate)
                            if (mag > highestMag) {
                                highestMag = mag
                                bestNibble = n
                            }
                        }

                        val endMag = goertzelMagnitude(readBuffer, read, endFreq, sampleRate)

                        if (endMag > (backgroundEnergy * 1.8) || (expectedNibblesCount > 0 && receivedNibbles.size >= expectedNibblesCount + 4)) {
                            val decoded = decodePayload(receivedNibbles)
                            if (decoded != null) {
                                Log.i(TAG, "Successfully decoded acoustic payload: $decoded")
                                _detectedPayload.value = decoded
                                withContext(Dispatchers.Main) {
                                    onPayloadDetected(decoded)
                                }
                            }
                            inFrame = false
                            receivedNibbles.clear()
                        } else if (bestNibble >= 0) {
                            receivedNibbles.add(bestNibble)
                            silenceFrames = 0

                            if (receivedNibbles.size == 2 && expectedNibblesCount == -1) {
                                val len = (receivedNibbles[0] shl 4) or receivedNibbles[1]
                                expectedNibblesCount = len * 2
                            }
                        } else {
                            silenceFrames++
                            if (silenceFrames > 8) {
                                inFrame = false
                                receivedNibbles.clear()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "AudioRecord listening error: ${e.message}")
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
        _ultrasonicSignalEnergy.value = 0f
    }

    fun clearDetectedPayload() {
        _detectedPayload.value = null
    }

    private fun decodePayload(nibbles: List<Int>): String? {
        try {
            if (nibbles.size < 4) return null
            val length = (nibbles[0] shl 4) or nibbles[1]
            if (length <= 0 || length > 120) return null
            val totalExpected = 2 + (length * 2) + 2
            if (nibbles.size < totalExpected) return null

            val bytes = ByteArray(length)
            var computedChecksum = length
            var nibbleIdx = 2

            for (i in 0 until length) {
                val b = (nibbles[nibbleIdx] shl 4) or nibbles[nibbleIdx + 1]
                bytes[i] = b.toByte()
                computedChecksum = (computedChecksum + b) and 0xFF
                nibbleIdx += 2
            }

            return String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding nibbles: ${e.message}")
            return null
        }
    }

    /**
     * Goertzel Algorithm for single-frequency energy extraction.
     */
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
}
