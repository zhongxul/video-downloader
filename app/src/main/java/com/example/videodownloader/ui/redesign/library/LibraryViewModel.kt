package com.example.videodownloader.ui.redesign.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.videodownloader.di.AppContainer
import com.example.videodownloader.ui.redesign.buildRedesignLibraryUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _events = Channel<LibraryUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        observeLibrary()
        startDownloadStatusSync()
    }

    fun onAction(action: LibraryAction) {
        when (action) {
            is LibraryAction.SwitchTab -> {
                _uiState.value = _uiState.value.copy(currentTab = action.tab)
            }
            is LibraryAction.OpenDetail -> {
                viewModelScope.launch { _events.send(LibraryUiEvent.NavigateToDetail(action.taskId)) }
            }
            is LibraryAction.OpenParseResult -> {
                _uiState.value = _uiState.value.copy(
                    expandedParseIds = _uiState.value.expandedParseIds.toggle(action.parseRecordId),
                )
            }
            is LibraryAction.EnterCompletedManage -> {
                _uiState.value = _uiState.value.copy(
                    completedManageMode = true,
                    selectedCompletedIds = setOf(action.itemId),
                )
            }
            is LibraryAction.ToggleCompletedSelection -> {
                val selected = _uiState.value.selectedCompletedIds.toggle(action.itemId)
                _uiState.value = _uiState.value.copy(selectedCompletedIds = selected)
            }
            LibraryAction.ToggleSelectAllCompleted -> {
                val ids = _uiState.value.completedItems.map { it.id }.toSet()
                val allSelected = ids.isNotEmpty() && ids.all { it in _uiState.value.selectedCompletedIds }
                _uiState.value = _uiState.value.copy(selectedCompletedIds = if (allSelected) emptySet() else ids)
            }
            LibraryAction.DeleteSelectedCompleted -> {
                deleteCompletedSelection()
            }
            is LibraryAction.EnterParseManage -> {
                _uiState.value = _uiState.value.copy(
                    parseManageMode = true,
                    selectedParseIds = setOf(action.recordId),
                )
            }
            is LibraryAction.ToggleParseSelection -> {
                val selected = _uiState.value.selectedParseIds.toggle(action.recordId)
                _uiState.value = _uiState.value.copy(selectedParseIds = selected)
            }
            LibraryAction.ToggleSelectAllParseRecords -> {
                val ids = _uiState.value.parseRecords.map { it.id }.toSet()
                val allSelected = ids.isNotEmpty() && ids.all { it in _uiState.value.selectedParseIds }
                _uiState.value = _uiState.value.copy(selectedParseIds = if (allSelected) emptySet() else ids)
            }
            LibraryAction.DeleteSelectedParseRecords -> {
                deleteParseSelection()
            }
            LibraryAction.ExitManageMode -> {
                _uiState.value = _uiState.value.copy(
                    completedManageMode = false,
                    selectedCompletedIds = emptySet(),
                    parseManageMode = false,
                    selectedParseIds = emptySet(),
                    expandedParseIds = _uiState.value.expandedParseIds,
                )
            }
        }
    }

    private fun observeLibrary() {
        viewModelScope.launch {
            combine(
                container.repository.observeTasks(),
                container.parseRecordRepository.observeRecords(),
            ) { tasks, records ->
                val previous = _uiState.value
                buildRedesignLibraryUiState(
                    tasks = tasks,
                    records = records,
                    currentTab = previous.currentTab,
                ).copy(
                    completedManageMode = previous.completedManageMode,
                    selectedCompletedIds = previous.selectedCompletedIds.intersect(tasks.map { it.parseRecordId ?: it.id }.toSet()),
                    parseManageMode = previous.parseManageMode,
                    selectedParseIds = previous.selectedParseIds.intersect(records.map { it.id }.toSet()),
                    expandedParseIds = previous.expandedParseIds.intersect(records.map { it.id }.toSet()),
                )
            }.collect { state ->
                _uiState.value = state
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

    private fun deleteCompletedSelection() {
        val selectedIds = _uiState.value.selectedCompletedIds
        val taskIds = _uiState.value.completedItems
            .filter { it.id in selectedIds }
            .flatMap { it.taskIds.ifEmpty { listOf(it.id) } }
        if (taskIds.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                val tasks = container.repository.getTasks(taskIds)
                tasks.forEach { task ->
                    task.externalDownloadId?.let { container.downloadGateway.cancelDownload(it) }
                }
                container.repository.deleteTasks(taskIds)
            }.onSuccess {
                _events.send(LibraryUiEvent.ShowToast("已删除 ${taskIds.size} 个任务"))
                _uiState.value = _uiState.value.copy(completedManageMode = false, selectedCompletedIds = emptySet())
            }.onFailure {
                _events.send(LibraryUiEvent.ShowToast(it.message ?: "删除失败"))
            }
        }
    }

    private fun deleteParseSelection() {
        val selectedIds = _uiState.value.selectedParseIds.toList()
        if (selectedIds.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                container.parseRecordRepository.deleteRecords(selectedIds)
            }.onSuccess {
                _events.send(LibraryUiEvent.ShowToast("已删除 ${selectedIds.size} 条解析记录"))
                _uiState.value = _uiState.value.copy(parseManageMode = false, selectedParseIds = emptySet())
            }.onFailure {
                _events.send(LibraryUiEvent.ShowToast(it.message ?: "删除失败"))
            }
        }
    }

    private fun Set<String>.toggle(value: String): Set<String> {
        return if (value in this) this - value else this + value
    }
}

class LibraryViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return LibraryViewModel(container) as T
    }
}
