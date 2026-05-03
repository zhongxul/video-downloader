package com.example.videodownloader.ui.redesign.library

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.videodownloader.ui.redesign.component.AppBottomNav
import com.example.videodownloader.ui.redesign.component.MediaPreview
import com.example.videodownloader.ui.redesign.component.NavItem
import com.example.videodownloader.ui.redesign.theme.AppDesignTheme
import com.example.videodownloader.ui.redesign.theme.AppTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onNavigateToDownload: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LibraryUiEvent.NavigateToDetail -> onNavigateToDetail(event.taskId)
                is LibraryUiEvent.NavigateToParseResult -> Toast.makeText(context, "历史解析结果需要重新解析后确认", Toast.LENGTH_SHORT).show()
                is LibraryUiEvent.ShowToast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
        }
    }
    LibraryContent(
        state = state,
        onAction = viewModel::onAction,
        onNavSelect = { item ->
            when (item) {
                NavItem.DOWNLOAD -> onNavigateToDownload()
                NavItem.PROFILE -> onNavigateToProfile()
                else -> {}
            }
        },
        onOpenDetail = { viewModel.onAction(LibraryAction.OpenDetail(it)) },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryContent(
    state: LibraryUiState,
    onAction: (LibraryAction) -> Unit,
    onNavSelect: (NavItem) -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    val c = AppTheme.colors
    val t = AppTheme.typo
    val s = AppTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bgApp)
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(s.md),
        ) {
            // Header
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "资源库", style = t.captionSemiBold, color = c.accent)
                Text(
                    text = "下载进度、已完成内容与解析记录统一管理",
                    style = t.heroTitle,
                    color = c.textPrimary,
                )
            }

            // Segment row
            SegmentRow(
                currentTab = state.currentTab,
                inProgressCount = state.inProgressCount,
                completedCount = state.completedCount,
                parseRecordCount = state.parseRecordCount,
                onSwitch = { onAction(LibraryAction.SwitchTab(it)) },
            )

            // Tab content
            when (state.currentTab) {
                LibraryTab.IN_PROGRESS -> InProgressTab(state.inProgressItems, onOpenDetail)
                LibraryTab.COMPLETED -> CompletedTab(
                    state = state,
                    onAction = onAction,
                    onOpenDetail = onOpenDetail,
                )
                LibraryTab.PARSE_RECORDS -> ParseRecordsTab(
                    state = state,
                    onAction = onAction,
                )
            }
        }

        Spacer(modifier = Modifier.height(s.md))
        AppBottomNav(selected = NavItem.LIBRARY, onSelect = onNavSelect)
    }
}

