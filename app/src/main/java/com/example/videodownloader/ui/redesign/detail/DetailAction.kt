package com.example.videodownloader.ui.redesign.detail

sealed interface DetailAction {
    data class SelectMedia(val index: Int) : DetailAction
    data class OpenMedia(val index: Int) : DetailAction
    data object GoBack : DetailAction
    data object OpenContent : DetailAction
    data object DeleteRecord : DetailAction
    data object RetryDownload : DetailAction
    data object PauseDownload : DetailAction
    data object ResumeDownload : DetailAction
    data object ShowMore : DetailAction
}
