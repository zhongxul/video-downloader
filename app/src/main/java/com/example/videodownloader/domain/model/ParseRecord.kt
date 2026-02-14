package com.example.videodownloader.domain.model

data class ParseRecord(
    val id: String,
    val rawInput: String,
    val resolvedUrl: String?,
    val title: String?,
    val coverUrl: String?,
    val status: ParseRecordStatus,
    val message: String?,
    val selectedFormatLabel: String?,
    val selectedExt: String?,
    val taskId: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

