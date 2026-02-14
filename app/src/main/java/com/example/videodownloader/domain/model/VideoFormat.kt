package com.example.videodownloader.domain.model

data class VideoFormat(
    val formatId: String,
    val resolution: String,
    val ext: String,
    val sizeText: String?,
    val downloadUrl: String,
    val durationSec: Double? = null,
    val fileSizeBytes: Long? = null,
    val downloadable: Boolean = true,
)
