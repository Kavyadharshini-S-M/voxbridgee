package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Database entity representing packets queued for Store-and-Forward mesh and LoRa dispatch.
 */
@Entity(tableName = "pending_transmissions")
data class PendingTransmissionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packetId: String,
    val senderCallsign: String,
    val text: String,
    val languageCode: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isAlert: Boolean = false,
    val alertPriority: String = "ROUTINE",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val retryCount: Int = 0,
    val status: String = "QUEUED" // QUEUED, TRANSMITTING, SENT, FAILED
)
