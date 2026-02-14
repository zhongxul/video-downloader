package com.example.videodownloader.domain.model

data class ParsedVideoInfo(
    val title: String,
    val coverUrl: String?,
    val formats: List<VideoFormat>,
)
