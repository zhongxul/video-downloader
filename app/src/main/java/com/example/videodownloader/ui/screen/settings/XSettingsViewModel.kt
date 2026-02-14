package com.example.videodownloader.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.videodownloader.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class XSettingsUiState(
    val cookieInput: String = "",
    val actionMessage: String? = null,
)

class XSettingsViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        XSettingsUiState(cookieInput = container.xCookieStore.getCookie().orEmpty()),
    )
    val uiState: StateFlow<XSettingsUiState> = _uiState.asStateFlow()

    fun onCookieChanged(value: String) {
        _uiState.update { it.copy(cookieInput = value) }
    }

    fun reloadCookieFromStore() {
        _uiState.update { it.copy(cookieInput = container.xCookieStore.getCookie().orEmpty()) }
    }

    fun saveCookie() {
        val normalized = container.xCookieStore.saveCookie(_uiState.value.cookieInput)
        val authToken = container.xCookieValidator.extractCookieValue(normalized, "auth_token")
        val ct0 = container.xCookieValidator.extractCookieValue(normalized, "ct0")
        _uiState.update {
            it.copy(
                cookieInput = normalized,
                actionMessage = when {
                    normalized.isBlank() -> "Cookie 已清空"
                    authToken.isNullOrBlank() || ct0.isNullOrBlank() -> "Cookie 已保存，但缺少 auth_token 或 ct0"
                    else -> "Cookie 已保存"
                },
            )
        }
    }

    fun importCookieFromWeb(rawCookie: String) {
        val normalized = container.xCookieStore.saveCookie(rawCookie)
        _uiState.update {
            it.copy(
                cookieInput = normalized,
                actionMessage = if (normalized.isBlank()) {
                    "未读取到有效 Cookie，请确认已登录 X"
                } else {
                    "已从登录页面读取并保存 Cookie"
                },
            )
        }
    }

    fun clearCookie() {
        container.xCookieStore.clearCookie()
        _uiState.update { it.copy(cookieInput = "", actionMessage = "Cookie 已清空") }
    }

    fun clearMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }
}

class XSettingsViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return XSettingsViewModel(container) as T
    }
}
