package com.example.videodownloader.domain.usecase

import com.example.videodownloader.data.repository.DownloadTaskRepository
import com.example.videodownloader.domain.model.DownloadTaskStatus
import com.example.videodownloader.download.DownloadGateway

class PauseDownloadTaskUseCase(
    private val repository: DownloadTaskRepository,
    private val downloadGateway: DownloadGateway,
) {
    suspend operator fun invoke(taskId: String) {
        val task = requireNotNull(repository.getTask(taskId)) { "任务不存在" }
        val externalId = task.externalDownloadId ?: throw IllegalArgumentException("任务缺少系统下载 ID")
        downloadGateway.cancelDownload(externalId)
        repository.updateTask(
            task.copy(
                status = DownloadTaskStatus.CANCELED,
                updatedAt = System.currentTimeMillis(),
                errorMessage = null,
            ),
        )
    }
}
