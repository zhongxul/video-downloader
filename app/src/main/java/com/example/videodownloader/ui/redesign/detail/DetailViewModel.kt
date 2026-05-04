package com.example.videodownloader.ui.redesign.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.videodownloader.di.AppContainer
import com.example.videodownloader.domain.model.DownloadTask
import com.example.videodownloader.domain.model.DownloadTaskStatus
import com.example.videodownloader.ui.redesign.toRedesignDetailUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DetailViewModel(
    private val container: AppContainer,
    private val taskId: String,
    private val successOnly: Boolean = false,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState(taskId = taskId))
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private val _events = Channel<DetailUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private var currentTasks: List<DownloadTask> = emptyList()
    private var visibleTasks: List<DownloadTask> = emptyList()

    init {
        observeTask()
        startDownloadStatusSync()
    }

    fun onAction(action: DetailAction) {
        when (action) {
            is DetailAction.SelectMedia -> {
                _uiState.value = currentTasks.toRedesignDetailUiState(taskId, action.index, successOnly)
            }
            is DetailAction.OpenMedia -> {
                openMediaAt(action.index)
            }
            DetailAction.GoBack -> {
                viewModelScope.launch { _events.send(DetailUiEvent.NavigateBack) }
            }
            DetailAction.OpenContent -> {
                openMediaAt(_uiState.value.currentIndex)
            }
            DetailAction.DeleteRecord -> {
                viewModelScope.launch {
                    val targets = visibleTasks.ifEmpty { container.repository.getTask(taskId)?.let { listOf(it) }.orEmpty() }
                    runCatching {
                        targets.forEach { task ->
                            task.externalDownloadId?.let { container.downloadGateway.cancelDownload(it) }
                        }
                        container.repository.deleteTasks(targets.map { it.id })
                    }
                        .onSuccess {
                            _events.send(DetailUiEvent.ShowToast("记录已删除"))
                            _events.send(DetailUiEvent.NavigateBack)
                        }
                        .onFailure { _events.send(DetailUiEvent.ShowToast(it.message ?: "删除失败")) }
                }
            }
            DetailAction.RetryDownload -> {
                viewModelScope.launch {
                    val targets = currentTasks.filter { it.status == DownloadTaskStatus.FAILED }.ifEmpty { currentTasks.take(1) }
                    runCatching {
                        targets.forEach { container.retryDownloadTaskUseCase(it.id) }
                    }.onSuccess { _events.send(DetailUiEvent.ShowToast("已重新加入下载队列")) }
                        .onFailure { _events.send(DetailUiEvent.ShowToast(it.message ?: "重试失败")) }
                }
            }
            DetailAction.PauseDownload -> {
                viewModelScope.launch {
                    val targets = currentTasks.filter {
                        it.status == DownloadTaskStatus.QUEUED || it.status == DownloadTaskStatus.DOWNLOADING
                    }.ifEmpty { currentTasks.take(1) }
                    runCatching {
                        targets.forEach { container.pauseDownloadTaskUseCase(it.id) }
                    }
                        .onSuccess { _events.send(DetailUiEvent.ShowToast("任务已暂停")) }
                        .onFailure { _events.send(DetailUiEvent.ShowToast(it.message ?: "暂停失败")) }
                }
            }
            DetailAction.ResumeDownload -> {
                viewModelScope.launch {
                    val targets = currentTasks.filter { it.status == DownloadTaskStatus.CANCELED }.ifEmpty { currentTasks.take(1) }
                    runCatching {
                        targets.forEach { container.resumeDownloadTaskUseCase(it.id) }
                    }
                        .onSuccess { _events.send(DetailUiEvent.ShowToast("任务已恢复")) }
                        .onFailure { _events.send(DetailUiEvent.ShowToast(it.message ?: "恢复失败")) }
                }
            }
            DetailAction.ShowMore -> {
                viewModelScope.launch { _events.send(DetailUiEvent.ShowToast("更多操作后续接入")) }
            }
        }
    }

    private fun openMediaAt(index: Int) {
        val state = _uiState.value
        val currentItem = state.mediaItems.getOrNull(index)
        val path = currentItem?.saveUri
            ?: state.metaItems.firstOrNull { it.label == "保存路径" }?.value.orEmpty()
        viewModelScope.launch {
            if (path.isBlank() || path == "尚未生成本地文件") {
                _events.send(DetailUiEvent.ShowToast("本地文件还不可用"))
            } else {
                _events.send(DetailUiEvent.OpenFile(path, currentItem?.ext.toMimeType()))
            }
        }
    }

    private fun observeTask() {
        viewModelScope.launch {
            container.repository.observeTasks().collect { tasks ->
                currentTasks = tasks.filter { it.id == taskId || it.parseRecordId == taskId }
                visibleTasks = if (successOnly) {
                    currentTasks.filter { it.status == DownloadTaskStatus.SUCCESS }.ifEmpty { currentTasks }
                } else {
                    currentTasks
                }
                _uiState.value = currentTasks.toRedesignDetailUiState(taskId, _uiState.value.currentIndex, successOnly)
            }
        }
    }

    private fun startDownloadStatusSync() {
        viewModelScope.launch {
            while (isActive) {
                runCatching { container.syncDownloadStatusUseCase() }
                delay(1500L)
            }
        }
    }

    private fun String?.toMimeType(): String {
        return when (this?.lowercase()) {
            "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic" -> "image/*"
            "mp4", "m4v", "mov", "mkv", "webm", "ts" -> "video/*"
            else -> "*/*"
        }
    }
}

class DetailViewModelFactory(
    private val container: AppContainer,
    private val taskId: String,
    private val successOnly: Boolean = false,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DetailViewModel(container, taskId, successOnly) as T
    }
}
