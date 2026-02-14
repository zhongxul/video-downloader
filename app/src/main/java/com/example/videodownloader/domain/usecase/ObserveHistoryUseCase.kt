package com.example.videodownloader.domain.usecase

import com.example.videodownloader.data.repository.DownloadTaskRepository
import com.example.videodownloader.domain.model.DownloadTask
import kotlinx.coroutines.flow.Flow

class ObserveHistoryUseCase(
    private val repository: DownloadTaskRepository,
) {
    operator fun invoke(): Flow<List<DownloadTask>> {
        return repository.observeTasks()
    }
}
