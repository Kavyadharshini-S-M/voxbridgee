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

    fun getUserAvatar(): String {
        return prefs.getString("user_avatar", "🛡️") ?: "🛡️"
    }

    fun setUserAvatar(avatar: String) {
        prefs.edit().putString("user_avatar", avatar).apply()
    }

    fun isShakeToSosEnabled(): Boolean {
        return prefs.getBoolean("shake_to_sos_enabled", true)
    }

    fun setShakeToSosEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("shake_to_sos_enabled", enabled).apply()
    }

    fun getFontScale(): Float {
        val savedScale = prefs.getFloat("font_scale", -1f)
        if (savedScale > 0f) return savedScale
        return when (prefs.getString("font_size", "Normal")) {
            "Small" -> 0.85f
            "Large" -> 1.15f
            "Extra Large" -> 1.30f
            else -> 1.0f
        }
    }

    fun setFontScale(scale: Float) {
        val label = when {
            scale < 0.92f -> "Small"
            scale <= 1.05f -> "Normal"
            scale <= 1.22f -> "Large"
            else -> "Extra Large"
        }
        prefs.edit()
            .putFloat("font_scale", scale)
            .putString("font_size", label)
            .apply()
    }

    fun getFontSize(): String {
        return prefs.getString("font_size", "Normal") ?: "Normal"
    }

    fun setFontSize(size: String) {
        val scale = when (size) {
            "Small" -> 0.85f
            "Large" -> 1.15f
            "Extra Large" -> 1.30f
            else -> 1.0f
        }
        setFontScale(scale)
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
