package com.example.videodownloader.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.videodownloader.R
import com.example.videodownloader.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DouyinSettingsUiState(
    val cookieInput: String = "",
    val actionMessage: String? = null,
)

class DouyinSettingsViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val appContext = container.appContext
    private val _uiState = MutableStateFlow(
        DouyinSettingsUiState(cookieInput = container.douyinCookieStore.getCookie().orEmpty()),
    )
    val uiState: StateFlow<DouyinSettingsUiState> = _uiState.asStateFlow()

    fun onCookieChanged(value: String) {
        _uiState.update { it.copy(cookieInput = value) }
    }

    fun reloadCookieFromStore() {
        _uiState.update { it.copy(cookieInput = container.douyinCookieStore.getCookie().orEmpty()) }
    }

    fun saveCookie() {
        val normalized = container.douyinCookieStore.saveCookie(_uiState.value.cookieInput)
        _uiState.update {
            it.copy(
                cookieInput = normalized,
                actionMessage = if (normalized.isBlank()) {
                    appContext.getString(R.string.settings_cookie_cleared)
                } else {
                    "抖音 Cookie 已保存"
                },
            )
        }
    }

    fun importCookieFromWeb(rawCookie: String) {
        val normalized = container.douyinCookieStore.saveCookie(rawCookie)
        _uiState.update {
            it.copy(
                cookieInput = normalized,
                actionMessage = if (normalized.isBlank()) {
                    "未从抖音 WebView 读取到 Cookie"
                } else {
                    "已从抖音 WebView 导入 Cookie"
                },
            )
        }
    }

    fun clearCookie() {
        container.douyinCookieStore.clearCookie()
        _uiState.update { it.copy(cookieInput = "", actionMessage = appContext.getString(R.string.settings_cookie_cleared)) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }
}

class DouyinSettingsViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DouyinSettingsViewModel(container) as T
    }
}
