package com.example.videodownloader.ui.redesign.component

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MediaPreview(
    source: String?,
    mediaSource: String? = null,
    contentDescription: String?,
    isVideo: Boolean,
    inlinePlaybackEnabled: Boolean = true,
    playTrigger: Int = 0,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderColor: Color = Color(0xFFD9EAF5),
) {
    if (source.isNullOrBlank()) {
        Box(modifier = modifier.background(placeholderColor))
        return
    }

    if (!isVideo) {
        AsyncImage(
            model = source,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
        )
        return
    }

    val context = LocalContext.current
    var playInline by remember(source, mediaSource, inlinePlaybackEnabled) { mutableStateOf(false) }
    val playbackSource = mediaSource?.takeIf { it.isNotBlank() } ?: source
    var imagePreviewFailed by remember(source) { mutableStateOf(false) }
    if (inlinePlaybackEnabled && playTrigger > 0) {
        playInline = true
    }

    if (inlinePlaybackEnabled && playInline && !playbackSource.isNullOrBlank()) {
        InlineVideoPlayer(
            source = playbackSource,
            modifier = modifier
                .clipToBounds()
                .background(Color.Black),
        )
        return
    }

    if (!imagePreviewFailed && isImagePreviewSource(source, playbackSource, mediaSource)) {
        AsyncImage(
            model = source,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
            onError = { imagePreviewFailed = true },
        )
        return
    }

    val bitmap by produceState<Bitmap?>(initialValue = null, source) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                MediaMetadataRetriever().use { retriever ->
                    if (source.startsWith("http://", ignoreCase = true) || source.startsWith("https://", ignoreCase = true)) {
                        retriever.setDataSource(source, emptyMap())
                    } else {
                        retriever.setDataSource(context, Uri.parse(source))
                    }
                    retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
            }.getOrNull()
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
        )
    } else {
        Box(modifier = modifier.background(placeholderColor))
    }
}

@Composable
private fun InlineVideoPlayer(
    source: String,
    modifier: Modifier = Modifier,
) {
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

    DisposableEffect(source) {
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
        modifier = modifier,
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                this.player = player
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        update = { it.player = player },
        onRelease = { view ->
            view.player = null
        },
    )
}

private fun isImagePreviewSource(source: String, playbackSource: String?, mediaSource: String?): Boolean {
    if (!mediaSource.isNullOrBlank() && mediaSource != source) return true
    if (playbackSource != source) return true

    val path = runCatching { Uri.parse(source).lastPathSegment.orEmpty() }.getOrDefault(source)
    val cleanPath = path.substringBefore('?').substringBefore('#').lowercase()
    return cleanPath.endsWith(".jpg") ||
        cleanPath.endsWith(".jpeg") ||
        cleanPath.endsWith(".png") ||
        cleanPath.endsWith(".webp") ||
        cleanPath.endsWith(".gif") ||
        cleanPath.endsWith(".bmp") ||
        cleanPath.endsWith(".heic")
}
