package com.example.videodownloader.ui.screen.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.videodownloader.R
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
import java.io.File
import com.example.videodownloader.ui.downloadTaskStatusText
import com.example.videodownloader.ui.parseRecordStatusText

enum class HistoryMainTab {
    DOWNLOAD,
    COMPLETED,
    PARSE_RECORDS,
}

enum class DownloadSubTab {
    DOWNLOADING,
    FAILED,
}

data class DownloadGroupUiModel(
    val groupKey: String,
    val title: String,
    val sourceUrl: String,
    val displayItems: List<DownloadTask>,
    val totalItems: Int,
    val isSingle: Boolean,
)

data class HistoryUiState(
    val parseRecords: List<ParseRecord> = emptyList(),
    val downloadTasks: List<DownloadTask> = emptyList(),
    val loading: Boolean = true,
    val actionMessage: String? = null,
    val shareTextPayload: String? = null,
    val mainTab: HistoryMainTab = HistoryMainTab.DOWNLOAD,
    val downloadSubTab: DownloadSubTab = DownloadSubTab.DOWNLOADING,
    val expandedGroupKeys: Set<String> = emptySet(),
    val parseManageMode: Boolean = false,
    val selectedParseIds: Set<String> = emptySet(),
    val downloadManageMode: Boolean = false,
    val selectedTaskIds: Set<String> = emptySet(),
    val completedManageMode: Boolean = false,
    val selectedCompletedIds: Set<String> = emptySet(),
    val shareCompletedToken: Long? = null,
)

class HistoryViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val appContext = container.appContext
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        observeHistory()
        startDownloadStatusSync()
    }

    fun clearMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }

    fun setMessage(message: String) {
        _uiState.update { it.copy(actionMessage = message) }
    }

    fun clearSharePayload() {
        _uiState.update { it.copy(shareTextPayload = null) }
    }

    fun consumeCompletedShareToken() {
        _uiState.update { it.copy(shareCompletedToken = null) }
    }

    fun setMainTab(tab: HistoryMainTab) {
        _uiState.update { it.copy(mainTab = tab) }
    }

    fun setDownloadSubTab(tab: DownloadSubTab) {
        _uiState.update {
            it.copy(
                downloadSubTab = tab,
                selectedTaskIds = emptySet(),
            )
        }
    }

    fun toggleGroupExpanded(groupKey: String) {
        val expanded = uiState.value.expandedGroupKeys.toMutableSet()
        if (!expanded.add(groupKey)) {
            expanded.remove(groupKey)
        }
        _uiState.update { it.copy(expandedGroupKeys = expanded) }
    }

    fun isGroupExpanded(groupKey: String): Boolean {
        return uiState.value.expandedGroupKeys.contains(groupKey)
    }

    fun currentDownloadGroups(): List<DownloadGroupUiModel> {
        return buildDownloadGroupsForUi(
            tasks = uiState.value.downloadTasks,
            subTab = uiState.value.downloadSubTab,
        )
    }

    fun completedTasks(): List<DownloadTask> {
        return uiState.value.downloadTasks
            .filter { it.status == DownloadTaskStatus.SUCCESS }
            .sortedByDescending { it.updatedAt }
    }

    fun toggleCompletedManageMode() {
        _uiState.update {
            it.copy(
                completedManageMode = !it.completedManageMode,
                selectedCompletedIds = if (it.completedManageMode) emptySet() else it.selectedCompletedIds,
            )
        }
    }

    fun toggleCompletedSelection(taskId: String) {
        val selected = uiState.value.selectedCompletedIds.toMutableSet()
        if (!selected.add(taskId)) {
            selected.remove(taskId)
        }
        _uiState.update { it.copy(selectedCompletedIds = selected) }
    }

    fun enterCompletedManageModeWithSelection(taskId: String) {
        _uiState.update { current ->
            current.copy(
                completedManageMode = true,
                selectedCompletedIds = current.selectedCompletedIds + taskId,
            )
        }
    }

    fun toggleSelectAllCompletedTasks() {
        val ids = completedTasks().map { it.id }.toSet()
        if (ids.isEmpty()) {
            _uiState.update { it.copy(actionMessage = appContext.getString(R.string.history_no_selectable_completed)) }
            return
        }
        val selected = uiState.value.selectedCompletedIds
        val allSelected = ids.all { selected.contains(it) }
        _uiState.update { it.copy(selectedCompletedIds = if (allSelected) emptySet() else ids) }
    }

    fun selectedCompletedTasks(): List<DownloadTask> {
        val selectedIds = uiState.value.selectedCompletedIds
        return completedTasks().filter { selectedIds.contains(it.id) }
    }

    fun requestShareCompletedTasks() {
        if (uiState.value.selectedCompletedIds.isEmpty()) {
            _uiState.update { it.copy(actionMessage = appContext.getString(R.string.history_select_completed_first)) }
            return
        }
        _uiState.update { it.copy(shareCompletedToken = System.currentTimeMillis()) }
    }

    fun deleteSelectedCompletedTasks() {
        val selected = selectedCompletedTasks()
        if (selected.isEmpty()) {
            _uiState.update { it.copy(actionMessage = appContext.getString(R.string.history_select_completed_first)) }
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
                        actionMessage = appContext.getString(R.string.history_deleted_completed_count, selected.size),
                        completedManageMode = false,
                        selectedCompletedIds = emptySet(),
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { it.copy(actionMessage = throwable.message ?: appContext.getString(R.string.history_delete_completed_failed)) }
            }
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

    fun toggleTaskSelection(taskId: String) {
        val selected = uiState.value.selectedTaskIds.toMutableSet()
        if (!selected.add(taskId)) {
            selected.remove(taskId)
        }
        _uiState.update { it.copy(selectedTaskIds = selected) }
    }

    fun toggleGroupSelection(groupKey: String) {
        val group = currentDownloadGroups().firstOrNull { it.groupKey == groupKey } ?: return
        val ids = group.displayItems.map { it.id }
        if (ids.isEmpty()) return

        val selected = uiState.value.selectedTaskIds.toMutableSet()
        val allSelected = ids.all { selected.contains(it) }
        if (allSelected) {
            selected.removeAll(ids.toSet())
        } else {
            selected.addAll(ids)
        }
        _uiState.update { it.copy(selectedTaskIds = selected) }
    }

    fun enterDownloadManageModeWithSelection(taskId: String) {
        _uiState.update { current ->
            val nextSelection = current.selectedTaskIds.toMutableSet()
            nextSelection.add(taskId)
            current.copy(
                downloadManageMode = true,
                selectedTaskIds = nextSelection,
            )
        }
    }

    fun enterDownloadManageModeWithGroup(groupKey: String) {
        val group = currentDownloadGroups().firstOrNull { it.groupKey == groupKey } ?: return
        val ids = group.displayItems.map { it.id }.toSet()
        _uiState.update { current ->
            current.copy(
                downloadManageMode = true,
                selectedTaskIds = current.selectedTaskIds + ids,
            )
        }
    }

    fun toggleSelectAllDownloadTasks() {
        val ids = currentDownloadGroups()
            .flatMap { it.displayItems }
            .map { it.id }
            .toSet()
        if (ids.isEmpty()) {
            _uiState.update { it.copy(actionMessage = appContext.getString(R.string.history_no_selectable_download_tasks)) }
            return
        }
        val selected = uiState.value.selectedTaskIds
        val allSelected = ids.all { selected.contains(it) }
        val next = if (allSelected) {
            selected - ids
        } else {
            selected + ids
        }
        _uiState.update { it.copy(selectedTaskIds = next) }
    }

    fun deleteSelectedDownloadTasks() {
        val selectedIds = uiState.value.selectedTaskIds.toList()
        if (selectedIds.isEmpty()) {
            _uiState.update { it.copy(actionMessage = appContext.getString(R.string.history_select_download_first)) }
            return
        }

        viewModelScope.launch {
            runCatching {
                val tasks = container.repository.getTasks(selectedIds)
                tasks.forEach { task ->
                    if (task.status == DownloadTaskStatus.QUEUED || task.status == DownloadTaskStatus.DOWNLOADING) {
                        task.externalDownloadId?.let { id -> container.downloadGateway.cancelDownload(id) }
                    }
                }
                container.repository.deleteTasks(selectedIds)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        actionMessage = appContext.getString(R.string.history_deleted_download_count, selectedIds.size),
                        selectedTaskIds = emptySet(),
                        downloadManageMode = false,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { it.copy(actionMessage = throwable.message ?: appContext.getString(R.string.history_delete_download_failed)) }
            }
        }
    }

    fun shareSelectedDownloadTasks() {
        val selectedIds = uiState.value.selectedTaskIds
        if (selectedIds.isEmpty()) {
            _uiState.update { it.copy(actionMessage = appContext.getString(R.string.history_select_download_first)) }
            return
        }
        val selected = uiState.value.downloadTasks.filter { selectedIds.contains(it.id) }
        val payload = selected.joinToString("\n") { task ->
            "${task.sourceUrl}\n${appContext.getString(R.string.history_share_status_line, appContext.downloadTaskStatusText(task.status))}"
        }
        _uiState.update { it.copy(shareTextPayload = payload) }
    }

    fun retryTask(taskId: String) {
        viewModelScope.launch {
            runCatching {
                container.retryDownloadTaskUseCase(taskId)
            }.onSuccess {
                _uiState.update { it.copy(actionMessage = appContext.getString(R.string.history_retry_enqueued)) }
            }.onFailure { throwable ->
                _uiState.update { it.copy(actionMessage = throwable.message ?: appContext.getString(R.string.detail_action_retry_failed)) }
            }
        }
    }

    fun retryFailedGroup(groupKey: String) {
        val group = currentDownloadGroups().firstOrNull { it.groupKey == groupKey } ?: return
        val failed = group.displayItems.filter { it.status == DownloadTaskStatus.FAILED }
        if (failed.isEmpty()) {
            _uiState.update { it.copy(actionMessage = appContext.getString(R.string.history_group_no_failed_items)) }
            return
        }
        viewModelScope.launch {
            runCatching {
                failed.forEach { task ->
                    container.retryDownloadTaskUseCase(task.id)
                }
            }.onSuccess {
                _uiState.update { it.copy(actionMessage = appContext.getString(R.string.history_retried_failed_count, failed.size)) }
            }.onFailure { throwable ->
                _uiState.update { it.copy(actionMessage = throwable.message ?: appContext.getString(R.string.history_retry_batch_failed)) }
            }
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

    fun toggleParseSelection(recordId: String) {
        val selected = uiState.value.selectedParseIds.toMutableSet()
        if (!selected.add(recordId)) {
            selected.remove(recordId)
        }
        _uiState.update { it.copy(selectedParseIds = selected) }
    }

    fun enterParseManageModeWithSelection(recordId: String) {
        _uiState.update { current ->
            val next = current.selectedParseIds.toMutableSet()
            next.add(recordId)
            current.copy(
                parseManageMode = true,
                selectedParseIds = next,
            )
        }
    }

    fun toggleSelectAllParseRecords() {
        val records = uiState.value.parseRecords
        if (records.isEmpty()) {
            _uiState.update { it.copy(actionMessage = appContext.getString(R.string.history_no_selectable_parse_records)) }
            return
        }
        val selected = uiState.value.selectedParseIds
        val allSelected = records.all { selected.contains(it.id) }
        val next = if (allSelected) emptySet() else records.map { it.id }.toSet()
        _uiState.update { it.copy(selectedParseIds = next) }
    }

    fun shareSelectedParseRecords() {
        val selectedIds = uiState.value.selectedParseIds
        if (selectedIds.isEmpty()) {
            _uiState.update { it.copy(actionMessage = appContext.getString(R.string.history_select_parse_first)) }
            return
        }
        val selected = uiState.value.parseRecords.filter { selectedIds.contains(it.id) }
        val payload = selected.joinToString("\n") { record ->
            val link = record.resolvedUrl ?: appContext.getString(R.string.history_no_link)
            "$link\n${appContext.getString(R.string.history_share_parse_result_line, appContext.parseRecordStatusText(record.status), record.message.orEmpty())}"
        }
        _uiState.update { it.copy(shareTextPayload = payload) }
    }

    fun deleteSelectedParseRecords() {
        val selectedIds = uiState.value.selectedParseIds.toList()
        if (selectedIds.isEmpty()) {
            _uiState.update { it.copy(actionMessage = appContext.getString(R.string.history_select_parse_first)) }
            return
        }
        viewModelScope.launch {
            runCatching {
                container.parseRecordRepository.deleteRecords(selectedIds)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        actionMessage = appContext.getString(R.string.history_deleted_parse_count, selectedIds.size),
                        selectedParseIds = emptySet(),
                        parseManageMode = false,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { it.copy(actionMessage = throwable.message ?: appContext.getString(R.string.history_delete_parse_failed)) }
            }
        }
    }

    private fun observeHistory() {
        viewModelScope.launch {
            combine(
                container.observeHistoryUseCase(),
                container.parseRecordRepository.observeRecords(),
            ) { tasks, records ->
                tasks to records
            }.collect { (tasks, records) ->
                _uiState.update {
                    it.copy(
                        downloadTasks = tasks,
                        parseRecords = records,
                        loading = false,
                    )
                }
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

    private fun deleteFileIfExists(saveUri: String?) {
        val path = saveUri
            ?.takeIf { it.startsWith("file://", ignoreCase = true) }
            ?.removePrefix("file://")
            ?.takeIf { it.isNotBlank() }
            ?: return
        val file = File(path)
        if (file.exists()) {
            file.delete()
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

internal fun buildDownloadGroupsForUi(
    tasks: List<DownloadTask>,
    subTab: DownloadSubTab,
): List<DownloadGroupUiModel> {
    val activeOrFailed = tasks.filter { it.status != DownloadTaskStatus.SUCCESS }
    val byGroup = activeOrFailed.groupBy(::resolveDownloadGroupKey)

    val groups = byGroup.mapNotNull { (groupKey, groupTasks) ->
        val filtered = when (subTab) {
            DownloadSubTab.DOWNLOADING -> groupTasks.filter {
                it.status == DownloadTaskStatus.DOWNLOADING || it.status == DownloadTaskStatus.QUEUED
            }

            DownloadSubTab.FAILED -> groupTasks.filter { it.status == DownloadTaskStatus.FAILED }
        }
        if (filtered.isEmpty()) return@mapNotNull null

        val sample = groupTasks.maxByOrNull { it.updatedAt } ?: return@mapNotNull null
        DownloadGroupUiModel(
            groupKey = groupKey,
            title = sample.title,
            sourceUrl = sample.sourceUrl,
            displayItems = filtered.sortedByDescending { it.updatedAt },
            totalItems = groupTasks.size,
            isSingle = groupTasks.size == 1,
        )
    }

    return when (subTab) {
        DownloadSubTab.DOWNLOADING -> {
            groups.sortedWith(
                compareBy<DownloadGroupUiModel> {
                    val containsRunning = it.displayItems.any { task -> task.status == DownloadTaskStatus.DOWNLOADING }
                    if (containsRunning) 0 else 1
                }.thenByDescending { group -> group.displayItems.maxOfOrNull { it.updatedAt } ?: 0L },
            )
        }

        DownloadSubTab.FAILED -> groups.sortedByDescending { it.displayItems.maxOfOrNull { task -> task.updatedAt } ?: 0L }
    }
}

private fun resolveDownloadGroupKey(task: DownloadTask): String {
    val parseId = task.parseRecordId?.trim().orEmpty()
    if (parseId.isNotBlank()) {
        return "parse:$parseId"
    }
    return "task:${task.id}"
}
