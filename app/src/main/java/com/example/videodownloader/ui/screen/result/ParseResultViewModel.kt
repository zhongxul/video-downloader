package com.example.videodownloader.ui.screen.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.videodownloader.di.AppContainer
import com.example.videodownloader.domain.model.ParsedVideoInfo
import com.example.videodownloader.domain.model.VideoFormat
import com.example.videodownloader.domain.usecase.planDownloadFormatsForSaving
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ParseResultUiState(
    val loading: Boolean = true,
    val sourceUrl: String? = null,
    val parseRecordId: String? = null,
    val parsedInfo: ParsedVideoInfo? = null,
    val recommendedFormatId: String? = null,
    val selectedIndex: Int = 0,
    val isSubmitting: Boolean = false,
    val actionMessage: String? = null,
)

private val imageExts = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")

class ParseResultViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ParseResultUiState())
    val uiState: StateFlow<ParseResultUiState> = _uiState.asStateFlow()

    init {
        val payload = container.parseResultStore.payload.value
        _uiState.value = ParseResultUiState(
            loading = false,
            sourceUrl = payload?.sourceUrl,
            parseRecordId = payload?.parseRecordId,
            parsedInfo = payload?.parsedInfo,
            recommendedFormatId = payload?.recommendedFormatId,
            selectedIndex = 0,
        )
    }

    fun clearMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }

    fun clearResultPayload() {
        container.parseResultStore.clear()
    }

    fun selectIndex(index: Int) {
        val total = uiState.value.parsedInfo?.formats?.size ?: 0
        if (total <= 0) return
        _uiState.update { it.copy(selectedIndex = index.coerceIn(0, total - 1)) }
    }

    fun downloadCurrent() {
        val state = uiState.value
        val info = state.parsedInfo ?: run {
            _uiState.update { it.copy(actionMessage = "没有可下载内容") }
            return
        }
        val formats = info.formats
        if (formats.isEmpty()) {
            _uiState.update { it.copy(actionMessage = "没有可下载内容") }
            return
        }

        val selected = formats[state.selectedIndex.coerceIn(0, formats.lastIndex)]
        if (!selected.downloadable) {
            _uiState.update { it.copy(actionMessage = "该选项暂不可下载") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            runCatching {
                val planned = planDownloadFormatsForSaving(info.title, listOf(selected))
                val totalImageCount = planned.count { it.format.ext.lowercase() in imageExts }.takeIf { it > 0 }
                planned.forEach {
                    container.createDownloadTaskUseCase(
                        sourceUrl = state.sourceUrl.orEmpty(),
                        title = it.title,
                        coverUrl = it.coverUrl ?: info.coverUrl,
                        format = it.format,
                        parseRecordId = state.parseRecordId,
                        totalImageCount = totalImageCount,
                    )
                }
            }.onSuccess {
                _uiState.update { it.copy(isSubmitting = false, actionMessage = "已加入下载队列") }
            }.onFailure { throwable ->
                _uiState.update { it.copy(isSubmitting = false, actionMessage = throwable.message ?: "下载失败") }
            }
        }
    }

    fun downloadAll() {
        val state = uiState.value
        val info = state.parsedInfo ?: run {
            _uiState.update { it.copy(actionMessage = "没有可下载内容") }
            return
        }
        val targets = info.formats.filter { it.downloadable }
        if (targets.isEmpty()) {
            _uiState.update { it.copy(actionMessage = "没有可下载内容") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            var successCount = 0
            var failedCount = 0
            val plannedTargets = planDownloadFormatsForSaving(info.title, targets)
            val totalImageCount = plannedTargets.count { it.format.ext.lowercase() in imageExts }.takeIf { it > 0 }
            plannedTargets.forEach { planned ->
                runCatching {
                    container.createDownloadTaskUseCase(
                        sourceUrl = state.sourceUrl.orEmpty(),
                        title = planned.title,
                        coverUrl = planned.coverUrl ?: info.coverUrl,
                        format = planned.format,
                        parseRecordId = null,
                        totalImageCount = totalImageCount,
                    )
                }.onSuccess {
                    successCount += 1
                }.onFailure {
                    failedCount += 1
                }
            }
            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    actionMessage = buildBatchDownloadMessage(successCount = successCount, failedCount = failedCount),
                )
            }
        }
    }

    fun currentFormat(): VideoFormat? {
        val state = uiState.value
        val formats = state.parsedInfo?.formats.orEmpty()
        if (formats.isEmpty()) return null
        return formats[state.selectedIndex.coerceIn(0, formats.lastIndex)]
    }
}

internal fun buildBatchDownloadMessage(successCount: Int, failedCount: Int): String {
    return when {
        failedCount == 0 -> "全部媒体已加入下载队列（${successCount}项）"
        successCount == 0 -> "批量下载失败，请稍后重试"
        else -> "已加入 ${successCount} 项，失败 ${failedCount} 项"
    }
}

class ParseResultViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ParseResultViewModel(container) as T
    }
}
