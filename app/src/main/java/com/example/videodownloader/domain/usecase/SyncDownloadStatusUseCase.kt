package com.example.videodownloader.domain.usecase

import com.example.videodownloader.core.text.AppText
import com.example.videodownloader.core.text.DefaultAppText
import com.example.videodownloader.data.repository.DownloadTaskRepository
import com.example.videodownloader.data.repository.ParseRecordRepository
import com.example.videodownloader.domain.model.DownloadTask
import com.example.videodownloader.domain.model.DownloadTaskStatus
import com.example.videodownloader.domain.model.ParseRecordStatus
import com.example.videodownloader.download.DownloadGateway
import com.example.videodownloader.download.DownloadProgressState
import java.io.File
import java.io.FileInputStream

class SyncDownloadStatusUseCase(
    private val repository: DownloadTaskRepository,
    private val parseRecordRepository: ParseRecordRepository,
    private val downloadGateway: DownloadGateway,
    private val appText: AppText = DefaultAppText,
) {
    suspend operator fun invoke() {
        val activeTasks = repository.getActiveTasks()
        activeTasks.forEach { task ->
            syncSingleTask(task)
        }
    }

    private suspend fun syncSingleTask(task: DownloadTask) {
        val now = System.currentTimeMillis()
        val externalId = task.externalDownloadId ?: run {
            updateTaskAndParseRecord(
                task = task,
                status = DownloadTaskStatus.FAILED,
                progress = task.progress,
                saveUri = task.saveUri,
                errorMessage = appText.missingSystemDownloadId(),
                now = now,
            )
            return
        }

        val snapshot = downloadGateway.queryDownloadProgress(externalId)
        var newStatus = when (snapshot.state) {
            DownloadProgressState.QUEUED -> DownloadTaskStatus.QUEUED
            DownloadProgressState.DOWNLOADING -> DownloadTaskStatus.DOWNLOADING
            DownloadProgressState.SUCCESS -> DownloadTaskStatus.SUCCESS
            DownloadProgressState.FAILED -> DownloadTaskStatus.FAILED
        }

        var newProgress = when (newStatus) {
            DownloadTaskStatus.SUCCESS -> 100
            else -> snapshot.progress ?: task.progress
        }

        var newSaveUri = snapshot.saveUri ?: task.saveUri
        var newErrorMessage = snapshot.errorMessage

        if (newStatus == DownloadTaskStatus.SUCCESS) {
            val validationError = validateDownloadedFile(
                saveUri = newSaveUri,
                expectedExt = task.selectedExt,
            )
            if (validationError != null) {
                newStatus = DownloadTaskStatus.FAILED
                newProgress = maxOf(task.progress, newProgress)
                newSaveUri = null
                newErrorMessage = validationError
            }
        }

        if (
            newStatus == task.status &&
            newProgress == task.progress &&
            newSaveUri == task.saveUri &&
            newErrorMessage == task.errorMessage
        ) {
            return
        }

        updateTaskAndParseRecord(
            task = task,
            status = newStatus,
            progress = newProgress,
            saveUri = newSaveUri,
            errorMessage = newErrorMessage,
            now = now,
        )
    }

    private suspend fun updateTaskAndParseRecord(
        task: DownloadTask,
        status: DownloadTaskStatus,
        progress: Int,
        saveUri: String?,
        errorMessage: String?,
        now: Long,
    ) {
        val updatedTask = task.copy(
            status = status,
            progress = progress,
            saveUri = saveUri,
            errorMessage = errorMessage,
            updatedAt = now,
        )
        repository.updateTask(updatedTask)

        val parseRecordId = task.parseRecordId ?: return
        val record = parseRecordRepository.getRecord(parseRecordId) ?: return
        val siblingTasks = repository.getTasksByParseRecordId(parseRecordId)
            .replaceWith(updatedTask)
        val aggregate = siblingTasks.aggregateStatus()
        parseRecordRepository.updateRecord(
            record.copy(
                status = aggregate.status.toParseRecordStatus(),
                message = aggregate.message,
                updatedAt = now,
            ),
        )
    }

    private fun List<DownloadTask>.replaceWith(updatedTask: DownloadTask): List<DownloadTask> {
        if (none { it.id == updatedTask.id }) return this + updatedTask
        return map { if (it.id == updatedTask.id) updatedTask else it }
    }

    private fun List<DownloadTask>.aggregateStatus(): AggregateDownloadStatus {
        if (isEmpty()) {
            return AggregateDownloadStatus(DownloadTaskStatus.FAILED, appText.downloadFailed())
        }
        firstOrNull { it.status == DownloadTaskStatus.FAILED }?.let { failedTask ->
            return AggregateDownloadStatus(
                status = DownloadTaskStatus.FAILED,
                message = failedTask.errorMessage ?: appText.downloadFailed(),
            )
        }
        if (any { it.status == DownloadTaskStatus.DOWNLOADING }) {
            return AggregateDownloadStatus(DownloadTaskStatus.DOWNLOADING, appText.downloading())
        }
        if (any { it.status == DownloadTaskStatus.QUEUED }) {
            return AggregateDownloadStatus(DownloadTaskStatus.QUEUED, appText.taskQueued())
        }
        if (all { it.status == DownloadTaskStatus.SUCCESS }) {
            return AggregateDownloadStatus(DownloadTaskStatus.SUCCESS, appText.downloadSuccess())
        }
        if (all { it.status == DownloadTaskStatus.CANCELED }) {
            return AggregateDownloadStatus(DownloadTaskStatus.CANCELED, appText.downloadCanceled())
        }
        return AggregateDownloadStatus(DownloadTaskStatus.CANCELED, appText.downloadCanceled())
    }

    private fun validateDownloadedFile(saveUri: String?, expectedExt: String): String? {
        val absolutePath = saveUri
            ?.takeIf { it.startsWith("file://", ignoreCase = true) }
            ?.removePrefix("file://")
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val file = File(absolutePath)
        if (!file.exists()) {
            return appText.downloadedFileMissing()
        }
        if (file.length() <= 0L) {
            file.delete()
            return appText.downloadedFileEmpty()
        }

        val header = readFileHeader(file, 512)
        val headerText = header.toString(Charsets.UTF_8).trimStart()
        if (headerText.startsWith("#EXTM3U", ignoreCase = true)) {
            file.delete()
            return appText.downloadedPlaylistNotMerged()
        }
        if (headerText.startsWith("<!doctype html", ignoreCase = true) || headerText.startsWith("<html", ignoreCase = true)) {
            file.delete()
            return appText.downloadedHtmlInsteadOfVideo()
        }

        if (isImageExt(expectedExt)) {
            if (!looksLikeImageContainer(header, expectedExt)) {
                file.delete()
                return appText.downloadedImageInvalid()
            }
            return null
        }

        if (!looksLikeVideoContainer(header, expectedExt)) {
            file.delete()
            return appText.downloadedVideoInvalid()
        }

        return null
    }

    private fun readFileHeader(file: File, maxBytes: Int): ByteArray {
        FileInputStream(file).use { input ->
            val buffer = ByteArray(maxBytes)
            val read = input.read(buffer)
            if (read <= 0) return ByteArray(0)
            return buffer.copyOf(read)
        }
    }

    private fun looksLikeVideoContainer(header: ByteArray, expectedExt: String): Boolean {
        if (header.isEmpty()) return false

        val isMp4Family = header.size >= 8 &&
            header[4] == 'f'.code.toByte() &&
            header[5] == 't'.code.toByte() &&
            header[6] == 'y'.code.toByte() &&
            header[7] == 'p'.code.toByte()
        val isEbml = header.size >= 4 &&
            header[0] == 0x1A.toByte() &&
            header[1] == 0x45.toByte() &&
            header[2] == 0xDF.toByte() &&
            header[3] == 0xA3.toByte()
        val isMpegTs = header.size >= 1 && header[0] == 0x47.toByte()
        val isFlv = header.size >= 3 &&
            header[0] == 'F'.code.toByte() &&
            header[1] == 'L'.code.toByte() &&
            header[2] == 'V'.code.toByte()

        return when (expectedExt.lowercase()) {
            // HLS 合并结果有时是 TS/FLV 容器，避免被误判成“格式异常”。
            "mp4", "mov", "m4v", "3gp" -> isMp4Family || isMpegTs || isFlv
            "webm", "mkv" -> isEbml
            "ts" -> isMpegTs
            "m3u8" -> false
            else -> isMp4Family || isEbml || isMpegTs || isFlv
        }
    }

    private fun isImageExt(ext: String): Boolean {
        return when (ext.lowercase()) {
            "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic" -> true
            else -> false
        }
    }

    private fun looksLikeImageContainer(header: ByteArray, expectedExt: String): Boolean {
        if (header.isEmpty()) return false
        val lowerExt = expectedExt.lowercase()

        val isJpeg = header.size >= 2 &&
            header[0] == 0xFF.toByte() &&
            header[1] == 0xD8.toByte()
        val isPng = header.size >= 8 &&
            header[0] == 0x89.toByte() &&
            header[1] == 0x50.toByte() &&
            header[2] == 0x4E.toByte() &&
            header[3] == 0x47.toByte()
        val isGif = header.size >= 6 &&
            header[0] == 'G'.code.toByte() &&
            header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte()
        val isWebp = header.size >= 12 &&
            header[0] == 'R'.code.toByte() &&
            header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte() &&
            header[3] == 'F'.code.toByte() &&
            header[8] == 'W'.code.toByte() &&
            header[9] == 'E'.code.toByte() &&
            header[10] == 'B'.code.toByte() &&
            header[11] == 'P'.code.toByte()
        val isBmp = header.size >= 2 &&
            header[0] == 'B'.code.toByte() &&
            header[1] == 'M'.code.toByte()
        val isHeic = header.size >= 12 &&
            header[4] == 'f'.code.toByte() &&
            header[5] == 't'.code.toByte() &&
            header[6] == 'y'.code.toByte() &&
            header[7] == 'p'.code.toByte() &&
            (
                header[8] == 'h'.code.toByte() ||
                    header[8] == 'm'.code.toByte() ||
                    header[8] == 'h'.code.toByte()
                )

        return when (lowerExt) {
            "jpg", "jpeg" -> isJpeg
            "png" -> isPng
            "gif" -> isGif
            "webp" -> isWebp
            "bmp" -> isBmp
            "heic" -> isHeic || isMp4FamilyLike(header)
            else -> isJpeg || isPng || isGif || isWebp || isBmp || isHeic
        }
    }

    private fun isMp4FamilyLike(header: ByteArray): Boolean {
        return header.size >= 8 &&
            header[4] == 'f'.code.toByte() &&
            header[5] == 't'.code.toByte() &&
            header[6] == 'y'.code.toByte() &&
            header[7] == 'p'.code.toByte()
    }

    private fun DownloadTaskStatus.toParseRecordStatus(): ParseRecordStatus {
        return when (this) {
            DownloadTaskStatus.QUEUED -> ParseRecordStatus.QUEUED
            DownloadTaskStatus.DOWNLOADING -> ParseRecordStatus.DOWNLOADING
            DownloadTaskStatus.SUCCESS -> ParseRecordStatus.SUCCESS
            DownloadTaskStatus.FAILED -> ParseRecordStatus.FAILED
            DownloadTaskStatus.CANCELED -> ParseRecordStatus.CANCELED
        }
    }

    private data class AggregateDownloadStatus(
        val status: DownloadTaskStatus,
        val message: String,
    )
}
