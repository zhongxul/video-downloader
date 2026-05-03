package com.example.videodownloader.ui.redesign.download

sealed interface DownloadAction {
    data class LinkChanged(val value: String) : DownloadAction
    data object PasteFromClipboard : DownloadAction
    data object StartParse : DownloadAction
    data object DismissError : DownloadAction
    data object NavigateToLibrary : DownloadAction
}
