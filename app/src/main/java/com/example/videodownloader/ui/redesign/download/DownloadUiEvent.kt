package com.example.videodownloader.ui.redesign.download

sealed interface DownloadUiEvent {
    data class NavigateToParseResult(val parseRecordId: String) : DownloadUiEvent
    data class ShowToast(val message: String) : DownloadUiEvent
}
