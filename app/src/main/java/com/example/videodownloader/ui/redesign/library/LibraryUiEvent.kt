package com.example.videodownloader.ui.redesign.library

sealed interface LibraryUiEvent {
    data class NavigateToDetail(val taskId: String) : LibraryUiEvent
    data class NavigateToParseResult(val parseRecordId: String) : LibraryUiEvent
    data class ShowToast(val message: String) : LibraryUiEvent
}
