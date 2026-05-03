package com.example.videodownloader.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.videodownloader.R
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
    private val appContext = container.appContext
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
                    normalized.isBlank() -> appContext.getString(R.string.settings_cookie_cleared)
                    authToken.isNullOrBlank() || ct0.isNullOrBlank() -> appContext.getString(R.string.settings_cookie_saved_missing_fields)
                    else -> appContext.getString(R.string.settings_cookie_saved)
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
                    appContext.getString(R.string.settings_cookie_not_found_from_web)
                } else {
                    appContext.getString(R.string.settings_cookie_imported_from_web)
                },
            )
        }
    }

    fun clearCookie() {
        container.xCookieStore.clearCookie()
        _uiState.update { it.copy(cookieInput = "", actionMessage = appContext.getString(R.string.settings_cookie_cleared)) }
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
