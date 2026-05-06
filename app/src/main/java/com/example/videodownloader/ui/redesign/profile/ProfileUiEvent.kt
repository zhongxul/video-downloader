package com.example.videodownloader.ui.redesign.profile

sealed interface ProfileUiEvent {
    data class ShowToast(val message: String) : ProfileUiEvent
    data class OpenSystemSettings(val action: String) : ProfileUiEvent
    data object NavigateToXCookieSettings : ProfileUiEvent
    data object NavigateToDouyinCookieSettings : ProfileUiEvent
}
