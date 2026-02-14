package com.example.videodownloader.domain.model

enum class ParseRecordStatus {
    PARSED,
    PARSE_FAILED,
    QUEUED,
    DOWNLOADING,
    SUCCESS,
    FAILED,
    CANCELED,
}

