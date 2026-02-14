package com.example.videodownloader.ui.screen.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.videodownloader.di.AppContainer
import com.example.videodownloader.domain.model.DownloadTask
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DownloadDetailUiState(
    val task: DownloadTask? = null,
    val loading: Boolean = true,
    val actionMessage: String? = null,
)

class DownloadDetailViewModel(
    private val container: AppContainer,
    private val taskId: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DownloadDetailUiState())
    val uiState: StateFlow<DownloadDetailUiState> = _uiState.asStateFlow()

    init {
        observeTask()
        startDownloadStatusSync()
    }

    fun clearMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }

    fun pauseTask() {
        viewModelScope.launch {
            runCatching {
                container.pauseDownloadTaskUseCase(taskId)
            }.onSuccess {
                _uiState.update { it.copy(actionMessage = "任务已暂停") }
            }.onFailure { throwable ->
                _uiState.update { it.copy(actionMessage = throwable.message ?: "暂停失败") }
            }
        }
    }

    fun resumeTask() {
        viewModelScope.launch {
            runCatching {
                container.resumeDownloadTaskUseCase(taskId)
            }.onSuccess {
                _uiState.update { it.copy(actionMessage = "任务已恢复") }
            }.onFailure { throwable ->
                _uiState.update { it.copy(actionMessage = throwable.message ?: "继续失败") }
            }
        }
    }

    fun retryTask() {
        viewModelScope.launch {
            runCatching {
                container.retryDownloadTaskUseCase(taskId)
            }.onSuccess {
                _uiState.update { it.copy(actionMessage = "已创建重试任务，请返回历史页查看") }
            }.onFailure { throwable ->
                _uiState.update { it.copy(actionMessage = throwable.message ?: "重试失败") }
            }
        }
    }

    private fun observeTask() {
        viewModelScope.launch {
            container.observeTaskDetailUseCase(taskId).collect { task ->
                _uiState.update {
                    it.copy(
                        task = task,
                        loading = false,
                    )
                }
            }
        }
    }

    private fun startDownloadStatusSync() {
        viewModelScope.launch {
            while (isActive) {
                runCatching {
                    container.syncDownloadStatusUseCase()
                }
                delay(1500L)
            }
        }
    }
}

class DownloadDetailViewModelFactory(
    private val container: AppContainer,
    private val taskId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DownloadDetailViewModel(container, taskId) as T
    }
}
