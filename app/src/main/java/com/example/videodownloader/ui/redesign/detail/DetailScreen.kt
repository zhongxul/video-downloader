package com.example.videodownloader.ui.redesign.detail

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.videodownloader.ui.screen.history.MediaActionHelper
import com.example.videodownloader.ui.redesign.component.AppChip
import com.example.videodownloader.ui.redesign.component.MediaPreview
import com.example.videodownloader.ui.redesign.component.AppPrimaryButton
import com.example.videodownloader.ui.redesign.component.AppSecondaryButton
import com.example.videodownloader.ui.redesign.theme.AppDesignTheme
import com.example.videodownloader.ui.redesign.theme.AppTheme

@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                DetailUiEvent.NavigateBack -> onBack()
                is DetailUiEvent.ShowToast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is DetailUiEvent.OpenFile -> {
                    MediaActionHelper.openMedia(context, event.path, event.mimeType)
                        ?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }
    DetailContent(
        state = state,
        onAction = { action ->
            viewModel.onAction(action)
        },
    )
}

@Composable
private fun DetailContent(
    state: DetailUiState,
    onAction: (DetailAction) -> Unit,
) {
    val c = AppTheme.colors
    val t = AppTheme.typo
    val s = AppTheme.spacing
    val r = AppTheme.radius

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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AppChip(text = "返回", onClick = { onAction(DetailAction.GoBack) })
                AppChip(text = "更多", onClick = { onAction(DetailAction.ShowMore) })
            }

            // Media card with status
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(r.xl))
                    .background(c.bgCard)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Status pill
                val statusColor = when (state.status) {
                    TaskStatus.COMPLETED -> c.success
                    TaskStatus.FAILED -> c.error
                    TaskStatus.DOWNLOADING -> c.primary
                    TaskStatus.PAUSED -> c.warning
                    else -> c.textSecondary
                }
                val statusBg = when (state.status) {
                    TaskStatus.COMPLETED -> Color(0xFFEAF7F5)
                    TaskStatus.FAILED -> Color(0xFFFFE6E2)
                    TaskStatus.DOWNLOADING -> c.surfaceTint
                    TaskStatus.PAUSED -> Color(0xFFFFF3E0)
                    else -> c.surfaceTint
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(r.pill))
                        .background(statusBg)
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = state.statusLabel.ifEmpty { "已完成 · 封面与媒体信息完整" },
                        style = t.label,
                        color = statusColor,
                    )
                }

                // Preview frame
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(176.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFC9E0F1)),
                ) {
                    val preview = state.mediaItems.getOrNull(state.currentIndex)?.previewUrl ?: state.previewUrl
                    val currentItem = state.mediaItems.getOrNull(state.currentIndex)
                    MediaPreview(
                        source = preview,
                        mediaSource = currentItem?.mediaUrl,
                        contentDescription = state.title,
                        isVideo = currentItem?.isVideo == true,
                        inlinePlaybackEnabled = false,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (state.mediaItems.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        state.mediaItems.forEachIndexed { index, item ->
                            Box(
                                modifier = Modifier
                                    .height(76.dp)
                                    .width(74.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (index == state.currentIndex) c.primary else c.surfaceTint)
                                    .clickable { onAction(DetailAction.SelectMedia(index)) },
                            ) {
                                MediaPreview(
                                    source = item.previewUrl,
                                    mediaSource = item.mediaUrl,
                                    contentDescription = item.title,
                                    isVideo = item.isVideo,
                                    inlinePlaybackEnabled = false,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }

            // Title block
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = state.title.ifEmpty { "校园跑偶遇平和善良 · 任务详情" },
                    style = t.cardTitle,
                    color = c.textPrimary,
                )
                Text(
                    text = state.subtitle.ifEmpty { "抖音来源 · 14:26 完成，可直接打开或重试同源下载。" },
                    style = t.caption,
                    color = c.textSecondary,
                )
            }

            // Meta card
            MetaCard(state.metaItems)

            // Hint card
            HintCard(state.hint)
        }

        Spacer(modifier = Modifier.height(s.md))

        // Action bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val secondaryText = when (state.status) {
                TaskStatus.FAILED -> "重新下载"
                else -> "删除"
            }
            val primaryText = when (state.status) {
                TaskStatus.DOWNLOADING, TaskStatus.QUEUED -> "暂停下载"
                TaskStatus.PAUSED -> "继续下载"
                else -> "打开内容"
            }
            AppSecondaryButton(
                text = secondaryText,
                onClick = {
                    if (state.status == TaskStatus.FAILED) onAction(DetailAction.RetryDownload)
                    else onAction(DetailAction.DeleteRecord)
                },
                modifier = Modifier.weight(1f),
            )
            AppPrimaryButton(
                text = primaryText,
                onClick = {
                    when (state.status) {
                        TaskStatus.DOWNLOADING, TaskStatus.QUEUED -> onAction(DetailAction.PauseDownload)
                        TaskStatus.PAUSED -> onAction(DetailAction.ResumeDownload)
                        else -> onAction(DetailAction.OpenContent)
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MetaCard(items: List<MetaItem>) {
    val c = AppTheme.colors
    val t = AppTheme.typo
    val r = AppTheme.radius

    val displayItems = items.ifEmpty {
        listOf(
            MetaItem("资源类型", "视频主文件 + 封面"),
            MetaItem("文件状态", "主文件成功，封面已写入"),
            MetaItem("异常提示", "限流时可直接重试或复制链接"),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(r.xl))
            .background(c.bgCard)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "任务信息", style = t.cardTitle, color = c.textPrimary)
        displayItems.forEach { item ->
            Text(
                text = "${item.label}  ${item.value}",
                style = t.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                color = c.textPrimary,
            )
        }
    }
}

@Composable
private fun HintCard(hint: String) {
    val c = AppTheme.colors
    val t = AppTheme.typo
    val r = AppTheme.radius

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(r.xl))
            .background(c.surfaceTint)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "操作建议", style = t.cardTitle, color = c.textPrimary)
        Text(
            text = hint.ifEmpty { "完成任务默认突出『打开内容』，失败任务则把主按钮替换为『重新下载』。" },
            style = t.caption,
            color = c.textSecondary,
        )
    }
}

@Preview(showBackground = true, widthDp = 430, heightDp = 932)
@Composable
private fun DetailScreenPreview() {
    AppDesignTheme {
        DetailContent(
            state = DetailUiState(status = TaskStatus.COMPLETED),
            onAction = {},
        )
    }
}
