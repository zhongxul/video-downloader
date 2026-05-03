package com.example.videodownloader.ui.redesign.parse_result

sealed interface ParseResultAction {
    data class SelectResource(val index: Int) : ParseResultAction
    data object DownloadCurrent : ParseResultAction
    data object DownloadAll : ParseResultAction
    data object GoBack : ParseResultAction
}
