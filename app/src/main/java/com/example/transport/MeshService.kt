package com.example.transport

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.VoxBridgeApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MeshService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private val binder = MeshBinder()
    
    // We'll expose the transport through a static reference for simplicity in this demo,
    // but the Service manages its lifecycle.
    companion object {
        var transportInstance: TacticalMeshTransport? = null
            private set
            
        private const val CHANNEL_ID = "mesh_service_channel"
        private const val MESSAGE_CHANNEL_ID = "mesh_message_channel"
        private const val NOTIFICATION_ID = 101
        private const val INCOMING_MSG_ID = 102
    }

    override fun onCreate() {
        super.onCreate()
        if (transportInstance == null) {
            transportInstance = TacticalMeshTransport(applicationContext, serviceScope)
        }
        createNotificationChannel()
        startIncomingPacketObserver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()
        return START_STICKY
    }

    private fun startForegroundService() {
        val notification = createNotification("VoxBridge is active and scanning")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VoxBridge Mesh")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync) // Placeholder icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            
            val meshChannel = NotificationChannel(
                CHANNEL_ID,
                "Mesh Network Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the mesh network active in the background"
            }
            
            val msgChannel = NotificationChannel(
                MESSAGE_CHANNEL_ID,
                "Incoming Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when a peer sends a message while the app is closed"
                enableLights(true)
                enableVibration(true)
            }

            manager.createNotificationChannel(meshChannel)
            manager.createNotificationChannel(msgChannel)
        }
    }

    private fun startIncomingPacketObserver() {
        serviceScope.launch {
            transportInstance?.incomingPackets?.collect { packet ->
                // Only show notification if app is in background
                if (!VoxBridgeApplication.isAppInForeground) {
                    showIncomingMessageNotification(packet)
                }
            }
        }
    }

    private fun showIncomingMessageNotification(packet: NetworkPacket) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, MESSAGE_CHANNEL_ID)
            .setContentTitle(packet.senderCallsign)
            .setContentText(packet.text)
            .setSmallIcon(R.drawable.stat_notify_chat)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .setDefaults(Notification.DEFAULT_ALL)
            .build()

        manager.notify(INCOMING_MSG_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    inner class MeshBinder : Binder() {
        fun getService(): MeshService = this@MeshService
        fun getTransport(): TransportLayer? = transportInstance
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        transportInstance = null
    }
}
