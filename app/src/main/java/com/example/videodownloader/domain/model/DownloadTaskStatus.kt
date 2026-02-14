package com.example.videodownloader.domain.model

enum class DownloadTaskStatus {
    QUEUED,
    DOWNLOADING,
    SUCCESS,
    FAILED,
    CANCELED,
}
