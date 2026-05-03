package com.example.videodownloader.domain.usecase

import com.example.videodownloader.core.text.AppText
import com.example.videodownloader.core.text.DefaultAppText
import com.example.videodownloader.data.repository.DownloadTaskRepository
import com.example.videodownloader.domain.model.DownloadTask
import com.example.videodownloader.domain.model.DownloadTaskStatus
import com.example.videodownloader.download.DownloadGateway
import java.util.UUID

class RetryDownloadTaskUseCase(
    private val repository: DownloadTaskRepository,
    private val downloadGateway: DownloadGateway,
    private val appText: AppText = DefaultAppText,
) {
    suspend operator fun invoke(taskId: String): DownloadTask {
        val sourceTask = requireNotNull(repository.getTask(taskId)) { appText.taskNotFound() }
        val now = System.currentTimeMillis()
        val newTaskId = UUID.randomUUID().toString()
        val fileName = buildOutputFileName(
            title = sourceTask.title,
            ext = sourceTask.selectedExt,
            isImage = isImageExt(sourceTask.selectedExt),
        )
        val enqueueResult = downloadGateway.startDownload(
            url = sourceTask.downloadUrl,
            fileName = fileName,
        )
        val resolvedTitle = resolveTaskTitle(sourceTask.title, enqueueResult.fileName)
        val newTask = sourceTask.copy(
            id = newTaskId,
            title = resolvedTitle,
            status = DownloadTaskStatus.QUEUED,
            progress = 0,
            errorMessage = null,
            createdAt = now,
            updatedAt = now,
            retryFromTaskId = sourceTask.id,
            externalDownloadId = enqueueResult.externalId,
            saveUri = enqueueResult.saveUri,
        )
        repository.insertTask(newTask)
        return newTask
    }

    private fun resolveTaskTitle(originalTitle: String, fileName: String?): String {
        val fromFileName = fileName
            ?.substringBeforeLast('.')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        return fromFileName ?: originalTitle
    }
}
