package com.example.videodownloader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DownloadTaskEntity::class, ParseRecordEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadTaskDao(): DownloadTaskDao
    abstract fun parseRecordDao(): ParseRecordDao
}
