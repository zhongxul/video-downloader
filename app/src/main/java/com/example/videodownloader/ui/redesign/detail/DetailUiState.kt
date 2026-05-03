package com.example.videodownloader.ui.redesign.detail

data class DetailUiState(
    val taskId: String = "",
    val title: String = "",
    val subtitle: String = "",
    val status: TaskStatus = TaskStatus.UNKNOWN,
    val statusLabel: String = "",
    val previewUrl: String? = null,
    val currentIndex: Int = 0,
    val mediaItems: List<DetailMediaItem> = emptyList(),
    val metaItems: List<MetaItem> = emptyList(),
    val hint: String = "",
)

enum class TaskStatus { DOWNLOADING, COMPLETED, FAILED, PAUSED, QUEUED, UNKNOWN }

data class MetaItem(
    val label: String,
    val value: String,
)

data class DetailMediaItem(
    val taskId: String,
    val title: String,
    val previewUrl: String?,
    val mediaUrl: String?,
    val saveUri: String?,
    val ext: String,
    val isVideo: Boolean = false,
)
