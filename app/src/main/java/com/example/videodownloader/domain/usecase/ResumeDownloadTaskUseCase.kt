package com.example.videodownloader.domain.usecase

import com.example.videodownloader.core.text.AppText
import com.example.videodownloader.core.text.DefaultAppText
import com.example.videodownloader.data.repository.DownloadTaskRepository
import com.example.videodownloader.domain.model.DownloadTaskStatus
import com.example.videodownloader.download.DownloadGateway

class ResumeDownloadTaskUseCase(
    private val repository: DownloadTaskRepository,
    private val downloadGateway: DownloadGateway,
    private val appText: AppText = DefaultAppText,
) {
    suspend operator fun invoke(taskId: String) {
        val task = requireNotNull(repository.getTask(taskId)) { appText.taskNotFound() }
        val fileName = buildOutputFileName(
            title = task.title,
            ext = task.selectedExt,
            isImage = isImageExt(task.selectedExt),
        )
        val startResult = downloadGateway.startDownload(task.downloadUrl, fileName)
        val resolvedTitle = resolveTaskTitle(task.title, startResult.fileName)
        repository.updateTask(
            task.copy(
                title = resolvedTitle,
                status = DownloadTaskStatus.QUEUED,
                progress = 0,
                errorMessage = null,
                externalDownloadId = startResult.externalId,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun resolveTaskTitle(originalTitle: String, fileName: String?): String {
        val fromFileName = fileName
            ?.substringBeforeLast('.')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        return fromFileName ?: originalTitle
    }
}
