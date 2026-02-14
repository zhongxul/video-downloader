package com.example.videodownloader.ui.screen.history

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.videodownloader.domain.model.DownloadTask
import com.example.videodownloader.domain.model.DownloadTaskStatus
import com.example.videodownloader.domain.model.ParseRecord
import com.example.videodownloader.domain.model.ParseRecordStatus
import com.example.videodownloader.ui.component.AppGradientBackdrop
import com.example.videodownloader.ui.component.AppSectionCard

private enum class HistoryTab {
    PARSE_RECORDS,
    DOWNLOAD_QUEUE,
}

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onOpenDetail: (String) -> Unit,
    onOpenCompleted: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val filteredTasks = viewModel.filteredDownloadTasks()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val selectedTab = rememberSaveable { mutableStateOf(HistoryTab.PARSE_RECORDS) }

    LaunchedEffect(state.actionMessage) {
        val msg = state.actionMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearMessage()
    }

    LaunchedEffect(state.shareTextPayload) {
        val payload = state.shareTextPayload ?: return@LaunchedEffect
        shareText(context, payload)
        viewModel.clearSharePayload()
    }

    AppGradientBackdrop {
        Column(modifier = Modifier.fillMaxSize()) {
            SnackbarHost(hostState = snackbarHostState)
            if (state.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        AppSectionCard {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("历史中心", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    "查看解析记录、下载队列和失败任务。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    HistoryTabButton(
                                        text = "解析记录",
                                        selected = selectedTab.value == HistoryTab.PARSE_RECORDS,
                                        onClick = { selectedTab.value = HistoryTab.PARSE_RECORDS },
                                    )
                                    HistoryTabButton(
                                        text = "下载队列",
                                        selected = selectedTab.value == HistoryTab.DOWNLOAD_QUEUE,
                                        onClick = { selectedTab.value = HistoryTab.DOWNLOAD_QUEUE },
                                    )
                                }

                                HorizontalDivider(modifier = Modifier.padding(top = 2.dp, bottom = 2.dp))

                                when (selectedTab.value) {
                                    HistoryTab.PARSE_RECORDS -> {
                                        ParseHistorySection(
                                            records = state.parseRecords,
                                            manageMode = state.parseManageMode,
                                            selectedIds = state.selectedParseIds,
                                            onToggleManage = viewModel::toggleParseManageMode,
                                            onToggleSelection = viewModel::toggleParseSelection,
                                            onLongPressSelection = viewModel::enterParseManageModeWithSelection,
                                            onToggleSelectAll = viewModel::toggleSelectAllParseRecords,
                                            onDeleteSelected = viewModel::deleteSelectedParseRecords,
                                            onShareSelected = viewModel::shareSelectedParseRecords,
                                        )
                                    }

                                    HistoryTab.DOWNLOAD_QUEUE -> {
                                        DownloadQueueSection(
                                            tasks = filteredTasks,
                                            filter = state.downloadFilter,
                                            manageMode = state.downloadManageMode,
                                            selectedTaskIds = state.selectedTaskIds,
                                            onFilterChanged = viewModel::setDownloadFilter,
                                            onToggleManage = viewModel::toggleDownloadManageMode,
                                            onToggleSelection = viewModel::toggleTaskSelection,
                                            onLongPressSelection = viewModel::enterDownloadManageModeWithSelection,
                                            onToggleSelectAll = viewModel::toggleSelectAllDownloadTasks,
                                            onDeleteSelected = viewModel::deleteSelectedDownloadTasks,
                                            onShareSelected = viewModel::shareSelectedDownloadTasks,
                                            onRetry = viewModel::retryTask,
                                            onOpenDetail = onOpenDetail,
                                            onOpenCompleted = onOpenCompleted,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryTabButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        FilledTonalButton(onClick = onClick) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick) { Text(text) }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ParseHistorySection(
    records: List<ParseRecord>,
    manageMode: Boolean,
    selectedIds: Set<String>,
    onToggleManage: () -> Unit,
    onToggleSelection: (String) -> Unit,
    onLongPressSelection: (String) -> Unit,
    onToggleSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onShareSelected: () -> Unit,
) {
    val allSelected = records.isNotEmpty() && records.all { selectedIds.contains(it.id) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("解析记录", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = onToggleManage) {
                Text(if (manageMode) "完成" else "管理")
            }
        }

        if (manageMode) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onDeleteSelected) { Text("删除") }
                OutlinedButton(onClick = onShareSelected) { Text("分享") }
                OutlinedButton(onClick = onToggleSelectAll) { Text(if (allSelected) "取消全选" else "全选") }
                Text("已选 ${selectedIds.size} 项", style = MaterialTheme.typography.bodySmall)
            }
        }

        if (records.isEmpty()) {
            Text("暂无解析记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            records.forEach { record ->
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (manageMode) {
                                Modifier.clickable { onToggleSelection(record.id) }
                            } else {
                                Modifier.combinedClickable(
                                    onClick = {},
                                    onLongClick = { onLongPressSelection(record.id) },
                                )
                            },
                        )
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (manageMode) {
                        Checkbox(
                            checked = selectedIds.contains(record.id),
                            onCheckedChange = { onToggleSelection(record.id) },
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(record.title ?: record.resolvedUrl ?: "未命名记录", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "状态：${record.status.toDisplayName()}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        record.selectedFormatLabel?.let {
                            Text(
                                "选项：$it · ${record.selectedExt.orEmpty()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        record.message?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (record.status == ParseRecordStatus.FAILED || record.status == ParseRecordStatus.PARSE_FAILED) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DownloadQueueSection(
    tasks: List<DownloadTask>,
    filter: DownloadFilter,
    manageMode: Boolean,
    selectedTaskIds: Set<String>,
    onFilterChanged: (DownloadFilter) -> Unit,
    onToggleManage: () -> Unit,
    onToggleSelection: (String) -> Unit,
    onLongPressSelection: (String) -> Unit,
    onToggleSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onShareSelected: () -> Unit,
    onRetry: (String) -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenCompleted: () -> Unit,
) {
    val visibleIds = tasks.map { it.id }.toSet()
    val selectedCount = selectedTaskIds.count { visibleIds.contains(it) }
    val allSelected = tasks.isNotEmpty() && visibleIds.all { selectedTaskIds.contains(it) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("下载队列", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenCompleted) { Text("已完成") }
                OutlinedButton(onClick = onToggleManage) { Text(if (manageMode) "完成" else "管理") }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterButton("全部", filter == DownloadFilter.ALL) { onFilterChanged(DownloadFilter.ALL) }
            FilterButton("下载中", filter == DownloadFilter.DOWNLOADING) { onFilterChanged(DownloadFilter.DOWNLOADING) }
            FilterButton("失败", filter == DownloadFilter.FAILED) { onFilterChanged(DownloadFilter.FAILED) }
        }

        if (manageMode) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onDeleteSelected) { Text("删除") }
                OutlinedButton(onClick = onShareSelected) { Text("分享") }
                OutlinedButton(onClick = onToggleSelectAll) { Text(if (allSelected) "取消全选" else "全选") }
                Text("已选 $selectedCount 项", style = MaterialTheme.typography.bodySmall)
            }
        }

        if (tasks.isEmpty()) {
            Text("当前筛选下暂无任务", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            tasks.forEach { task ->
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (manageMode) {
                                Modifier.clickable { onToggleSelection(task.id) }
                            } else {
                                Modifier.combinedClickable(
                                    onClick = {},
                                    onLongClick = { onLongPressSelection(task.id) },
                                )
                            },
                        )
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (manageMode) {
                        Checkbox(
                            checked = selectedTaskIds.contains(task.id),
                            onCheckedChange = { onToggleSelection(task.id) },
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        val statusLine = buildString {
                            append("状态：")
                            append(task.status.toDisplayName())
                            compactErrorMessage(task.errorMessage)?.let {
                                append(" · ")
                                append(it)
                            }
                        }
                        Text(
                            text = statusLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (task.status == DownloadTaskStatus.FAILED) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        LinearProgressIndicator(
                            progress = { task.progress.coerceIn(0, 100) / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "下载进度 ${task.progress.coerceIn(0, 100)}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        if (!manageMode) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onOpenDetail(task.id) }) { Text("详情") }
                                Button(
                                    enabled = task.status == DownloadTaskStatus.FAILED,
                                    onClick = { onRetry(task.id) },
                                ) {
                                    Text("重试")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        FilledTonalButton(onClick = onClick) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick) { Text(text) }
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

private fun DownloadTaskStatus.toDisplayName(): String {
    return when (this) {
        DownloadTaskStatus.QUEUED -> "排队中"
        DownloadTaskStatus.DOWNLOADING -> "下载中"
        DownloadTaskStatus.SUCCESS -> "成功"
        DownloadTaskStatus.FAILED -> "失败"
        DownloadTaskStatus.CANCELED -> "已取消"
    }
}

private fun compactErrorMessage(errorMessage: String?): String? {
    if (errorMessage.isNullOrBlank()) return null
    return errorMessage.replace("\n", " ").trim()
}

private fun shareText(context: Context, content: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, content)
    }
    context.startActivity(Intent.createChooser(intent, "分享"))
}
