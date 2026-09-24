package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingTransmissionDao {

    @Query("""
        SELECT * FROM pending_transmissions 
        WHERE status = 'QUEUED' 
        ORDER BY 
            CASE 
                WHEN alertPriority = 'CRITICAL_DISTRESS' THEN 0 
                WHEN isAlert = 1 THEN 1 
                WHEN alertPriority = 'URGENT' THEN 2 
                ELSE 3 
            END ASC, 
            timestamp ASC
    """)
    fun getPendingQueue(): Flow<List<PendingTransmissionEntity>>

    @Query("""
        SELECT * FROM pending_transmissions 
        WHERE status = 'QUEUED' 
        ORDER BY 
            CASE 
                WHEN alertPriority = 'CRITICAL_DISTRESS' THEN 0 
                WHEN isAlert = 1 THEN 1 
                WHEN alertPriority = 'URGENT' THEN 2 
                ELSE 3 
            END ASC, 
            timestamp ASC
    """)
    suspend fun getPendingQueueList(): List<PendingTransmissionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PendingTransmissionEntity): Long

    @Update
    suspend fun update(item: PendingTransmissionEntity)

    @Query("DELETE FROM pending_transmissions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM pending_transmissions WHERE packetId = :packetId")
    suspend fun deleteByPacketId(packetId: String)

    @Query("UPDATE pending_transmissions SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("SELECT COUNT(*) FROM pending_transmissions WHERE status = 'QUEUED'")
    fun getPendingCount(): Flow<Int>

    @Query("DELETE FROM pending_transmissions")
    suspend fun clearAll()
}
