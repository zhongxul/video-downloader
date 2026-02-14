package com.example.videodownloader.ui.screen.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.videodownloader.di.AppContainer
import com.example.videodownloader.domain.model.DownloadTask
import com.example.videodownloader.domain.model.DownloadTaskStatus
import com.example.videodownloader.domain.model.ParseRecord
import com.example.videodownloader.domain.model.ParseRecordStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class HistoryUiState(
    val parseRecords: List<ParseRecord> = emptyList(),
    val downloadTasks: List<DownloadTask> = emptyList(),
    val loading: Boolean = true,
    val actionMessage: String? = null,
    val shareTextPayload: String? = null,
    val downloadFilter: DownloadFilter = DownloadFilter.ALL,
    val parseManageMode: Boolean = false,
    val selectedParseIds: Set<String> = emptySet(),
    val downloadManageMode: Boolean = false,
    val selectedTaskIds: Set<String> = emptySet(),
)

enum class DownloadFilter {
    ALL,
    DOWNLOADING,
    FAILED,
}

class HistoryViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        observeHistory()
        startDownloadStatusSync()
    }

    fun clearMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }

    fun clearSharePayload() {
        _uiState.update { it.copy(shareTextPayload = null) }
    }

    fun setDownloadFilter(filter: DownloadFilter) {
        _uiState.update { it.copy(downloadFilter = filter, selectedTaskIds = emptySet()) }
    }

    fun filteredDownloadTasks(): List<DownloadTask> {
        val tasks = uiState.value.downloadTasks
            .filterNot { it.status == DownloadTaskStatus.SUCCESS }
        return when (uiState.value.downloadFilter) {
            DownloadFilter.ALL -> tasks
            DownloadFilter.DOWNLOADING -> tasks.filter {
                it.status == DownloadTaskStatus.QUEUED || it.status == DownloadTaskStatus.DOWNLOADING
            }
            DownloadFilter.FAILED -> tasks.filter { it.status == DownloadTaskStatus.FAILED }
        }
    }

    fun toggleParseManageMode() {
        _uiState.update {
            it.copy(
                parseManageMode = !it.parseManageMode,
                selectedParseIds = if (it.parseManageMode) emptySet() else it.selectedParseIds,
            )
        }
    }

    fun toggleDownloadManageMode() {
        _uiState.update {
            it.copy(
                downloadManageMode = !it.downloadManageMode,
                selectedTaskIds = if (it.downloadManageMode) emptySet() else it.selectedTaskIds,
            )
        }
    }

    fun toggleParseSelection(recordId: String) {
        val selected = uiState.value.selectedParseIds.toMutableSet()
        if (!selected.add(recordId)) {
            selected.remove(recordId)
        }
        _uiState.update { it.copy(selectedParseIds = selected) }
    }

    fun enterParseManageModeWithSelection(recordId: String) {
        _uiState.update { current ->
            val nextSelection = current.selectedParseIds.toMutableSet()
            if (!nextSelection.add(recordId)) {
                nextSelection.remove(recordId)
            }
            current.copy(
                parseManageMode = true,
                selectedParseIds = nextSelection,
            )
        }
    }

    fun toggleTaskSelection(taskId: String) {
        val selected = uiState.value.selectedTaskIds.toMutableSet()
        if (!selected.add(taskId)) {
            selected.remove(taskId)
        }
        _uiState.update { it.copy(selectedTaskIds = selected) }
    }

    fun enterDownloadManageModeWithSelection(taskId: String) {
        _uiState.update { current ->
            val nextSelection = current.selectedTaskIds.toMutableSet()
            if (!nextSelection.add(taskId)) {
                nextSelection.remove(taskId)
            }
            current.copy(
                downloadManageMode = true,
                selectedTaskIds = nextSelection,
            )
        }
    }

    fun toggleSelectAllParseRecords() {
        val records = uiState.value.parseRecords
        if (records.isEmpty()) {
            _uiState.update { it.copy(actionMessage = "当前没有可选的解析记录") }
            return
        }

        val selectedIds = uiState.value.selectedParseIds
        val allSelected = records.all { selectedIds.contains(it.id) }
        val nextSelected = if (allSelected) {
            emptySet()
        } else {
            records.map { it.id }.toSet()
        }
        _uiState.update { it.copy(selectedParseIds = nextSelected) }
    }

    fun toggleSelectAllDownloadTasks() {
        val visibleTasks = filteredDownloadTasks()
        if (visibleTasks.isEmpty()) {
            _uiState.update { it.copy(actionMessage = "当前筛选下没有可选的下载记录") }
            return
        }

        val visibleIds = visibleTasks.map { it.id }.toSet()
        val selectedIds = uiState.value.selectedTaskIds
        val allSelected = visibleIds.all { selectedIds.contains(it) }
        val nextSelected = if (allSelected) {
            selectedIds - visibleIds
        } else {
            selectedIds + visibleIds
        }
        _uiState.update { it.copy(selectedTaskIds = nextSelected) }
    }

    fun shareSelectedParseRecords() {
        val selectedIds = uiState.value.selectedParseIds
        if (selectedIds.isEmpty()) {
            _uiState.update { it.copy(actionMessage = "请先选择解析记录") }
            return
        }

        val selected = uiState.value.parseRecords.filter { selectedIds.contains(it.id) }
        val payload = selected.joinToString("\n") { record ->
            val link = record.resolvedUrl ?: "(无链接)"
            "$link\n结果：${record.status.toDisplayName()} ${record.message.orEmpty()}"
        }
        _uiState.update { it.copy(shareTextPayload = payload) }
    }

    fun shareSelectedDownloadTasks() {
        val selectedIds = uiState.value.selectedTaskIds
        if (selectedIds.isEmpty()) {
            _uiState.update { it.copy(actionMessage = "请先选择下载记录") }
            return
        }

        val selected = filteredDownloadTasks().filter { selectedIds.contains(it.id) }
        val payload = selected.joinToString("\n") { task ->
            "${task.sourceUrl}\n状态：${task.status.toDisplayName()}"
        }
        _uiState.update { it.copy(shareTextPayload = payload) }
    }

    fun deleteSelectedParseRecords() {
        val selectedIds = uiState.value.selectedParseIds.toList()
        if (selectedIds.isEmpty()) {
            _uiState.update { it.copy(actionMessage = "请先选择解析记录") }
            return
        }

        viewModelScope.launch {
            runCatching {
                container.parseRecordRepository.deleteRecords(selectedIds)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        actionMessage = "已删除 ${selectedIds.size} 条解析记录",
                        selectedParseIds = emptySet(),
                        parseManageMode = false,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { it.copy(actionMessage = throwable.message ?: "删除解析记录失败") }
            }
        }
    }

    fun deleteSelectedDownloadTasks() {
        val selectedIds = uiState.value.selectedTaskIds.toList()
        if (selectedIds.isEmpty()) {
            _uiState.update { it.copy(actionMessage = "请先选择下载记录") }
            return
        }

        viewModelScope.launch {
            runCatching {
                val tasks = container.repository.getTasks(selectedIds)
                tasks.forEach { task ->
                    if (task.status == DownloadTaskStatus.QUEUED || task.status == DownloadTaskStatus.DOWNLOADING) {
                        task.externalDownloadId?.let { id ->
                            container.downloadGateway.cancelDownload(id)
                        }
                    }
                }
                container.repository.deleteTasks(selectedIds)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        actionMessage = "已删除 ${selectedIds.size} 条下载记录",
                        selectedTaskIds = emptySet(),
                        downloadManageMode = false,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { it.copy(actionMessage = throwable.message ?: "删除下载记录失败") }
            }
        }
    }

    fun retryTask(taskId: String) {
        viewModelScope.launch {
            runCatching {
                container.retryDownloadTaskUseCase(taskId)
            }.onSuccess {
                _uiState.update { it.copy(actionMessage = "已重新加入下载队列") }
            }.onFailure { throwable ->
                _uiState.update { it.copy(actionMessage = throwable.message ?: "重试失败") }
            }
        }
    }

    private fun observeHistory() {
        viewModelScope.launch {
            combine(
                container.observeHistoryUseCase(),
                container.parseRecordRepository.observeRecords(),
            ) { tasks, parseRecords ->
                tasks to parseRecords
            }.collect { (tasks, parseRecords) ->
                _uiState.update {
                    it.copy(
                        parseRecords = parseRecords,
                        downloadTasks = tasks,
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

class HistoryViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HistoryViewModel(container) as T
    }
}

private fun DownloadTaskStatus.toDisplayName(): String {
    return when (this) {
        DownloadTaskStatus.QUEUED -> "排队中"
        DownloadTaskStatus.DOWNLOADING -> "下载中"
        DownloadTaskStatus.SUCCESS -> "成功"
        DownloadTaskStatus.FAILED -> "失败"
        DownloadTaskStatus.CANCELED -> "已取消"
    }
}

private fun ParseRecordStatus.toDisplayName(): String {
    return when (this) {
        ParseRecordStatus.PARSED -> "已解析"
        ParseRecordStatus.PARSE_FAILED -> "解析失败"
        ParseRecordStatus.QUEUED -> "排队中"
        ParseRecordStatus.DOWNLOADING -> "下载中"
        ParseRecordStatus.SUCCESS -> "下载成功"
        ParseRecordStatus.FAILED -> "下载失败"
        ParseRecordStatus.CANCELED -> "已取消"
    }
}
