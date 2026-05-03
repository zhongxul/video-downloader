package com.example.videodownloader.ui.redesign.profile

sealed interface ProfileAction {
    data object OpenSavePath : ProfileAction
    data object ToggleNotification : ProfileAction
    data object OpenXCookieSettings : ProfileAction
    data object OpenSupportedSites : ProfileAction
    data object ExportLogs : ProfileAction
    data object ReportIssue : ProfileAction
}
