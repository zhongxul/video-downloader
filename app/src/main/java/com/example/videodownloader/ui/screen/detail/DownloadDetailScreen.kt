package com.example.videodownloader.ui.screen.detail

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.videodownloader.domain.model.DownloadTaskStatus
import com.example.videodownloader.ui.component.AppGradientBackdrop
import com.example.videodownloader.ui.component.AppSectionCard

@Composable
fun DownloadDetailScreen(
    viewModel: DownloadDetailViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.actionMessage) {
        val msg = state.actionMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearMessage()
    }

    AppGradientBackdrop {
        Box(modifier = Modifier.fillMaxSize()) {
            SnackbarHost(hostState = snackbarHostState)

            if (state.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
                return@Box
            }

            val task = state.task
            if (task == null) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    AppSectionCard {
                        Text("任务不存在", color = MaterialTheme.colorScheme.error)
                    }
                }
                return@Box
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    AppSectionCard {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = onBack) { Text("返回") }
                            Text("下载详情", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("标题：${task.title}")
                            Text("状态：${task.status.toDisplayName()}")
                            Text("进度：${task.progress}%")
                            Text("格式：${task.selectedResolution} · ${task.selectedExt}")
                            Text("来源：${task.sourceUrl}")
                            Text("下载链接：${task.downloadUrl}")
                            task.saveUri?.let { Text("本地路径：$it") }
                            task.errorMessage?.let { Text("错误：$it", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }

                item {
                    AppSectionCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (task.status == DownloadTaskStatus.DOWNLOADING || task.status == DownloadTaskStatus.QUEUED) {
                                Button(onClick = viewModel::pauseTask) { Text("暂停") }
                            }
                            if (task.status == DownloadTaskStatus.CANCELED) {
                                FilledTonalButton(onClick = viewModel::resumeTask) { Text("继续") }
                            }
                            if (task.status == DownloadTaskStatus.FAILED) {
                                Button(onClick = viewModel::retryTask) { Text("重试") }
                            }
                        }
                    }
                }
            }
        }
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
