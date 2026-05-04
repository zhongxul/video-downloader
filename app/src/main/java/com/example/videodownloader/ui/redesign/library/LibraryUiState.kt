package com.example.videodownloader.ui.redesign.library

data class LibraryUiState(
    val currentTab: LibraryTab = LibraryTab.IN_PROGRESS,
    val inProgressItems: List<InProgressGroup> = emptyList(),
    val completedItems: List<CompletedItem> = emptyList(),
    val parseRecords: List<ParseRecordItem> = emptyList(),
    val inProgressCount: Int = 0,
    val completedCount: Int = 0,
    val parseRecordCount: Int = 0,
    val inProgressManageMode: Boolean = false,
    val selectedInProgressIds: Set<String> = emptySet(),
    val completedManageMode: Boolean = false,
    val selectedCompletedIds: Set<String> = emptySet(),
    val parseManageMode: Boolean = false,
    val selectedParseIds: Set<String> = emptySet(),
    val expandedParseIds: Set<String> = emptySet(),
)

enum class LibraryTab { IN_PROGRESS, COMPLETED, PARSE_RECORDS }

data class InProgressGroup(
    val id: String,
    val title: String,
    val taskIds: List<String> = emptyList(),
    val totalCount: Int,
    val successCount: Int,
    val downloadingCount: Int,
    val retryCount: Int,
    val progress: Float,
)

data class CompletedItem(
    val id: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val isVideo: Boolean = false,
    val taskIds: List<String> = emptyList(),
    val itemCount: Int = 1,
)

data class ParseRecordItem(
    val id: String,
    val title: String,
    val timeAgo: String,
    val status: String,
    val sourceUrl: String = "",
    val message: String = "",
)
