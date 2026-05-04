package com.example.videodownloader.ui.redesign.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.videodownloader.di.AppContainer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ProfileUiState(
            notificationEnabled = container.appSettingsStore.isDownloadNotificationEnabled(),
        ),
    )
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _events = Channel<ProfileUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onAction(action: ProfileAction) {
        when (action) {
            ProfileAction.OpenSavePath -> {
                sendToast("保存位置暂按系统下载目录管理")
            }
            ProfileAction.ToggleNotification -> {
                val current = _uiState.value
                val nextEnabled = !current.notificationEnabled
                container.appSettingsStore.setDownloadNotificationEnabled(nextEnabled)
                _uiState.value = current.copy(
                    notificationEnabled = nextEnabled,
                )
                sendToast(if (nextEnabled) "下载完成通知已开启" else "下载完成通知已关闭")
            }
            ProfileAction.OpenXCookieSettings -> {
                viewModelScope.launch {
                    _events.send(ProfileUiEvent.NavigateToXCookieSettings)
                }
            }
            ProfileAction.OpenSupportedSites -> {
                sendToast("当前支持抖音、X 以及直链媒体解析")
            }
            ProfileAction.ExportLogs -> {
                sendToast("日志导出后续接入")
            }
            ProfileAction.ReportIssue -> {
                sendToast("异常反馈后续接入")
            }
        }
    }

    private fun sendToast(message: String) {
        viewModelScope.launch {
            _events.send(ProfileUiEvent.ShowToast(message))
        }
    }
}

class ProfileViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ProfileViewModel(container) as T
    }
}
