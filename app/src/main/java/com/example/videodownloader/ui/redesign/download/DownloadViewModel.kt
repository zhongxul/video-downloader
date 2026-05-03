package com.example.videodownloader.ui.redesign.download

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.videodownloader.di.AppContainer
import com.example.videodownloader.di.ParseResultPayload
import com.example.videodownloader.domain.model.ParseRecord
import com.example.videodownloader.domain.model.ParseRecordStatus
import com.example.videodownloader.ui.redesign.buildQueueSummary
import com.example.videodownloader.ui.redesign.buildRecentParseInfo
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class DownloadViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadUiState())
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    private val _events = Channel<DownloadUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        observeSummary()
    }

    fun onAction(action: DownloadAction) {
        when (action) {
            is DownloadAction.LinkChanged -> {
                _uiState.value = _uiState.value.copy(linkInput = action.value)
            }
            DownloadAction.PasteFromClipboard -> {
                pasteFromClipboard()
            }
            DownloadAction.StartParse -> {
                startParse()
            }
            DownloadAction.DismissError -> {
                _uiState.value = _uiState.value.copy(parseError = null)
            }
            DownloadAction.NavigateToLibrary -> {
                viewModelScope.launch {
                    _events.send(DownloadUiEvent.ShowToast("请切换到资源库查看全部任务"))
                }
            }
        }
    }

    private fun pasteFromClipboard() {
        val clipboard = container.appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(container.appContext)?.toString().orEmpty()
        if (text.isBlank()) {
            viewModelScope.launch { _events.send(DownloadUiEvent.ShowToast("剪贴板没有可用链接")) }
            return
        }
        _uiState.update { it.copy(linkInput = text, parseError = null) }
    }

    private fun startParse() {
        if (uiState.value.isParsing) return
        val rawInput = uiState.value.linkInput.trim()
        if (rawInput.isBlank()) {
            _uiState.update { it.copy(parseError = "请先粘贴分享链接") }
            return
        }

        viewModelScope.launch {
            container.parseResultStore.clear()
            _uiState.update { it.copy(isParsing = true, parseError = null) }
            runCatching {
                val resolvedUrl = container.parseLinkUseCase.resolveUrl(rawInput)
                val info = container.parseLinkUseCase(rawInput)
                val recordId = saveParseRecord(
                    rawInput = rawInput,
                    resolvedUrl = resolvedUrl,
                    title = info.title,
                    coverUrl = info.coverUrl,
                    status = ParseRecordStatus.PARSED,
                    message = "解析成功",
                )
                ParseResultPayload(
                    parsedInfo = info,
                    sourceUrl = resolvedUrl,
                    parseRecordId = recordId,
                    recommendedFormatId = null,
                )
            }.onSuccess { payload ->
                container.parseResultStore.save(payload)
                _uiState.update {
                    it.copy(
                        linkInput = payload.sourceUrl,
                        isParsing = false,
                        parseError = null,
                    )
                }
                _events.send(DownloadUiEvent.NavigateToParseResult(payload.parseRecordId.orEmpty()))
            }.onFailure { throwable ->
                val resolvedUrl = runCatching { container.parseLinkUseCase.resolveUrl(rawInput) }.getOrNull()
                runCatching {
                    saveParseRecord(
                        rawInput = rawInput,
                        resolvedUrl = resolvedUrl,
                        title = null,
                        coverUrl = null,
                        status = ParseRecordStatus.PARSE_FAILED,
                        message = throwable.message ?: "解析失败",
                    )
                }
                _uiState.update {
                    it.copy(
                        isParsing = false,
                        parseError = throwable.message ?: "解析失败，请稍后重试",
                    )
                }
            }
        }
    }

    private suspend fun saveParseRecord(
        rawInput: String,
        resolvedUrl: String?,
        title: String?,
        coverUrl: String?,
        status: ParseRecordStatus,
        message: String?,
    ): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        container.parseRecordRepository.insertRecord(
            ParseRecord(
                id = id,
                rawInput = rawInput,
                resolvedUrl = resolvedUrl,
                title = title,
                coverUrl = coverUrl,
                status = status,
                message = message,
                selectedFormatLabel = null,
                selectedExt = null,
                taskId = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return id
    }

    private fun observeSummary() {
        viewModelScope.launch {
            combine(
                container.repository.observeTasks(),
                container.parseRecordRepository.observeRecords(),
            ) { tasks, records ->
                val todayStart = java.time.LocalDate.now()
                    .atStartOfDay(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                val todayCompleted = tasks.count {
                    it.status == com.example.videodownloader.domain.model.DownloadTaskStatus.SUCCESS &&
                        it.updatedAt >= todayStart
                }
                DownloadUiState(
                    linkInput = _uiState.value.linkInput,
                    isParsing = _uiState.value.isParsing,
                    parseError = _uiState.value.parseError,
                    todayCompleted = todayCompleted,
                    queuedCount = tasks.count {
                        it.status == com.example.videodownloader.domain.model.DownloadTaskStatus.QUEUED ||
                            it.status == com.example.videodownloader.domain.model.DownloadTaskStatus.DOWNLOADING
                    },
                    recentParse = buildRecentParseInfo(records),
                    queueSummary = buildQueueSummary(tasks),
                )
            }.collect { next ->
                _uiState.value = next
            }
        }
    }
}

class DownloadViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DownloadViewModel(container) as T
    }
}
