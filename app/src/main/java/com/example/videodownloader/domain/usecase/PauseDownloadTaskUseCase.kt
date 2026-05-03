package com.example.videodownloader.domain.usecase

import com.example.videodownloader.core.text.AppText
import com.example.videodownloader.core.text.DefaultAppText
import com.example.videodownloader.data.repository.DownloadTaskRepository
import com.example.videodownloader.domain.model.DownloadTaskStatus
import com.example.videodownloader.download.DownloadGateway

class PauseDownloadTaskUseCase(
    private val repository: DownloadTaskRepository,
    private val downloadGateway: DownloadGateway,
    private val appText: AppText = DefaultAppText,
) {
    suspend operator fun invoke(taskId: String) {
        val task = requireNotNull(repository.getTask(taskId)) { appText.taskNotFound() }
        val externalId = task.externalDownloadId ?: throw IllegalArgumentException(appText.missingSystemDownloadId())
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
