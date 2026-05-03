package com.example.videodownloader.ui.redesign.detail

sealed interface DetailUiEvent {
    data object NavigateBack : DetailUiEvent
    data class ShowToast(val message: String) : DetailUiEvent
    data class OpenFile(val path: String, val mimeType: String) : DetailUiEvent
}
