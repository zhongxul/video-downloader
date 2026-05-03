package com.example.videodownloader.ui.redesign.parse_result

sealed interface ParseResultUiEvent {
    data object NavigateBack : ParseResultUiEvent
    data class ShowToast(val message: String) : ParseResultUiEvent
    data object DownloadStarted : ParseResultUiEvent
}
