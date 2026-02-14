package com.example.videodownloader.domain.usecase

import com.example.videodownloader.data.repository.DownloadTaskRepository
import com.example.videodownloader.domain.model.DownloadTaskStatus
import com.example.videodownloader.download.DownloadGateway

class ResumeDownloadTaskUseCase(
    private val repository: DownloadTaskRepository,
    private val downloadGateway: DownloadGateway,
) {
    suspend operator fun invoke(taskId: String) {
        val task = requireNotNull(repository.getTask(taskId)) { "任务不存在" }
        val fileName = buildFileName(task.title, task.selectedExt)
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

    private fun buildFileName(title: String, ext: String): String {
        val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "video" }
        val safeExt = ext.trim().lowercase().trimStart('.').ifBlank { "mp4" }
        return "$safeTitle.$safeExt"
    }

    private fun resolveTaskTitle(originalTitle: String, fileName: String?): String {
        val fromFileName = fileName
            ?.substringBeforeLast('.')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        return fromFileName ?: originalTitle
    }
}
