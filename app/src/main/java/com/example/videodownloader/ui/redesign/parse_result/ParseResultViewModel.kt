package com.example.videodownloader.ui.redesign.parse_result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import com.example.videodownloader.di.AppContainer
import com.example.videodownloader.di.ParseResultPayload
import com.example.videodownloader.domain.model.VideoFormat
import com.example.videodownloader.domain.usecase.planDownloadFormatsForSaving
import com.example.videodownloader.ui.redesign.toRedesignParseResultUiState

class ParseResultViewModel(
    private val container: AppContainer,
    private val parseRecordId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ParseResultUiState())
    val uiState: StateFlow<ParseResultUiState> = _uiState.asStateFlow()

    private val _events = Channel<ParseResultUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var payload: ParseResultPayload? = null

    init {
        loadPayload()
    }

    fun onAction(action: ParseResultAction) {
        when (action) {
            is ParseResultAction.SelectResource -> {
                val safeIndex = action.index.coerceIn(0, (_uiState.value.resources.size - 1).coerceAtLeast(0))
                _uiState.value = payload?.toRedesignParseResultUiState(safeIndex) ?: _uiState.value.copy(currentIndex = safeIndex)
            }
            ParseResultAction.DownloadCurrent -> {
                downloadCurrent()
            }
            ParseResultAction.DownloadAll -> {
                downloadAll()
            }
            ParseResultAction.GoBack -> {
                viewModelScope.launch { _events.send(ParseResultUiEvent.NavigateBack) }
            }
        }
    }

    private fun loadPayload() {
        val current = container.parseResultStore.payload.value
        payload = current?.takeIf { it.parseRecordId == parseRecordId || parseRecordId.isBlank() }
        payload?.let {
            _uiState.value = it.toRedesignParseResultUiState()
        } ?: viewModelScope.launch {
            _events.send(ParseResultUiEvent.ShowToast("解析结果已过期，请重新解析"))
        }
    }

    private fun downloadCurrent() {
        val currentPayload = payload ?: run {
            viewModelScope.launch { _events.send(ParseResultUiEvent.ShowToast("没有可下载内容")) }
            return
        }
        val formats = currentPayload.parsedInfo.formats
        val selected = formats.getOrNull(_uiState.value.currentIndex.coerceIn(0, (formats.size - 1).coerceAtLeast(0)))
        if (selected == null || !selected.downloadable) {
            viewModelScope.launch { _events.send(ParseResultUiEvent.ShowToast("该资源暂不可下载")) }
            return
        }
        createTasks(listOf(selected), attachParseRecord = true)
    }

    private fun downloadAll() {
        val targets = payload?.parsedInfo?.formats.orEmpty().filter { it.downloadable }
        if (targets.isEmpty()) {
            viewModelScope.launch { _events.send(ParseResultUiEvent.ShowToast("没有可下载内容")) }
            return
        }
        createTasks(targets, attachParseRecord = true)
    }

    private fun createTasks(targets: List<VideoFormat>, attachParseRecord: Boolean) {
        val currentPayload = payload ?: return
        viewModelScope.launch {
            var successCount = 0
            var failedCount = 0
            val plannedTargets = planDownloadFormatsForSaving(currentPayload.parsedInfo.title, targets)
            val totalImageCount = plannedTargets.count {
                it.format.downloadable && it.format.ext.lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")
            }.takeIf { it > 0 } ?: currentPayload.parsedInfo.formats.count {
                it.downloadable && it.ext.lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")
            }.takeIf { it > 0 }

            plannedTargets.forEach { planned ->
                runCatching {
                    container.createDownloadTaskUseCase(
                        sourceUrl = currentPayload.sourceUrl,
                        title = planned.title,
                        coverUrl = planned.coverUrl ?: currentPayload.parsedInfo.coverUrl,
                        format = planned.format,
                        parseRecordId = if (attachParseRecord) currentPayload.parseRecordId else null,
                        totalImageCount = totalImageCount,
                    )
                }.onSuccess {
                    successCount += 1
                }.onFailure {
                    failedCount += 1
                }
            }

            val message = when {
                failedCount == 0 -> "已加入下载队列（${successCount}项）"
                successCount == 0 -> "下载创建失败，请稍后重试"
                else -> "已加入 ${successCount} 项，失败 ${failedCount} 项"
            }
            _events.send(ParseResultUiEvent.ShowToast(message))
            if (successCount > 0) {
                _events.send(ParseResultUiEvent.DownloadStarted)
            }
        }
    }
}

class ParseResultViewModelFactory(
    private val container: AppContainer,
    private val parseRecordId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ParseResultViewModel(container, parseRecordId) as T
    }
}
