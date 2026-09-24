package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        VoiceMessageEntity::class,
        PendingTransmissionEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class ITantraDatabase : RoomDatabase() {

    abstract fun voiceMessageDao(): VoiceMessageDao
    abstract fun pendingTransmissionDao(): PendingTransmissionDao

    companion object {
        @Volatile
        private var INSTANCE: ITantraDatabase? = null

        fun getInstance(context: Context): ITantraDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ITantraDatabase::class.java,
                    "itantra_mission_logs.db"
                ).fallbackToDestructiveMigration(true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
