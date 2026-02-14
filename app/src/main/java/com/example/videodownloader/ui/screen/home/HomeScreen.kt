package com.example.videodownloader.ui.screen.home

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.videodownloader.domain.model.VideoFormat
import com.example.videodownloader.ui.component.AppGradientBackdrop
import com.example.videodownloader.ui.component.AppSectionCard
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenXSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    var hasNotificationPermission by remember {
        mutableStateOf(checkNotificationPermission(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasNotificationPermission = granted
    }

    LaunchedEffect(state.submitMessage) {
        val msg = state.submitMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearSubmitMessage()
    }

    AppGradientBackdrop {
        Column(modifier = Modifier.fillMaxSize()) {
            SnackbarHost(hostState = snackbarHostState)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    HeroHeader(onOpenXSettings = onOpenXSettings)
                }

                if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    item {
                        AppSectionCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("建议开启通知权限", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "用于持续显示下载进度和结果通知。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Button(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                                    Text("授权")
                                }
                            }
                        }
                    }
                }

                item {
                    AppSectionCard {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("输入链接", style = MaterialTheme.typography.titleLarge)
                            OutlinedTextField(
                                value = state.linkInput,
                                onValueChange = viewModel::onLinkChanged,
                                label = { Text("链接或分享文案") },
                                placeholder = { Text("支持抖音文案整段粘贴、X 链接") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 4,
                                maxLines = 8,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { viewModel.fillLinkFromClipboard(readClipboardText(context)) },
                                    enabled = !state.isSubmitting,
                                ) {
                                    Icon(Icons.Outlined.ContentPaste, contentDescription = null)
                                    Text(" 粘贴")
                                }
                                FilledTonalButton(
                                    onClick = viewModel::parseLink,
                                    enabled = !state.isParsing && !state.isSubmitting,
                                ) {
                                    if (state.isParsing) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Text(" 解析中")
                                    } else {
                                        Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                                        Text(" 开始解析")
                                    }
                                }
                            }
                        }
                    }
                }

                state.parseError?.let { error ->
                    item {
                        AppSectionCard {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.Security, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Text(text = error, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }

                state.parsedInfo?.let { info ->
                    val hasRecommendation = info.formats.size > 1 && !state.recommendedFormatId.isNullOrBlank()
                    item {
                        AppSectionCard {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(text = info.title, style = MaterialTheme.typography.titleLarge)
                                state.parsedSourceUrl?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = "可下载选项 ${info.formats.size}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    items(info.formats, key = { it.formatId }) { format ->
                        FormatCard(
                            format = format,
                            isRecommended = hasRecommendation && state.recommendedFormatId == format.formatId,
                            submitting = state.isSubmitting,
                            onDownload = { viewModel.createTask(format) },
                            onPreview = { openPreview(context, format.downloadUrl) },
                        )
                    }
                }

                item { Box(modifier = Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun HeroHeader(onOpenXSettings: () -> Unit) {
    AppSectionCard(contentPadding = PaddingValues(0.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        listOf(Color(0xFF0D66FF), Color(0xFF00A889), Color(0xFF6B4EFF)),
                    ),
                )
                .padding(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Video Downloader",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "本地解析、本地下载，不依赖额外服务器。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.92f),
                )
                ElevatedButton(onClick = onOpenXSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = null)
                    Text(" 打开 X Cookie 设置")
                }
            }
        }
    }
}

@Composable
private fun FormatCard(
    format: VideoFormat,
    isRecommended: Boolean,
    submitting: Boolean,
    onDownload: () -> Unit,
    onPreview: () -> Unit,
) {
    val showPreview = shouldShowPreviewForHls(format)

    AppSectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isRecommended) {
                    Text(
                        text = "推荐",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                Text(
                    text = format.resolution + "." + format.ext,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                buildFormatDetailText(format)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!format.downloadable) {
                    Text(
                        text = "该选项不是可直接下载的视频流",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (showPreview) {
                    OutlinedButton(onClick = onPreview) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                        Text(" 预览")
                    }
                }

                if (isRecommended) {
                    Button(onClick = onDownload, enabled = !submitting && format.downloadable) {
                        Text(if (format.downloadable) "下载推荐" else "不可下载")
                    }
                } else {
                    OutlinedButton(onClick = onDownload, enabled = !submitting && format.downloadable) {
                        Text(if (format.downloadable) "下载" else "不可下载")
                    }
                }
            }
        }
    }
}

