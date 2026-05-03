package com.example.videodownloader.ui.redesign.component

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.Surface
import android.view.TextureView
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

    if (inlinePlaybackEnabled && playInline && !playbackSource.isNullOrBlank()) {
        InlineVideoPlayer(
            source = playbackSource,
            modifier = modifier
                .clipToBounds()
                .background(Color.Black),
            onError = { playInline = false },
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
            modifier = if (inlinePlaybackEnabled) modifier.clickable { playInline = true } else modifier,
        )
    } else {
        val placeholderModifier = if (inlinePlaybackEnabled) {
            modifier.background(placeholderColor).clickable { playInline = true }
        } else {
            modifier.background(placeholderColor)
        }
        Box(modifier = placeholderModifier)
    }
}

@Composable
private fun InlineVideoPlayer(
    source: String,
    modifier: Modifier = Modifier,
    onError: () -> Unit,
) {
    val context = LocalContext.current
    val player = remember(source) { MediaPlayer() }

    DisposableEffect(source) {
        onDispose {
            runCatching { player.stop() }
            player.release()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            TextureView(viewContext).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                        val surface = Surface(surfaceTexture)
                        runCatching {
                            player.reset()
                            if (source.startsWith("http://", ignoreCase = true) || source.startsWith("https://", ignoreCase = true)) {
                                player.setDataSource(source)
                            } else {
                                player.setDataSource(context, Uri.parse(source))
                            }
                            player.setSurface(surface)
                            player.isLooping = true
                            player.setOnPreparedListener { it.start() }
                            player.setOnErrorListener { _, _, _ ->
                                surface.release()
                                onError()
                                true
                            }
                            player.prepareAsync()
                        }.onFailure {
                            surface.release()
                            onError()
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) = Unit

                    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                        runCatching {
                            player.pause()
                            player.setSurface(null)
                        }
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
                }
            }
        },
    )
}
