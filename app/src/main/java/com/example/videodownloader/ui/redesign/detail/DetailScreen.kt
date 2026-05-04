package com.example.videodownloader.ui.redesign.detail

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.videodownloader.R
import com.example.videodownloader.ui.screen.history.MediaActionHelper
import com.example.videodownloader.ui.redesign.component.MediaPreview
import com.example.videodownloader.ui.redesign.component.AppPrimaryButton
import com.example.videodownloader.ui.redesign.component.AppTopBar
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
    var viewerItem by remember(state.mediaItems) { mutableStateOf<DetailMediaItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bgApp),
    ) {
        AppTopBar(
            title = if (state.status == TaskStatus.COMPLETED) state.sourceTitle else null,
            showBack = true,
            onBack = { onAction(DetailAction.GoBack) },
            rightActionText = if (state.status == TaskStatus.COMPLETED) null else "删除记录",
            onRightAction = { onAction(DetailAction.DeleteRecord) },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Title block
            TitleBlock(state = state)

            if (state.status == TaskStatus.COMPLETED) {
                CompletedMediaFlow(
                    state = state,
                    onOpenMedia = { index -> viewerItem = state.mediaItems.getOrNull(index) },
                )
            } else {
                TaskPreviewCard(
                    state = state,
                    onAction = onAction,
                )
                MetaCard(state.metaItems)
            }
        }

        // Action bar
        if (state.status != TaskStatus.COMPLETED) {
            val actionBar = state.actionBar()
            AppPrimaryButton(
                text = actionBar.primaryText,
                onClick = {
                    when (state.status) {
                        TaskStatus.DOWNLOADING, TaskStatus.QUEUED -> onAction(DetailAction.PauseDownload)
                        TaskStatus.PAUSED -> onAction(DetailAction.ResumeDownload)
                        TaskStatus.FAILED -> onAction(DetailAction.RetryDownload)
                        else -> onAction(DetailAction.OpenContent)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            )
        }
    }

    viewerItem?.let { item ->
        InAppMediaViewer(
            item = item,
            onClose = { viewerItem = null },
        )
    }
}

@Composable
private fun TitleBlock(state: DetailUiState) {
    val c = AppTheme.colors
    val t = AppTheme.typo
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
}

@Composable
private fun TaskPreviewCard(
    state: DetailUiState,
    onAction: (DetailAction) -> Unit,
) {
    val c = AppTheme.colors
    val t = AppTheme.typo
    val r = AppTheme.radius
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(r.xl))
            .background(c.bgCard)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val statusColor = when (state.status) {
            TaskStatus.FAILED -> c.error
            TaskStatus.DOWNLOADING -> c.primary
            TaskStatus.PAUSED -> c.warning
            else -> c.textSecondary
        }
        val statusBg = when (state.status) {
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
                text = state.statusLabel,
                style = t.label,
                color = statusColor,
            )
        }

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
}

@Composable
private fun CompletedMediaFlow(
    state: DetailUiState,
    onOpenMedia: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        state.mediaItems.forEachIndexed { index, item ->
            CompletedMediaBlock(
                item = item,
                onOpen = { onOpenMedia(index) },
            )
        }
    }
}

@Composable
private fun CompletedMediaBlock(
    item: DetailMediaItem,
    onOpen: () -> Unit,
) {
    val r = AppTheme.radius
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(r.lg))
            .background(Color.White),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        MediaPreview(
            source = item.previewUrl,
            mediaSource = item.mediaUrl,
            contentDescription = item.title,
            isVideo = item.isVideo,
            inlinePlaybackEnabled = false,
            contentScale = ContentScale.Fit,
            placeholderColor = Color.White,
            modifier = Modifier
                .fillMaxSize()
                .then(if (!item.isVideo) Modifier.clickable(onClick = onOpen) else Modifier),
        )
        if (item.isVideo) {
            Icon(
                painter = painterResource(R.drawable.ic_play_circle),
                contentDescription = "播放",
                tint = Color.Unspecified,
                modifier = Modifier
                    .height(56.dp)
                    .clickable(onClick = onOpen),
            )
        }
    }
}

@Composable
private fun InAppMediaViewer(
    item: DetailMediaItem,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f)),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        if (item.isVideo) {
            InAppVideoPlayer(
                source = item.mediaUrl ?: item.saveUri,
                ext = item.ext,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AsyncImage(
                model = item.saveUri ?: item.previewUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onClose),
            )
        }
    }
}

@Composable
private fun InAppVideoPlayer(
    source: String?,
    ext: String,
    modifier: Modifier = Modifier,
) {
    if (source.isNullOrBlank()) {
        Box(modifier = modifier.background(Color.Black))
        return
    }

    val context = LocalContext.current
    val player = remember(source) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.Builder()
                .setUri(source)
                .apply {
                    if (ext.equals("m3u8", ignoreCase = true) || source.contains(".m3u8", ignoreCase = true)) {
                        setMimeType(MimeTypes.APPLICATION_M3U8)
                    }
                }
                .build()
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    androidx.compose.runtime.DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                this.player = player
                useController = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        update = { it.player = player },
    )
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
