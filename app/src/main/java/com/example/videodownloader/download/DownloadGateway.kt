package com.example.videodownloader.download

data class StartDownloadResult(
    val externalId: Long,
    val saveUri: String?,
    val fileName: String?,
)

enum class DownloadProgressState {
    QUEUED,
    DOWNLOADING,
    SUCCESS,
    FAILED,
}

data class DownloadProgressSnapshot(
    val state: DownloadProgressState,
    val progress: Int?,
    val saveUri: String?,
    val errorMessage: String?,
)

interface DownloadGateway {
    suspend fun startDownload(
        url: String,
        fileName: String,
    ): StartDownloadResult

    suspend fun queryDownloadProgress(externalId: Long): DownloadProgressSnapshot

    suspend fun cancelDownload(externalId: Long)
}
