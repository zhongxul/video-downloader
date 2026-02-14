package com.example.videodownloader.ui.screen.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.videodownloader.domain.model.DownloadTask
import com.example.videodownloader.ui.component.AppGradientBackdrop
import com.example.videodownloader.ui.component.AppSectionCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CompletedScreen(
    viewModel: CompletedViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var detailTask by remember { mutableStateOf<DownloadTask?>(null) }

    LaunchedEffect(state.actionMessage) {
        val msg = state.actionMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearMessage()
    }

    AppGradientBackdrop {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SnackbarHost(hostState = snackbarHostState)
            Header(
                manageMode = state.manageMode,
                selectedCount = state.selectedIds.size,
                onBack = onBack,
                onToggleManage = viewModel::toggleManageMode,
                onDelete = viewModel::deleteSelected,
                onShare = {
                    val msg = MediaActionHelper.shareVideos(context, viewModel.selectedTasks())
                    if (!msg.isNullOrBlank()) {
                        viewModel.setMessage(msg)
                    }
                },
            )

            if (state.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            } else if (state.tasks.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.tasks, key = { it.id }) { task ->
                        CompletedVideoItem(
                            task = task,
                            manageMode = state.manageMode,
                            selected = state.selectedIds.contains(task.id),
                            onToggleSelection = { viewModel.toggleSelection(task.id) },
                            onOpenDetail = { detailTask = task },
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
        }
    }

    detailTask?.let { task ->
        val info = MediaActionHelper.readLocalVideoInfo(task.saveUri)
        AlertDialog(
            onDismissRequest = { detailTask = null },
            confirmButton = {
                Button(onClick = { detailTask = null }) { Text("关闭") }
            },
            title = { Text("文件信息") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("标题：${task.title}")
                    Text("格式：${task.selectedResolution} · ${task.selectedExt}")
                    Text("完成时间：${formatTime(task.updatedAt)}")
                    Text("路径：${info?.path ?: "无"}")
                    Text("大小：${formatSize(info?.sizeBytes ?: 0L)}")
                    Text("存在：${if (info?.exists == true) "是" else "否"}")
                }
            },
        )
    }
}

@Composable
private fun Header(
    manageMode: Boolean,
    selectedCount: Int,
    onBack: () -> Unit,
    onToggleManage: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    AppSectionCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack) { Text("返回") }
                FilledTonalButton(onClick = onToggleManage) { Text(if (manageMode) "完成" else "管理") }
            }
            Text(
                text = "已完成视频",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            if (manageMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onDelete) { Text("删除") }
                    OutlinedButton(onClick = onShare) { Text("分享") }
                    Text("已选 $selectedCount 项", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun CompletedVideoItem(
    task: DownloadTask,
    manageMode: Boolean,
    selected: Boolean,
    onToggleSelection: () -> Unit,
    onOpenDetail: () -> Unit,
    onPlay: () -> Unit,
) {
    AppSectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AsyncImage(
                model = task.coverUrl,
                contentDescription = "封面",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 96.dp, height = 64.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = task.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "${task.selectedResolution} · ${task.selectedExt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatTime(task.updatedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (manageMode) {
                Checkbox(checked = selected, onCheckedChange = { onToggleSelection() })
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = onOpenDetail) { Text("详情") }
                    Button(onClick = onPlay) { Text("播放") }
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    AppSectionCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)),
            )
            Text("还没有完成的视频", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "下载成功后会在这里集中展示。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(modifier = Modifier.height(4.dp))
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatSize(sizeBytes: Long): String {
    if (sizeBytes <= 0L) return "0 B"
    val kb = sizeBytes / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.getDefault(), "%.2f GB", gb)
}
