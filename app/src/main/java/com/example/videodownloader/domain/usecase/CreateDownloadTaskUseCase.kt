package com.example.videodownloader.domain.usecase

import com.example.videodownloader.data.repository.DownloadTaskRepository
import com.example.videodownloader.data.repository.ParseRecordRepository
import com.example.videodownloader.domain.model.DownloadTask
import com.example.videodownloader.domain.model.DownloadTaskStatus
import com.example.videodownloader.domain.model.ParseRecordStatus
import com.example.videodownloader.domain.model.VideoFormat
import com.example.videodownloader.download.DownloadGateway
import java.util.UUID

class CreateDownloadTaskUseCase(
    private val repository: DownloadTaskRepository,
    private val parseRecordRepository: ParseRecordRepository,
    private val downloadGateway: DownloadGateway,
) {
    suspend operator fun invoke(
        sourceUrl: String,
        title: String,
        coverUrl: String?,
        format: VideoFormat,
        parseRecordId: String? = null,
        retryFromTaskId: String? = null,
    ): DownloadTask {
        require(format.downloadable) { "该选项不是可直接下载的视频文件，请选择其他清晰度重试" }

        val now = System.currentTimeMillis()
        val taskId = UUID.randomUUID().toString()
        val targetExt = resolveOutputExt(format.ext)
        val fileName = buildFileName(title, targetExt)
        val enqueueResult = downloadGateway.startDownload(
            url = format.downloadUrl,
            fileName = fileName,
        )
        val resolvedTitle = resolveTaskTitle(title, enqueueResult.fileName)

        val task = DownloadTask(
            id = taskId,
            parseRecordId = parseRecordId,
            sourceUrl = sourceUrl,
            downloadUrl = format.downloadUrl,
            title = resolvedTitle,
            coverUrl = coverUrl,
            selectedFormatId = format.formatId,
            selectedResolution = format.resolution,
            selectedExt = targetExt,
            status = DownloadTaskStatus.QUEUED,
            progress = 0,
            saveUri = enqueueResult.saveUri,
            errorMessage = null,
            createdAt = now,
            updatedAt = now,
            retryFromTaskId = retryFromTaskId,
            externalDownloadId = enqueueResult.externalId,
        )
        repository.insertTask(task)
        if (!parseRecordId.isNullOrBlank()) {
            val record = parseRecordRepository.getRecord(parseRecordId)
            if (record != null) {
                parseRecordRepository.updateRecord(
                    record.copy(
                        taskId = taskId,
                        selectedFormatLabel = format.resolution,
                        selectedExt = targetExt,
                        status = ParseRecordStatus.QUEUED,
                        message = "已加入下载队列",
                        updatedAt = now,
                    ),
                )
            }
        }
        return task
    }

    private fun buildFileName(title: String, ext: String): String {
        val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .ifBlank { "video" }
            .trimEnd('.')
            .take(80)
        val normalizedExt = normalizeExt(ext)
        return "$safeTitle.$normalizedExt"
    }

    private fun resolveOutputExt(ext: String): String {
        val normalized = normalizeExt(ext)
        // m3u8 下载成功后会在本地合并为 mp4 文件，统一按 mp4 管理。
        return if (normalized == "m3u8") "mp4" else normalized
    }

    private fun resolveTaskTitle(originalTitle: String, fileName: String?): String {
        val fromFileName = fileName
            ?.substringBeforeLast('.')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        return fromFileName ?: originalTitle
    }

    private fun normalizeExt(ext: String): String {
        val value = ext.trim().lowercase().trimStart('.')
        return when (value) {
            "mp4", "m3u8", "webm", "mov", "mkv" -> value
            else -> "mp4"
        }
    }
}
