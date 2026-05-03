package com.example.videodownloader.ui.screen.result

import android.graphics.SurfaceTexture
import android.graphics.Matrix
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.videodownloader.R
import com.example.videodownloader.core.net.buildMediaPreviewHeaderMap
import com.example.videodownloader.domain.model.VideoFormat
import com.example.videodownloader.ui.component.AppGradientBackdrop
import com.example.videodownloader.ui.component.AppSectionCard
import kotlin.math.roundToInt

@Composable
fun ParseResultScreen(
    viewModel: ParseResultViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val parsedInfo = state.parsedInfo
    val formats = parsedInfo?.formats.orEmpty()

    val handleBack = {
        viewModel.clearResultPayload()
        onBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearResultPayload()
        }
    }

    LaunchedEffect(state.actionMessage) {
        val message = state.actionMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearMessage()
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

            if (parsedInfo == null || formats.isEmpty()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AppSectionCard {
                        Text(
                            text = stringResource(R.string.parse_result_empty),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    OutlinedButton(onClick = handleBack) { Text(stringResource(R.string.common_back)) }
                }
                return@Column
            }

            val pagerState = rememberPagerState(
                initialPage = state.selectedIndex.coerceIn(0, formats.lastIndex),
                pageCount = { formats.size },
            )

            LaunchedEffect(pagerState.currentPage) {
                viewModel.selectIndex(pagerState.currentPage)
            }

            val current = formats[state.selectedIndex.coerceIn(0, formats.lastIndex)]

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    AppSectionCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedButton(onClick = handleBack) { Text(stringResource(R.string.common_back)) }
                                Text(
                                    text = "${state.selectedIndex + 1}/${formats.size}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(parsedInfo.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            state.sourceUrl?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = if (formats.size > 1) {
                                    stringResource(R.string.parse_result_multi_hint)
                                } else {
                                    stringResource(R.string.parse_result_single_hint)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                item {
                    AppSectionCard(contentPadding = PaddingValues(10.dp)) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp),
                        ) { page ->
                            FormatPreviewCard(
                                format = formats[page],
                                previewImageUrl = if (isImageFormat(formats[page])) {
                                    formats[page].downloadUrl
                                } else {
                                    parsedInfo.coverUrl
                                },
                                isRecommended = state.recommendedFormatId == formats[page].formatId && formats.size > 1,
                            )
                        }
                    }
                }

                item {
                    AppSectionCard {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "${if (isImageFormat(current)) stringResource(R.string.parse_result_media_image) else stringResource(R.string.parse_result_media_video)} · ${current.resolution}.${current.ext}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            buildFormatDetail(current)?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                item {
                    AppSectionCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                onClick = viewModel::downloadCurrent,
                                enabled = !state.isSubmitting && current.downloadable,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.parse_result_download_current))
                            }
                            FilledTonalButton(
                                onClick = viewModel::downloadAll,
                                enabled = !state.isSubmitting,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.parse_result_download_all))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatPreviewCard(
    format: VideoFormat,
    previewImageUrl: String?,
    isRecommended: Boolean,
) {
    val context = LocalContext.current
    val imagePreviewUrl = if (isImageFormat(format)) {
        format.downloadUrl
    } else {
        previewImageUrl.orEmpty()
    }
    val imageHeaders = remember(imagePreviewUrl) { buildMediaPreviewHeaderMap(imagePreviewUrl) }
    var zoomImageUrl by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = if (isImageFormat(format)) stringResource(R.string.parse_result_media_image) else stringResource(R.string.parse_result_media_video),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
            if (isRecommended) {
                Text(
                    text = stringResource(R.string.parse_result_tag_recommended),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(999.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            if (!format.downloadable) {
                Text(
                    text = stringResource(R.string.parse_result_tag_not_downloadable),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(999.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (isImageFormat(format) && imagePreviewUrl.isNotBlank()) {
                val request = remember(imagePreviewUrl) {
                    ImageRequest.Builder(context)
                        .data(imagePreviewUrl)
                        .crossfade(true)
                        .apply {
                            imageHeaders.forEach { (key, value) ->
                                addHeader(key, value)
                            }
                        }
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = if (isImageFormat(format)) {
                        stringResource(R.string.parse_result_preview_image)
                    } else {
                        stringResource(R.string.parse_result_preview_video_cover)
                    },
                    contentScale = if (isImageFormat(format)) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                        .background(Color.Black.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .padding(6.dp),
                )
                FilledTonalButton(
                    onClick = { zoomImageUrl = imagePreviewUrl.takeIf { it.isNotBlank() } },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp),
                ) {
                    Text(stringResource(R.string.parse_result_zoom_image))
                }
            } else if (!isImageFormat(format)) {
                VideoPreviewCard(
                    videoUrl = format.downloadUrl,
                    previewImageUrl = imagePreviewUrl,
                    previewHeaders = imageHeaders,
                )
            } else {
                Text(
                    text = stringResource(R.string.parse_result_no_preview),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (!zoomImageUrl.isNullOrBlank()) {
        ZoomImageDialog(
            imageUrl = zoomImageUrl.orEmpty(),
            onDismiss = { zoomImageUrl = null },
        )
    }
}

@Composable
private fun VideoPreviewCard(
    videoUrl: String,
    previewImageUrl: String,
    previewHeaders: Map<String, String>,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (previewImageUrl.isNotBlank()) {
            val context = LocalContext.current
            val request = remember(previewImageUrl) {
                ImageRequest.Builder(context)
                    .data(previewImageUrl)
                    .crossfade(true)
                    .apply {
                        previewHeaders.forEach { (key, value) ->
                            addHeader(key, value)
                        }
                    }
                    .build()
            }
                AsyncImage(
                    model = request,
                    contentDescription = stringResource(R.string.parse_result_preview_video_cover),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
                    .background(Color.Black.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(6.dp),
            )
        }

        AndroidView(
            factory = { context ->
                object : TextureView(context), TextureView.SurfaceTextureListener {
                    private var surface: Surface? = null
                    private var mediaPlayer: MediaPlayer? = null
                    private var startedUrl: String? = null
                    private var videoWidthPx: Int = 0
                    private var videoHeightPx: Int = 0

                    init {
                        surfaceTextureListener = this
                        isOpaque = false
                    }

                    private fun applyCenterInsideTransform() {
                        val viewWidth = width
                        val viewHeight = height
                        val sourceWidth = videoWidthPx
                        val sourceHeight = videoHeightPx
                        if (viewWidth <= 0 || viewHeight <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
                            setTransform(Matrix())
                            return
                        }

                        val scale = minOf(
                            viewWidth.toFloat() / sourceWidth.toFloat(),
                            viewHeight.toFloat() / sourceHeight.toFloat(),
                        )
                        val scaledWidth = sourceWidth * scale
                        val scaledHeight = sourceHeight * scale
                        val scaleX = scaledWidth / viewWidth.toFloat()
                        val scaleY = scaledHeight / viewHeight.toFloat()
                        val matrix = Matrix().apply {
                            setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
                        }
                        setTransform(matrix)
                    }

                    private fun releasePlayer() {
                        mediaPlayer?.runCatching {
                            stop()
                        }
                        mediaPlayer?.release()
                        mediaPlayer = null
                    }

                    private fun ensurePlayer(texture: SurfaceTexture) {
                        if (startedUrl == videoUrl && mediaPlayer != null) return
                        releasePlayer()
                        surface?.release()
                        surface = Surface(texture)
                        mediaPlayer = MediaPlayer().apply {
                            isLooping = true
                            setVolume(0f, 0f)
                            setSurface(surface)
                            setOnPreparedListener { player ->
                                videoWidthPx = player.videoWidth
                                videoHeightPx = player.videoHeight
                                applyCenterInsideTransform()
                                player.start()
                            }
                            setOnVideoSizeChangedListener { _, width, height ->
                                videoWidthPx = width
                                videoHeightPx = height
                                applyCenterInsideTransform()
                            }
                            setOnErrorListener { _, _, _ ->
                                true
                            }
                            setDataSource(videoUrl)
                            prepareAsync()
                        }
                        startedUrl = videoUrl
                    }

                    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                        ensurePlayer(surfaceTexture)
                    }

                    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                        applyCenterInsideTransform()
                    }

                    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                        releasePlayer()
                        surface?.release()
                        surface = null
                        startedUrl = null
                        videoWidthPx = 0
                        videoHeightPx = 0
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
                }
            },
            update = { view ->
                val texture = view.surfaceTexture
                if (texture != null) {
                    view.surfaceTextureListener?.onSurfaceTextureAvailable(texture, view.width, view.height)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
                .background(Color.Transparent, RoundedCornerShape(12.dp)),
        )

        Text(
            text = stringResource(R.string.parse_result_video_preview_hint),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ZoomImageDialog(
    imageUrl: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val headers = remember(imageUrl) { buildMediaPreviewHeaderMap(imageUrl) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset += panChange
    }
    val request = remember(imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(true)
            .apply {
                headers.forEach { (key, value) ->
                    addHeader(key, value)
                }
            }
            .build()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = request,
                contentDescription = stringResource(R.string.parse_result_zoom_image_content),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(transformState)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
            )
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
            ) {
                Text(stringResource(R.string.parse_result_close))
            }
            if (scale > 1.05f) {
                FilledTonalButton(
                    onClick = {
                        scale = 1f
                        offset = Offset.Zero
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                ) {
                    Text(stringResource(R.string.parse_result_reset_zoom))
                }
            }
        }
    }
}

private fun isImageFormat(format: VideoFormat): Boolean {
    return when (format.ext.lowercase()) {
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic" -> true
        else -> false
    }
}

private fun buildFormatDetail(format: VideoFormat): String? {
    if (!format.downloadable) return null
    if (isImageFormat(format)) {
        return "原图下载 · 按顺序保存"
    }

    val chunks = mutableListOf<String>()
    format.durationSec
        ?.takeIf { it > 0.0 }
        ?.let {
            val total = it.roundToInt().coerceAtLeast(0)
            val h = total / 3600
            val m = (total % 3600) / 60
            val s = total % 60
            val text = if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
            chunks += "时长 $text"
        }
    format.fileSizeBytes
        ?.takeIf { it > 0L }
        ?.let {
            val kb = it / 1024.0
            val sizeText = if (kb < 1024.0) {
                String.format("%.0fKB", kb)
            } else {
                val mb = kb / 1024.0
                if (mb < 1024.0) String.format("%.1fMB", mb) else String.format("%.2fGB", mb / 1024.0)
            }
            chunks += "大小 $sizeText"
        }
    if (!format.sizeText.isNullOrBlank()) {
        chunks += format.sizeText
    }
    if (chunks.isEmpty()) chunks += "大小未知"
    return chunks.joinToString(" · ")
}
