package com.example.videodownloader.ui.redesign.library

sealed interface LibraryAction {
    data class SwitchTab(val tab: LibraryTab) : LibraryAction
    data class OpenDetail(val taskId: String) : LibraryAction
    data class OpenParseResult(val parseRecordId: String) : LibraryAction
    data class EnterCompletedManage(val itemId: String) : LibraryAction
    data class ToggleCompletedSelection(val itemId: String) : LibraryAction
    data object ToggleSelectAllCompleted : LibraryAction
    data object DeleteSelectedCompleted : LibraryAction
    data class EnterParseManage(val recordId: String) : LibraryAction
    data class ToggleParseSelection(val recordId: String) : LibraryAction
    data object ToggleSelectAllParseRecords : LibraryAction
    data object DeleteSelectedParseRecords : LibraryAction
    data object ExitManageMode : LibraryAction
}
