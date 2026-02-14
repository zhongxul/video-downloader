package com.example.videodownloader.domain.usecase

import com.example.videodownloader.data.repository.DownloadTaskRepository

class ClearFinishedHistoryUseCase(
    private val repository: DownloadTaskRepository,
) {
    suspend operator fun invoke() {
        repository.clearFinishedTasks()
    }
}
