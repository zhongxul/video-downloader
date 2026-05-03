package com.example.videodownloader.ui.redesign.parse_result

data class ParseResultUiState(
    val title: String = "",
    val sourceTag: String = "",
    val resourceMeta: String = "",
    val currentIndex: Int = 0,
    val resources: List<ResourceItem> = emptyList(),
    val selectedVersion: VersionInfo? = null,
)

data class ResourceItem(
    val id: String,
    val thumbnailUrl: String? = null,
    val previewUrl: String? = null,
    val mediaUrl: String? = null,
    val isVideo: Boolean = false,
)

data class VersionInfo(
    val label: String,
    val description: String,
)
