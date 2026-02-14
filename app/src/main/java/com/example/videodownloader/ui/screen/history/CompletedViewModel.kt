package com.example.videodownloader.ui.screen.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.videodownloader.di.AppContainer
import com.example.videodownloader.domain.model.DownloadTask
import com.example.videodownloader.domain.model.DownloadTaskStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class CompletedUiState(
    val tasks: List<DownloadTask> = emptyList(),
    val loading: Boolean = true,
    val manageMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val actionMessage: String? = null,
)

class CompletedViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CompletedUiState())
    val uiState: StateFlow<CompletedUiState> = _uiState.asStateFlow()

    init {
        observeCompleted()
    }

    fun clearMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }

    fun setMessage(message: String) {
        _uiState.update { it.copy(actionMessage = message) }
    }

    fun toggleManageMode() {
        _uiState.update {
            it.copy(
                manageMode = !it.manageMode,
                selectedIds = if (it.manageMode) emptySet() else it.selectedIds,
            )
        }
    }

    fun toggleSelection(taskId: String) {
        val selected = uiState.value.selectedIds.toMutableSet()
        if (!selected.add(taskId)) {
            selected.remove(taskId)
        }
        _uiState.update { it.copy(selectedIds = selected) }
    }

    fun selectedTasks(): List<DownloadTask> {
        val selectedIds = uiState.value.selectedIds
        return uiState.value.tasks.filter { selectedIds.contains(it.id) }
    }

    fun deleteSelected() {
        val selected = selectedTasks()
        if (selected.isEmpty()) {
            _uiState.update { it.copy(actionMessage = "请先选择要删除的视频") }
            return
        }

        viewModelScope.launch {
            runCatching {
                selected.forEach { task ->
                    deleteFileIfExists(task.saveUri)
                }
                container.repository.deleteTasks(selected.map { it.id })
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        actionMessage = "已删除 ${selected.size} 个视频",
                        manageMode = false,
                        selectedIds = emptySet(),
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { it.copy(actionMessage = throwable.message ?: "删除失败") }
            }
        }
    }

    private fun observeCompleted() {
        viewModelScope.launch {
            container.observeHistoryUseCase().collect { tasks ->
                _uiState.update {
                    it.copy(
                        tasks = tasks.filter { task -> task.status == DownloadTaskStatus.SUCCESS },
                        loading = false,
                    )
                }
            }
        }
    }

    private fun deleteFileIfExists(saveUri: String?) {
        val path = saveUri
            ?.takeIf { it.startsWith("file://", ignoreCase = true) }
            ?.removePrefix("file://")
            ?: return
        val file = File(path)
        if (file.exists()) {
            file.delete()
        }
    }
}

class CompletedViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CompletedViewModel(container) as T
    }
}
