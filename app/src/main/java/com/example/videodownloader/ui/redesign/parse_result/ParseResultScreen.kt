package com.example.videodownloader.ui.redesign.parse_result

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.example.videodownloader.R
import com.example.videodownloader.ui.redesign.component.AppTopBar
import com.example.videodownloader.ui.redesign.component.MediaPreview
import com.example.videodownloader.ui.redesign.theme.AppDesignTheme
import com.example.videodownloader.ui.redesign.theme.AppTheme

@Composable
fun ParseResultScreen(
    viewModel: ParseResultViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ParseResultUiEvent.NavigateBack -> onBack()
                ParseResultUiEvent.DownloadStarted -> Unit
                is ParseResultUiEvent.ShowToast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
        }
    }
    ParseResultContent(
        state = state,
        onAction = { action ->
            viewModel.onAction(action)
        },
    )
}

@Composable
private fun ParseResultContent(
    state: ParseResultUiState,
    onAction: (ParseResultAction) -> Unit,
) {
    val c = AppTheme.colors
    val t = AppTheme.typo
    val r = AppTheme.radius
    var viewerResource by remember { mutableStateOf<ResourceItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bgApp),
    ) {
        AppTopBar(
            title = state.sourceTag.ifEmpty { "媒体视频" },
            showBack = true,
            onBack = { onAction(ParseResultAction.GoBack) },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Preview card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(r.xl))
                    .background(c.bgCard)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = state.title,
                    style = t.sectionTitle.copy(fontSize = 21.nonScaledSp),
                    color = c.textPrimary,
                )
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(r.pill))
                            .background(c.surfaceTint)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        val total = state.resources.size.coerceAtLeast(1)
                        Text(
                            text = "${state.currentIndex + 1} / $total",
                            style = t.dataSmall,
                            color = c.primary,
                        )
                    }
                }

                // Media stage
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(RoundedCornerShape(r.lg))
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    val currentResource = state.resources.getOrNull(state.currentIndex)
                    Box(contentAlignment = Alignment.Center) {
                        MediaPreview(
                            source = currentResource?.previewUrl,
                            mediaSource = currentResource?.mediaUrl,
                            contentDescription = state.title,
                            isVideo = currentResource?.isVideo == true,
                            contentScale = ContentScale.Fit,
                            placeholderColor = Color.White,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(18.dp)),
                        )
                        if (currentResource?.isVideo == true) {
                            Icon(
                                painter = painterResource(R.drawable.ic_play_circle),
                                contentDescription = "播放",
                                tint = Color.Unspecified,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clickable { viewerResource = currentResource },
                            )
                        }
                    }
                }
            }

            // Thumbnail row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            ) {
                state.resources.forEachIndexed { i, resource ->
                    val isSelected = i == state.currentIndex
                    Box(
                        modifier = Modifier
                            .size(74.dp, 92.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) c.primary else Color(0xFFD9EAF5))
                            .clickable { onAction(ParseResultAction.SelectResource(i)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        MediaPreview(
                            source = resource.thumbnailUrl,
                            mediaSource = resource.mediaUrl,
                            contentDescription = "资源 ${i + 1}",
                            isVideo = resource.isVideo,
                            inlinePlaybackEnabled = false,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

        }

        Spacer(modifier = Modifier.height(12.dp))

        // Action bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
                .clip(RoundedCornerShape(r.xl))
                .shadow(10.dp, RoundedCornerShape(r.xl))
                .background(Color(0xFFFFFFFFF0.toInt()))
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WhiteButton(
                text = "下载当前",
                onClick = { onAction(ParseResultAction.DownloadCurrent) },
                modifier = Modifier.weight(1f),
            )
            WhiteButton(
                text = "下载全部",
                onClick = { onAction(ParseResultAction.DownloadAll) },
                modifier = Modifier.weight(1f),
            )
        }
    }

    viewerResource?.let { resource ->
        ParseResultVideoViewer(
            resource = resource,
            onClose = { viewerResource = null },
        )
    }
}

@Composable
private fun WhiteButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    val t = AppTheme.typo
    val r = AppTheme.radius

    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(r.pill))
            .background(c.bgCard)
            .border(1.dp, c.borderSoft, RoundedCornerShape(r.pill))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = t.button, color = c.primary)
    }
}

private val Int.nonScaledSp get() = androidx.compose.ui.unit.TextUnit(
    this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp
)

@Composable
private fun ParseResultVideoViewer(
    resource: ResourceItem,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        val source = resource.mediaUrl
        if (!source.isNullOrBlank()) {
            val context = LocalContext.current
            val player = remember(source) {
                ExoPlayer.Builder(context).build().apply {
                    val mediaItem = MediaItem.Builder()
                        .setUri(source)
                        .apply {
                            if (source.contains(".m3u8", ignoreCase = true)) {
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
                onDispose {
                    runCatching {
                        player.clearVideoSurface()
                        player.stop()
                        player.clearMediaItems()
                    }
                    player.release()
                }
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        this.player = player
                        useController = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                update = { it.player = player },
                onRelease = { it.player = null },
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 430, heightDp = 932)
@Composable
private fun ParseResultScreenPreview() {
    AppDesignTheme {
        ParseResultContent(
            state = ParseResultUiState(
                title = "校园跑偶遇平和善良、温柔大方的学妹 · 图集 6 张",
                sourceTag = "抖音图集",
                resourceMeta = "原图下载 · WebP · 建议整组保存",
                resources = List(6) { ResourceItem(id = "$it") },
            ),
            onAction = {},
        )
    }
}
