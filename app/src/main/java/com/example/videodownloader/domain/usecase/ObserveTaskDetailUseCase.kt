package com.example.videodownloader.domain.usecase

import com.example.videodownloader.data.repository.DownloadTaskRepository
import com.example.videodownloader.domain.model.DownloadTask
import kotlinx.coroutines.flow.Flow

class ObserveTaskDetailUseCase(
    private val repository: DownloadTaskRepository,
) {
    operator fun invoke(taskId: String): Flow<DownloadTask?> {
        return repository.observeTask(taskId)
    }
}
