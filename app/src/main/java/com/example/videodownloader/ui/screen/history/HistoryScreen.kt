package com.example.videodownloader.ui.screen.history

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.videodownloader.R
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.videodownloader.core.net.buildMediaPreviewHeaderMap
import com.example.videodownloader.domain.model.DownloadTask
import com.example.videodownloader.domain.model.DownloadTaskStatus
import com.example.videodownloader.domain.model.ParseRecord
import com.example.videodownloader.domain.model.ParseRecordStatus
import com.example.videodownloader.ui.downloadTaskStatusText
import com.example.videodownloader.ui.parseRecordStatusText
import com.example.videodownloader.ui.component.AppGradientBackdrop
import com.example.videodownloader.ui.component.AppSectionCard

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onOpenDetail: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

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
    LaunchedEffect(state.shareCompletedToken) {
        val token = state.shareCompletedToken ?: return@LaunchedEffect
        val message = MediaActionHelper.shareVideos(context, viewModel.selectedCompletedTasks())
        if (!message.isNullOrBlank()) {
            viewModel.setMessage(message)
        }
        if (token > 0L) {
            viewModel.consumeCompletedShareToken()
        }
    }

    AppGradientBackdrop {
        Column(modifier = Modifier.fillMaxSize()) {
            SnackbarHost(hostState = snackbarHostState)
            if (state.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    HeaderTabs(
                        selected = state.mainTab,
                        onSelect = viewModel::setMainTab,
                    )
                }

                when (state.mainTab) {
                    HistoryMainTab.DOWNLOAD -> {
                        item {
                            DownloadSubTabs(
                                selected = state.downloadSubTab,
                                onSelect = viewModel::setDownloadSubTab,
                            )
                        }

                        item {
                            DownloadManageBar(
                                manageMode = state.downloadManageMode,
                                selectedCount = state.selectedTaskIds.size,
                                allSelected = currentDownloadTaskIds(viewModel).all { state.selectedTaskIds.contains(it) } &&
                                    currentDownloadTaskIds(viewModel).isNotEmpty(),
                                onToggleManage = viewModel::toggleDownloadManageMode,
                                onToggleSelectAll = viewModel::toggleSelectAllDownloadTasks,
                                onDelete = viewModel::deleteSelectedDownloadTasks,
                                onShare = viewModel::shareSelectedDownloadTasks,
                            )
                        }

                        val groups = viewModel.currentDownloadGroups()
                        if (groups.isEmpty()) {
                            item {
                                AppSectionCard {
                                    Text(
                                        text = if (state.downloadSubTab == DownloadSubTab.DOWNLOADING) {
                                            stringResource(R.string.history_empty_downloading)
                                        } else {
                                            stringResource(R.string.history_empty_failed)
                                        },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        } else {
                            items(groups, key = { it.groupKey + "_" + state.downloadSubTab.name }) { group ->
                                DownloadGroupCard(
                                    group = group,
                                    subTab = state.downloadSubTab,
                                    expanded = viewModel.isGroupExpanded(group.groupKey),
                                    manageMode = state.downloadManageMode,
                                    selectedTaskIds = state.selectedTaskIds,
                                    onToggleExpand = { viewModel.toggleGroupExpanded(group.groupKey) },
                                    onSelectGroup = { viewModel.toggleGroupSelection(group.groupKey) },
                                    onLongPressGroup = { viewModel.enterDownloadManageModeWithGroup(group.groupKey) },
                                    onSelectItem = { viewModel.toggleTaskSelection(it.id) },
                                    onLongPressItem = { viewModel.enterDownloadManageModeWithSelection(it.id) },
                                    onOpenDetail = onOpenDetail,
                                    onRetryItem = { viewModel.retryTask(it.id) },
                                    onRetryFailedGroup = { viewModel.retryFailedGroup(group.groupKey) },
                                )
                            }
                        }
                    }

                    HistoryMainTab.COMPLETED -> {
                        val completed = viewModel.completedTasks()
                        item {
                            CompletedManageBar(
                                manageMode = state.completedManageMode,
                                selectedCount = state.selectedCompletedIds.size,
                                allSelected = completed.isNotEmpty() && completed.all { state.selectedCompletedIds.contains(it.id) },
                                onToggleManage = viewModel::toggleCompletedManageMode,
                                onToggleSelectAll = viewModel::toggleSelectAllCompletedTasks,
                                onDelete = viewModel::deleteSelectedCompletedTasks,
                                onShare = viewModel::requestShareCompletedTasks,
                            )
                        }
                        if (completed.isEmpty()) {
                            item {
                                AppSectionCard {
                                    Text(stringResource(R.string.history_empty_completed), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            items(completed, key = { it.id }) { task ->
                                CompletedTaskCard(
                                    task = task,
                                    manageMode = state.completedManageMode,
                                    selected = state.selectedCompletedIds.contains(task.id),
                                    onToggleSelection = { viewModel.toggleCompletedSelection(task.id) },
                                    onLongPressSelection = { viewModel.enterCompletedManageModeWithSelection(task.id) },
                                    onOpenDetail = { onOpenDetail(task.id) },
                                    onPlay = {
                                        val error = MediaActionHelper.openVideo(context, task)
                                        if (!error.isNullOrBlank()) {
                                            viewModel.setMessage(error)
                                        }
                                    },
                                )
                            }
                        }
                    }

                    HistoryMainTab.PARSE_RECORDS -> {
                        item {
                            ParseManageBar(
                                manageMode = state.parseManageMode,
                                selectedCount = state.selectedParseIds.size,
                                allSelected = state.parseRecords.isNotEmpty() && state.parseRecords.all { state.selectedParseIds.contains(it.id) },
                                onToggleManage = viewModel::toggleParseManageMode,
                                onToggleSelectAll = viewModel::toggleSelectAllParseRecords,
                                onDelete = viewModel::deleteSelectedParseRecords,
                                onShare = viewModel::shareSelectedParseRecords,
                            )
                        }
                        if (state.parseRecords.isEmpty()) {
                            item {
                                AppSectionCard { Text(stringResource(R.string.history_empty_parse_records), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        } else {
                            items(state.parseRecords, key = { it.id }) { record ->
                                ParseRecordCard(
                                    record = record,
                                    manageMode = state.parseManageMode,
                                    selected = state.selectedParseIds.contains(record.id),
                                    onToggleSelection = { viewModel.toggleParseSelection(record.id) },
                                    onLongPressSelection = { viewModel.enterParseManageModeWithSelection(record.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun currentDownloadTaskIds(viewModel: HistoryViewModel): Set<String> {
    return viewModel.currentDownloadGroups().flatMap { it.displayItems }.map { it.id }.toSet()
}

@Composable
private fun HeaderTabs(
    selected: HistoryMainTab,
    onSelect: (HistoryMainTab) -> Unit,
) {
    AppSectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TabButton(stringResource(R.string.history_tab_download), selected == HistoryMainTab.DOWNLOAD) { onSelect(HistoryMainTab.DOWNLOAD) }
                TabButton(stringResource(R.string.history_tab_completed), selected == HistoryMainTab.COMPLETED) { onSelect(HistoryMainTab.COMPLETED) }
                TabButton(stringResource(R.string.history_tab_parse_records), selected == HistoryMainTab.PARSE_RECORDS) { onSelect(HistoryMainTab.PARSE_RECORDS) }
            }
        }
    }
}

@Composable
private fun DownloadSubTabs(
    selected: DownloadSubTab,
    onSelect: (DownloadSubTab) -> Unit,
) {
    AppSectionCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabButton(stringResource(R.string.history_subtab_downloading), selected == DownloadSubTab.DOWNLOADING) { onSelect(DownloadSubTab.DOWNLOADING) }
            TabButton(stringResource(R.string.history_subtab_failed), selected == DownloadSubTab.FAILED) { onSelect(DownloadSubTab.FAILED) }
        }
    }
}

@Composable
private fun DownloadManageBar(
    manageMode: Boolean,
    selectedCount: Int,
    allSelected: Boolean,
    onToggleManage: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    AppSectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onToggleManage) { Text(stringResource(if (manageMode) R.string.common_done else R.string.common_manage)) }
                if (manageMode) {
                    Button(onClick = onDelete) { Text(stringResource(R.string.common_delete)) }
                    OutlinedButton(onClick = onShare) { Text(stringResource(R.string.common_share)) }
                    OutlinedButton(onClick = onToggleSelectAll) { Text(stringResource(if (allSelected) R.string.common_deselect_all else R.string.history_select_current_segment)) }
                }
            }
            if (manageMode) {
                Text(stringResource(R.string.history_selected_count, selectedCount), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CompletedManageBar(
    manageMode: Boolean,
    selectedCount: Int,
    allSelected: Boolean,
    onToggleManage: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    AppSectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onToggleManage) { Text(stringResource(if (manageMode) R.string.common_done else R.string.common_manage)) }
                if (manageMode) {
                    Button(onClick = onDelete) { Text(stringResource(R.string.common_delete)) }
                    OutlinedButton(onClick = onShare) { Text(stringResource(R.string.history_share_media)) }
                    OutlinedButton(onClick = onToggleSelectAll) { Text(stringResource(if (allSelected) R.string.common_deselect_all else R.string.common_select_all)) }
                }
            }
            if (manageMode) {
                Text(stringResource(R.string.history_selected_count, selectedCount), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ParseManageBar(
    manageMode: Boolean,
    selectedCount: Int,
    allSelected: Boolean,
    onToggleManage: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    AppSectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onToggleManage) { Text(stringResource(if (manageMode) R.string.common_done else R.string.common_manage)) }
                if (manageMode) {
                    Button(onClick = onDelete) { Text(stringResource(R.string.common_delete)) }
                    OutlinedButton(onClick = onShare) { Text(stringResource(R.string.common_share)) }
                    OutlinedButton(onClick = onToggleSelectAll) { Text(stringResource(if (allSelected) R.string.common_deselect_all else R.string.common_select_all)) }
                }
            }
            if (manageMode) {
                Text(stringResource(R.string.history_selected_count, selectedCount), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TabButton(
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadGroupCard(
    group: DownloadGroupUiModel,
    subTab: DownloadSubTab,
    expanded: Boolean,
    manageMode: Boolean,
    selectedTaskIds: Set<String>,
    onToggleExpand: () -> Unit,
    onSelectGroup: () -> Unit,
    onLongPressGroup: () -> Unit,
    onSelectItem: (DownloadTask) -> Unit,
    onLongPressItem: (DownloadTask) -> Unit,
    onOpenDetail: (String) -> Unit,
    onRetryItem: (DownloadTask) -> Unit,
    onRetryFailedGroup: () -> Unit,
) {
    val context = LocalContext.current
    AppSectionCard(
        modifier = Modifier.then(
            if (group.isSingle) {
                Modifier.combinedClickable(
                    onClick = {
                        val item = group.displayItems.firstOrNull() ?: return@combinedClickable
                        if (manageMode) {
                            onSelectItem(item)
                        } else {
                            onOpenDetail(item.id)
                        }
                    },
                    onLongClick = {
                        val item = group.displayItems.firstOrNull() ?: return@combinedClickable
                        if (!manageMode) onLongPressItem(item)
                    },
                )
            } else {
                Modifier.combinedClickable(
                    onClick = {
                        if (manageMode) {
                            onSelectGroup()
                        } else {
                            onToggleExpand()
                        }
                    },
                    onLongClick = {
                        if (!manageMode) onLongPressGroup()
                    },
                )
            }
        ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val selectedCount = group.displayItems.count { selectedTaskIds.contains(it.id) }
            val allSelected = group.displayItems.isNotEmpty() && selectedCount == group.displayItems.size

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (group.isSingle) {
                            group.displayItems.firstOrNull()?.status?.let(context::downloadTaskStatusText).orEmpty()
                        } else {
                            stringResource(
                                R.string.history_group_child_summary,
                                group.displayItems.size,
                                group.totalItems,
                                stringResource(if (expanded) R.string.history_group_expanded else R.string.history_group_collapsed),
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (group.sourceUrl.isNotBlank()) {
                        Text(
                            text = group.sourceUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (manageMode) {
                    Checkbox(
                        checked = allSelected,
                        onCheckedChange = { onSelectGroup() },
                    )
                } else if (!group.isSingle) {
                    OutlinedButton(onClick = onToggleExpand) {
                        Text(stringResource(if (expanded) R.string.history_collapse else R.string.history_expand))
                    }
                }
            }

            if (group.isSingle) {
                val item = group.displayItems.first()
                if (item.status == DownloadTaskStatus.DOWNLOADING || item.status == DownloadTaskStatus.QUEUED) {
                    LinearProgressIndicator(
                        progress = { item.progress.coerceIn(0, 100) / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (!manageMode && item.status == DownloadTaskStatus.FAILED) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onRetryItem(item) }) { Text(stringResource(R.string.common_retry)) }
                    }
                }
            } else {
                if (!manageMode && subTab == DownloadSubTab.FAILED) {
                    OutlinedButton(onClick = onRetryFailedGroup) {
                        Text(stringResource(R.string.history_retry_failed_children))
                    }
                }
                if (expanded) {
                    group.displayItems.forEach { item ->
                        HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (manageMode) onSelectItem(item) else onOpenDetail(item.id)
                                    },
                                    onLongClick = { if (!manageMode) onLongPressItem(item) },
                                )
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = context.downloadTaskStatusText(item.status),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (item.status == DownloadTaskStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (item.status == DownloadTaskStatus.DOWNLOADING || item.status == DownloadTaskStatus.QUEUED) {
                                    LinearProgressIndicator(
                                        progress = { item.progress.coerceIn(0, 100) / 100f },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                            if (manageMode) {
                                Checkbox(
                                    checked = selectedTaskIds.contains(item.id),
                                    onCheckedChange = { onSelectItem(item) },
                                )
                            } else if (item.status == DownloadTaskStatus.FAILED) {
                                OutlinedButton(onClick = { onRetryItem(item) }) { Text(stringResource(R.string.common_retry)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompletedTaskCard(
    task: DownloadTask,
    manageMode: Boolean,
    selected: Boolean,
    onToggleSelection: () -> Unit,
    onLongPressSelection: () -> Unit,
    onOpenDetail: () -> Unit,
    onPlay: () -> Unit,
) {
    val context = LocalContext.current
    val coverRequest = remember(task.coverUrl) {
        task.coverUrl?.takeIf { it.isNotBlank() }?.let { url ->
            ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .apply {
                    buildMediaPreviewHeaderMap(url).forEach { (key, value) ->
                        addHeader(key, value)
                    }
                }
                .build()
        }
    }
    AppSectionCard(
        modifier = Modifier.then(
            if (manageMode) {
                Modifier.clickable(onClick = onToggleSelection)
            } else {
                Modifier.combinedClickable(
                    onClick = onOpenDetail,
                    onLongClick = onLongPressSelection,
                )
            }
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = coverRequest,
                contentDescription = stringResource(R.string.history_cover),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 128.dp, height = 90.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${task.selectedResolution} · ${task.selectedExt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.history_completed_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (manageMode) {
                Checkbox(checked = selected, onCheckedChange = { onToggleSelection() })
            } else {
                OutlinedButton(onClick = onPlay) { Text(stringResource(R.string.history_play)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ParseRecordCard(
    record: ParseRecord,
    manageMode: Boolean,
    selected: Boolean,
    onToggleSelection: () -> Unit,
    onLongPressSelection: () -> Unit,
) {
    val context = LocalContext.current
    AppSectionCard(
        modifier = Modifier.combinedClickable(
            onClick = { if (manageMode) onToggleSelection() },
            onLongClick = { if (!manageMode) onLongPressSelection() },
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = record.title ?: record.resolvedUrl ?: stringResource(R.string.history_unnamed_record),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = context.parseRecordStatusText(record.status),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (record.status == ParseRecordStatus.PARSE_FAILED || record.status == ParseRecordStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                record.message?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (manageMode) {
                Checkbox(checked = selected, onCheckedChange = { onToggleSelection() })
            }
        }
    }
}

private fun shareText(context: Context, content: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, content)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.history_share_chooser)))
}
