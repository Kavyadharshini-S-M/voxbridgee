package com.example.audio

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * TacticalAlertManager: Handles multi-sensory dispatch across haptic vibration and camera torch strobe.
 *
 * Capabilities:
 * - High-priority / distress packets: SOS vibration pattern + 3 rapid camera LED strobes.
 * - Normal / routine packets: Short vibration pattern.
 */
class TacticalAlertManager(private val context: Context) {
    private val TAG = "TacticalAlertManager"

    private val cameraManager by lazy {
        try {
            context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        } catch (e: Throwable) {
            null
        }
    }

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    val shortPattern = longArrayOf(0, 100, 100, 100)
    val sosPattern = longArrayOf(0, 300, 100, 300, 100, 300)

    /**
     * Dispatches multi-sensory alert for incoming packets based on urgency.
     */
    fun dispatchIncomingAlert(isAlert: Boolean, scope: CoroutineScope) {
        if (isAlert) {
            triggerDistressAlert(scope)
        } else {
            triggerNormalAlert()
        }
    }

    /**
     * Short haptic alert for routine / normal voice and text messages.
     */
    fun triggerNormalAlert() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(shortPattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(shortPattern, -1)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Normal vibration error: ${e.message}")
        }
    }

    /**
     * SOS distress alert with SOS haptic pattern and 3 rapid camera LED strobe flashes.
     */
    fun triggerDistressAlert(scope: CoroutineScope) {
        // 1. SOS Haptic Pattern
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(sosPattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(sosPattern, -1)
            }
        } catch (e: Exception) {
            Log.w(TAG, "SOS vibration error: ${e.message}")
        }

        // 2. Camera LED Flashlight Strobe (3 pulses)
        triggerTorchStrobe(scope, count = 3)
    }

    /**
     * Pulses camera LED torch in rapid succession.
     */
    fun triggerTorchStrobe(scope: CoroutineScope, count: Int = 3) {
        scope.launch(Dispatchers.Default) {
            try {
                val cm = cameraManager ?: return@launch
                val cameraId = try {
                    cm.cameraIdList.firstOrNull { id ->
                        val characteristics = cm.getCameraCharacteristics(id)
                        val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                        val isBack = characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                        hasFlash && isBack
                    } ?: cm.cameraIdList.firstOrNull { id ->
                        cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    }
                } catch (e: Throwable) {
                    null
                } ?: return@launch

                for (i in 0 until count) {
                    try {
                        cm.setTorchMode(cameraId, true)
                        delay(120)
                        cm.setTorchMode(cameraId, false)
                        delay(100)
                    } catch (e: Throwable) {
                        Log.d(TAG, "Torch pulse $i notice: ${e.message}")
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Torch strobe error: ${e.message}")
            }
        }
    }
}
