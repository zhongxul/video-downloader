package com.example.videodownloader.data.repository

import com.example.videodownloader.data.local.DownloadTaskDao
import com.example.videodownloader.data.local.toDomain
import com.example.videodownloader.data.local.toEntity
import com.example.videodownloader.domain.model.DownloadTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DownloadTaskRepositoryImpl(
    private val dao: DownloadTaskDao,
) : DownloadTaskRepository {
    override suspend fun insertTask(task: DownloadTask) {
        dao.insert(task.toEntity())
    }

    override suspend fun updateTask(task: DownloadTask) {
        dao.update(task.toEntity())
    }

    override suspend fun getTask(taskId: String): DownloadTask? {
        return dao.getById(taskId)?.toDomain()
    }

    override suspend fun getTasks(taskIds: List<String>): List<DownloadTask> {
        if (taskIds.isEmpty()) return emptyList()
        return dao.getByIds(taskIds).map { it.toDomain() }
    }

    override suspend fun getActiveTasks(): List<DownloadTask> {
        return dao.getActiveTasks().map { it.toDomain() }
    }

    override suspend fun deleteTasks(taskIds: List<String>) {
        if (taskIds.isEmpty()) return
        dao.deleteByIds(taskIds)
    }

    override suspend fun clearFinishedTasks() {
        dao.clearFinished()
    }

    override fun observeTask(taskId: String): Flow<DownloadTask?> {
        return dao.observeById(taskId).map { it?.toDomain() }
    }

    override fun observeTasks(): Flow<List<DownloadTask>> {
        return dao.observeAll().map { list -> list.map { it.toDomain() } }
    }
}
