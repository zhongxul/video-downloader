package com.example.videodownloader.data.repository

import com.example.videodownloader.domain.model.DownloadTask
import kotlinx.coroutines.flow.Flow

interface DownloadTaskRepository {
    suspend fun insertTask(task: DownloadTask)
    suspend fun updateTask(task: DownloadTask)
    suspend fun getTask(taskId: String): DownloadTask?
    suspend fun getTasks(taskIds: List<String>): List<DownloadTask>
    suspend fun getActiveTasks(): List<DownloadTask>
    suspend fun deleteTasks(taskIds: List<String>)
    suspend fun clearFinishedTasks()
    fun observeTask(taskId: String): Flow<DownloadTask?>
    fun observeTasks(): Flow<List<DownloadTask>>
}
