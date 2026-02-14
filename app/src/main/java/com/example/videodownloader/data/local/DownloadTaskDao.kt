package com.example.videodownloader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadTaskEntity)

    @Update
    suspend fun update(entity: DownloadTaskEntity)

    @Query("SELECT * FROM download_tasks WHERE id = :taskId LIMIT 1")
    suspend fun getById(taskId: String): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE id = :taskId LIMIT 1")
    fun observeById(taskId: String): Flow<DownloadTaskEntity?>

    @Query("SELECT * FROM download_tasks WHERE status IN ('QUEUED', 'DOWNLOADING') ORDER BY createdAt DESC")
    suspend fun getActiveTasks(): List<DownloadTaskEntity>

    @Query("SELECT * FROM download_tasks WHERE id IN (:taskIds)")
    suspend fun getByIds(taskIds: List<String>): List<DownloadTaskEntity>

    @Query("DELETE FROM download_tasks WHERE id IN (:taskIds)")
    suspend fun deleteByIds(taskIds: List<String>)

    @Query("DELETE FROM download_tasks WHERE status IN ('SUCCESS', 'FAILED', 'CANCELED')")
    suspend fun clearFinished()

    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadTaskEntity>>
}
