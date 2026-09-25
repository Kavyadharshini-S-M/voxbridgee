package com.example.data

import kotlinx.coroutines.flow.Flow

class VoiceMessageRepository(private val dao: VoiceMessageDao) {

    val allMessages: Flow<List<VoiceMessageEntity>> = dao.getAllMessages()
    val alertCount: Flow<Int> = dao.getAlertCount()

    suspend fun logMessage(message: VoiceMessageEntity): Long {
        return dao.insert(message)
    }

    suspend fun markAsPlayed(id: Long) {
        dao.markAsPlayed(id)
    }

    suspend fun deleteMessage(id: Long) {
        dao.deleteById(id)
    }

    suspend fun clearMissionLogs() {
        dao.purgeOlderThan(Long.MAX_VALUE)
    }

    suspend fun purgeExpired48hMessages() {
        val cutoff = System.currentTimeMillis() - (48L * 60 * 60 * 1000L)
        dao.purgeOlderThan(cutoff)
    }
}
