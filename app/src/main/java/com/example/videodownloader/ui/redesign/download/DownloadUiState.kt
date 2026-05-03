package com.example.videodownloader.ui.redesign.download

data class DownloadUiState(
    val linkInput: String = "",
    val isParsing: Boolean = false,
    val parseError: String? = null,
    val todayCompleted: Int = 0,
    val queuedCount: Int = 0,
    val recentParse: RecentParseInfo? = null,
    val queueSummary: QueueSummary? = null,
)

data class RecentParseInfo(
    val title: String,
    val resourceDesc: String,
    val timeAgo: String,
)

data class QueueSummary(
    val waitingCount: Int = 0,
    val retryCount: Int = 0,
)
