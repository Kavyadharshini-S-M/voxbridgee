package com.example.data

import android.content.Context
import android.content.SharedPreferences

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("voxbridge_prefs", Context.MODE_PRIVATE)

    fun getCustomDeviceName(): String? {
        return prefs.getString("custom_device_name", null)
    }

    fun setCustomDeviceName(name: String) {
        prefs.edit().putString("custom_device_name", name).apply()
    }

    fun getHardwareKeyRemap(): String {
        return prefs.getString("hardware_key_remap", "volume_down") ?: "volume_down"
    }

    fun setHardwareKeyRemap(mode: String) {
        prefs.edit().putString("hardware_key_remap", mode).apply()
    }

    fun isAudioChirpEnabled(): Boolean {
        return prefs.getBoolean("audio_chirp_enabled", true)
    }

    fun setAudioChirpEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("audio_chirp_enabled", enabled).apply()
    }

    fun isFirstLaunchCompleted(): Boolean {
        return prefs.getBoolean("first_launch_completed", false)
    }

    fun setFirstLaunchCompleted(completed: Boolean) {
        prefs.edit().putBoolean("first_launch_completed", completed).apply()
    }

    fun getDefaultLanguage(): String {
        return prefs.getString("default_language", "hi") ?: "hi"
    }

    fun setDefaultLanguage(langCode: String) {
        prefs.edit().putString("default_language", langCode).apply()
    }

    fun isUltrasonicEnabled(): Boolean {
        return prefs.getBoolean("ultrasonic_pairing_enabled", true)
    }

    fun setUltrasonicEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("ultrasonic_pairing_enabled", enabled).apply()
    }

    fun isFieldModeEnabled(): Boolean {
        return prefs.getBoolean("field_mode_enabled", false)
    }

    fun setFieldModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("field_mode_enabled", enabled).apply()
    }

    fun getInstallUuid(): String {
        var uuid = prefs.getString("install_uuid", null)
        if (uuid.isNullOrBlank()) {
            uuid = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("install_uuid", uuid).apply()
        }
        return uuid
    }
}
