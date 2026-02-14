package com.example.videodownloader.domain.model

data class DownloadTask(
    val id: String,
    val parseRecordId: String?,
    val sourceUrl: String,
    val downloadUrl: String,
    val title: String,
    val coverUrl: String?,
    val selectedFormatId: String,
    val selectedResolution: String,
    val selectedExt: String,
    val status: DownloadTaskStatus,
    val progress: Int,
    val saveUri: String?,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val retryFromTaskId: String?,
    val externalDownloadId: Long?,
)
