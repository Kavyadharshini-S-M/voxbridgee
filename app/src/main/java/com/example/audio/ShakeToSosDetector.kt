package com.example.audio

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.sqrt

/**
 * Accelerometer-based Shake-to-SOS detector.
 * Detects rapid, deliberate shaking of the phone to trigger emergency SOS broadcast.
 */
class ShakeToSosDetector(
    private val context: Context,
    private val onShakeDetected: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var isListening = false
    private var lastShakeTimestamp = 0L
    private var shakeCount = 0
    private var lastDirectionChangeTime = 0L

    companion object {
        private const val SHAKE_THRESHOLD_G_FORCE = 2.7f // ~26.5 m/s² total acceleration
        private const val SHAKE_WINDOW_MS = 1500L
        private const val SHAKE_MIN_INTERVAL_MS = 250L
        private const val REQUIRED_SHAKE_COUNT = 3
    }

    fun start() {
        if (isListening || accelerometer == null) return
        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        isListening = true
        Log.d("ShakeToSosDetector", "Shake-to-SOS sensor listener started")
    }

    fun stop() {
        if (!isListening) return
        sensorManager?.unregisterListener(this)
        isListening = false
        shakeCount = 0
        Log.d("ShakeToSosDetector", "Shake-to-SOS sensor listener stopped")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Calculate G-force
        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH
        val gForce = sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()

        val now = System.currentTimeMillis()

        if (gForce > SHAKE_THRESHOLD_G_FORCE) {
            if (now - lastDirectionChangeTime > SHAKE_MIN_INTERVAL_MS) {
                lastDirectionChangeTime = now

                if (now - lastShakeTimestamp > SHAKE_WINDOW_MS) {
                    shakeCount = 1
                } else {
                    shakeCount++
                }
                lastShakeTimestamp = now

                if (shakeCount >= REQUIRED_SHAKE_COUNT) {
                    shakeCount = 0
                    Log.i("ShakeToSosDetector", "🚨 Rapid shake sequence detected! Triggering SOS dispatch.")
                    onShakeDetected()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
