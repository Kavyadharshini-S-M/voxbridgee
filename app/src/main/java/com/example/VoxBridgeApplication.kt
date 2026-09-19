package com.example

import android.app.Application
import android.content.Intent
import android.os.Build
import com.example.transport.MeshService

class VoxBridgeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startMeshServiceSafely()
    }

    fun startMeshServiceSafely() {
        try {
            val intent = Intent(this, MeshService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        var isAppInForeground: Boolean = false
    }
}
