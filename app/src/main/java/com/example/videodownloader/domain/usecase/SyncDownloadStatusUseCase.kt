package com.example.videodownloader.domain.usecase

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
                errorMessage = "下载任务缺少系统下载 ID",
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
        repository.updateTask(
            task.copy(
                status = status,
                progress = progress,
                saveUri = saveUri,
                errorMessage = errorMessage,
                updatedAt = now,
            ),
        )

        val parseRecordId = task.parseRecordId ?: return
        val record = parseRecordRepository.getRecord(parseRecordId) ?: return
        parseRecordRepository.updateRecord(
            record.copy(
                status = status.toParseRecordStatus(),
                message = when (status) {
                    DownloadTaskStatus.SUCCESS -> "下载成功"
                    DownloadTaskStatus.FAILED -> errorMessage ?: "下载失败"
                    DownloadTaskStatus.CANCELED -> "下载已取消"
                    DownloadTaskStatus.QUEUED -> "任务排队中"
                    DownloadTaskStatus.DOWNLOADING -> "下载中"
                },
                updatedAt = now,
            ),
        )
    }

    private fun validateDownloadedFile(saveUri: String?, expectedExt: String): String? {
        val absolutePath = saveUri
            ?.takeIf { it.startsWith("file://", ignoreCase = true) }
            ?.removePrefix("file://")
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val file = File(absolutePath)
        if (!file.exists()) {
            return "下载文件不存在，已按失败处理"
        }
        if (file.length() <= 0L) {
            file.delete()
            return "下载文件为空，已自动删除"
        }

        val header = readFileHeader(file, 512)
        val headerText = header.toString(Charsets.UTF_8).trimStart()
        if (headerText.startsWith("#EXTM3U", ignoreCase = true)) {
            file.delete()
            return "下载结果仍是 m3u8 播放清单，未成功合并为视频文件，已按失败处理并删除文件"
        }
        if (headerText.startsWith("<!doctype html", ignoreCase = true) || headerText.startsWith("<html", ignoreCase = true)) {
            file.delete()
            return "下载结果不是视频文件（网页内容），已按失败处理并删除文件"
        }

        if (!looksLikeVideoContainer(header, expectedExt)) {
            file.delete()
            return "下载文件格式异常，疑似不可播放，已按失败处理并删除文件"
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

    private fun DownloadTaskStatus.toParseRecordStatus(): ParseRecordStatus {
        return when (this) {
            DownloadTaskStatus.QUEUED -> ParseRecordStatus.QUEUED
            DownloadTaskStatus.DOWNLOADING -> ParseRecordStatus.DOWNLOADING
            DownloadTaskStatus.SUCCESS -> ParseRecordStatus.SUCCESS
            DownloadTaskStatus.FAILED -> ParseRecordStatus.FAILED
            DownloadTaskStatus.CANCELED -> ParseRecordStatus.CANCELED
        }
    }
}