@Composable
private fun SegmentRow(
    currentTab: LibraryTab,
    inProgressCount: Int,
    completedCount: Int,
    parseRecordCount: Int,
    onSwitch: (LibraryTab) -> Unit,
) {
    val c = AppTheme.colors
    val t = AppTheme.typo
    val r = AppTheme.radius

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        data class Seg(val tab: LibraryTab, val label: String)
        val segments = listOf(
            Seg(LibraryTab.IN_PROGRESS, "进行中 $inProgressCount"),
            Seg(LibraryTab.COMPLETED, "已完成 $completedCount"),
            Seg(LibraryTab.PARSE_RECORDS, "解析记录 $parseRecordCount"),
        )
        segments.forEach { seg ->
            val isActive = seg.tab == currentTab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(r.pill))
                    .then(
                        if (isActive) Modifier.background(c.primary)
                        else Modifier
                            .background(c.bgCard)
                            .border(1.dp, c.borderSoft, RoundedCornerShape(r.pill))
                    )
                    .clickable { onSwitch(seg.tab) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = seg.label,
                    style = t.captionSemiBold,
                    color = if (isActive) Color.White else c.textSecondary,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InProgressTab(items: List<InProgressGroup>, onOpenDetail: (String) -> Unit) {
    if (items.isEmpty()) {
        EmptyState("暂无下载任务")
    } else {
        items.forEach { group ->
            InProgressCard(group = group, onClick = { onOpenDetail(group.id) })
        }
    }
}

@Composable
private fun InProgressCard(group: InProgressGroup, onClick: () -> Unit) {
    val c = AppTheme.colors
    val t = AppTheme.typo
    val r = AppTheme.radius

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(r.xl))
            .background(c.bgCard)
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text = group.title, style = t.sectionTitle, color = c.textPrimary)
        Text(
            text = "${group.totalCount} 项中的 ${group.successCount} 项成功，${group.downloadingCount} 项下载中，${group.retryCount} 项等待重试",
            style = t.body,
            color = c.textSecondary,
        )
        LinearProgressIndicator(
            progress = { group.progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(r.pill)),
            color = c.accent,
            trackColor = Color(0xFFDDEAF4),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompletedTab(
    state: LibraryUiState,
    onAction: (LibraryAction) -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    val c = AppTheme.colors
    val t = AppTheme.typo

    Text(text = "已完成预览", style = t.captionSemiBold, color = c.textSecondary)

    if (state.completedItems.isEmpty()) {
        EmptyState("暂无已完成内容")
        return
    }

    if (state.completedManageMode) {
        ManageBar(
            selectedCount = state.selectedCompletedIds.size,
            allSelected = state.completedItems.isNotEmpty() && state.completedItems.all { it.id in state.selectedCompletedIds },
            onSelectAll = { onAction(LibraryAction.ToggleSelectAllCompleted) },
            onDelete = { onAction(LibraryAction.DeleteSelectedCompleted) },
            onCancel = { onAction(LibraryAction.ExitManageMode) },
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.completedItems.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowItems.forEach { item ->
                    CompletedCard(
                        item = item,
                        selected = item.id in state.selectedCompletedIds,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (state.completedManageMode) onAction(LibraryAction.ToggleCompletedSelection(item.id))
                            else onOpenDetail(item.id)
                        },
                        onLongClick = { onAction(LibraryAction.EnterCompletedManage(item.id)) },
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompletedCard(
    item: CompletedItem,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val c = AppTheme.colors
    val t = AppTheme.typo
    val r = AppTheme.radius

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(r.xl))
            .background(if (selected) c.surfaceTint else c.bgCard)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(156.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF9ED0E9)),
        ) {
            MediaPreview(
                source = item.thumbnailUrl,
                contentDescription = item.title,
                isVideo = item.isVideo,
                inlinePlaybackEnabled = false,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = if (item.itemCount > 1) "${item.title} (${item.itemCount})" else item.title,
            style = t.captionSemiBold,
            color = c.textPrimary,
            maxLines = 2,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ParseRecordsTab(
    state: LibraryUiState,
    onAction: (LibraryAction) -> Unit,
) {
    val c = AppTheme.colors
    val t = AppTheme.typo
    val r = AppTheme.radius

    if (state.parseRecords.isEmpty()) {
        EmptyState("暂无解析记录")
        return
    }

    if (state.parseManageMode) {
        ManageBar(
            selectedCount = state.selectedParseIds.size,
            allSelected = state.parseRecords.isNotEmpty() && state.parseRecords.all { it.id in state.selectedParseIds },
            onSelectAll = { onAction(LibraryAction.ToggleSelectAllParseRecords) },
            onDelete = { onAction(LibraryAction.DeleteSelectedParseRecords) },
            onCancel = { onAction(LibraryAction.ExitManageMode) },
        )
    }

    state.parseRecords.forEach { record ->
        val selected = record.id in state.selectedParseIds
        val expanded = record.id in state.expandedParseIds
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(r.xl))
                .background(if (selected) c.surfaceTint else Color(0xFFF9FBFD))
                .border(1.dp, c.borderSoft, RoundedCornerShape(r.xl))
                .combinedClickable(
                    onClick = {
                        if (state.parseManageMode) onAction(LibraryAction.ToggleParseSelection(record.id))
                        else onAction(LibraryAction.OpenParseResult(record.id))
                    },
                    onLongClick = { onAction(LibraryAction.EnterParseManage(record.id)) },
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = record.title, style = t.cardTitle, color = c.textPrimary)
            Text(text = "${record.timeAgo} · ${record.status}", style = t.body, color = c.textSecondary)
            if (record.sourceUrl.isNotBlank()) {
                Text(text = record.sourceUrl, style = t.caption, color = c.textSecondary)
            }
            if (record.message.isNotBlank()) {
                Text(text = record.message, style = t.caption, color = c.textSecondary)
            }
            if (expanded) {
                Text(text = "记录 ID  ${record.id}", style = t.caption, color = c.textSecondary)
                Text(text = "说明  解析结果详情尚未持久化，重新确认需要再次解析原链接。", style = t.caption, color = c.textSecondary)
            }
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    val c = AppTheme.colors
    val t = AppTheme.typo
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = t.bodySemiBold, color = c.textSecondary)
    }
}

@Composable
private fun ManageBar(
    selectedCount: Int,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    val c = AppTheme.colors
    val t = AppTheme.typo
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.bgCard)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "已选 $selectedCount 项", style = t.bodySemiBold, color = c.textPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = if (allSelected) "取消全选" else "全选", style = t.bodySemiBold, color = c.primary, modifier = Modifier.clickable(onClick = onSelectAll))
            Text(text = "删除", style = t.bodySemiBold, color = c.error, modifier = Modifier.clickable(onClick = onDelete))
            Text(text = "完成", style = t.bodySemiBold, color = c.textSecondary, modifier = Modifier.clickable(onClick = onCancel))
        }
    }
}

@Preview(showBackground = true, widthDp = 430, heightDp = 932)
@Composable
private fun LibraryScreenPreview() {
    AppDesignTheme {
        LibraryContent(
            state = LibraryUiState(
                inProgressCount = 3,
                completedCount = 28,
                parseRecordCount = 14,
            ),
            onAction = {},
            onNavSelect = {},
            onOpenDetail = {},
        )
    }
}
