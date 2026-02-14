package com.example.videodownloader.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.videodownloader.domain.model.DownloadTask
import com.example.videodownloader.domain.model.DownloadTaskStatus

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey val id: String,
    val parseRecordId: String?,
    val sourceUrl: String,
    val downloadUrl: String,
    val title: String,
    val coverUrl: String?,
    val selectedFormatId: String,
    val selectedResolution: String,
    val selectedExt: String,
    val status: String,
    val progress: Int,
    val saveUri: String?,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val retryFromTaskId: String?,
    val externalDownloadId: Long?,
)

fun DownloadTaskEntity.toDomain(): DownloadTask {
    return DownloadTask(
        id = id,
        parseRecordId = parseRecordId,
        sourceUrl = sourceUrl,
        downloadUrl = downloadUrl,
        title = title,
        coverUrl = coverUrl,
        selectedFormatId = selectedFormatId,
        selectedResolution = selectedResolution,
        selectedExt = selectedExt,
        status = DownloadTaskStatus.valueOf(status),
        progress = progress,
        saveUri = saveUri,
        errorMessage = errorMessage,
        createdAt = createdAt,
        updatedAt = updatedAt,
        retryFromTaskId = retryFromTaskId,
        externalDownloadId = externalDownloadId,
    )
}

fun DownloadTask.toEntity(): DownloadTaskEntity {
    return DownloadTaskEntity(
        id = id,
        parseRecordId = parseRecordId,
        sourceUrl = sourceUrl,
        downloadUrl = downloadUrl,
        title = title,
        coverUrl = coverUrl,
        selectedFormatId = selectedFormatId,
        selectedResolution = selectedResolution,
        selectedExt = selectedExt,
        status = status.name,
        progress = progress,
        saveUri = saveUri,
        errorMessage = errorMessage,
        createdAt = createdAt,
        updatedAt = updatedAt,
        retryFromTaskId = retryFromTaskId,
        externalDownloadId = externalDownloadId,
    )
}