private fun readClipboardText(context: Context): String {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = manager.primaryClip ?: return ""
    if (clip.itemCount == 0) return ""
    return clip.getItemAt(0).coerceToText(context)?.toString().orEmpty().trim()
}

private fun checkNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}

private fun buildFormatDetailText(format: VideoFormat): String? {
    if (!format.downloadable) return null

    val chunks = mutableListOf<String>()
    format.durationSec
        ?.takeIf { it > 0.0 }
        ?.let { chunks += "时长 " + formatDuration(it) }

    if (isLikelyHlsFormat(format)) {
        chunks += "分片流"
        val extraText = format.sizeText.orEmpty().trim()
        if (extraText.isNotBlank()) {
            if (looksLikeBitrateText(extraText)) {
                chunks += "码率 " + extraText
            } else if (!isGenericHlsText(extraText)) {
                chunks += extraText
            }
        }
        chunks += "最终大小以下载后为准"
        return chunks.joinToString(" · ")
    }

    val hasExactSize = format.fileSizeBytes
        ?.takeIf { it > 0L }
        ?.let {
            chunks += "大小 " + formatSize(it)
            true
        } ?: false

    val extraText = format.sizeText.orEmpty().trim()
    if (!hasExactSize && extraText.isNotBlank()) {
        if (looksLikeBitrateText(extraText)) {
            chunks += "码率 " + extraText
        } else {
            chunks += extraText
        }
    }
    if (!hasExactSize) {
        chunks += "大小未知"
    }

    return chunks.joinToString(" · ")
}

private fun isLikelyHlsFormat(format: VideoFormat): Boolean {
    if (format.ext.equals("m3u8", ignoreCase = true)) return true
    val lowerUrl = format.downloadUrl.lowercase()
    if (lowerUrl.contains(".m3u8")) return true
    val lowerSize = format.sizeText.orEmpty().lowercase()
    return lowerSize.contains("hls") || lowerSize.contains("m3u8") || lowerSize.contains("分片流")
}

private fun shouldShowPreviewForHls(format: VideoFormat): Boolean {
    if (!isLikelyHlsFormat(format)) return false
    if ((format.fileSizeBytes ?: 0L) > 0L) return false

    val resolution = format.resolution.orEmpty().trim()
    val hasResolutionInfo = Regex(
        """(\d{3,4})\s*p|(\d{3,4})\s*x\s*(\d{3,4})""",
        RegexOption.IGNORE_CASE,
    ).containsMatchIn(resolution)
    val hasBitrateInfo = looksLikeBitrateText(format.sizeText.orEmpty())
    return !hasResolutionInfo && !hasBitrateInfo
}

private fun isGenericHlsText(text: String): Boolean {
    val normalized = text.trim().lowercase()
    if (normalized.isBlank()) return true
    if (normalized == "hls" || normalized == "m3u8") return true
    if (normalized == "分片流") return true
    return false
}

private fun openPreview(context: Context, url: String) {
    if (url.isBlank()) return
    val targetUri = Uri.parse(url)
    val directIntent = Intent(Intent.ACTION_VIEW, targetUri)
    runCatching {
        context.startActivity(directIntent)
    }.onFailure {
        runCatching {
            context.startActivity(Intent.createChooser(directIntent, "选择预览应用"))
        }
    }
}

private fun looksLikeBitrateText(text: String): Boolean {
    return Regex("""\d+(?:\.\d+)?\s*(k|m|g)?bps\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)
}

private fun formatDuration(durationSec: Double): String {
    val totalSec = durationSec.roundToInt().coerceAtLeast(0)
    val hour = totalSec / 3600
    val min = (totalSec % 3600) / 60
    val sec = totalSec % 60
    return if (hour > 0) {
        String.format("%d:%02d:%02d", hour, min, sec)
    } else {
        String.format("%02d:%02d", min, sec)
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "0B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) {
        return String.format("%.0fKB", kb)
    }
    val mb = kb / 1024.0
    if (mb < 1024.0) {
        return String.format("%.1fMB", mb)
    }
    return String.format("%.2fGB", mb / 1024.0)
}
