package com.example.videodownloader.ui.redesign.parse_result

import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.videodownloader.ui.redesign.component.AppChip
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
            verticalArrangement = Arrangement.spacedBy(s.md),
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AppChip(
                    text = "返回",
                    onClick = { onAction(ParseResultAction.GoBack) },
                )
                AppChip(
                    text = state.sourceTag.ifEmpty { "抖音图集" },
                    backgroundColor = Color(0xFFEAF7F5),
                    textColor = c.accent,
                )
            }

            // Preview card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(r.xl))
                    .background(c.bgCard)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "资源确认", style = t.pageTitle, color = c.textPrimary)
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
                        .background(Color(0xFFDDEAF4)),
                    contentAlignment = Alignment.Center,
                ) {
                    val currentResource = state.resources.getOrNull(state.currentIndex)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MediaPreview(
                            source = currentResource?.previewUrl,
                            mediaSource = currentResource?.mediaUrl,
                            contentDescription = state.title,
                            isVideo = currentResource?.isVideo == true,
                            contentScale = ContentScale.Fit,
                            placeholderColor = Color(0xFF8FC7E6),
                            modifier = Modifier
                                .size(208.dp, 248.dp)
                                .clip(RoundedCornerShape(18.dp)),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "当前预览 · 原始比例显示",
                            style = t.caption,
                            color = c.textSecondary,
                        )
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
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            // Info card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(r.xl))
                    .background(c.bgCard)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = state.title.ifEmpty { "校园跑偶遇平和善良、温柔大方的学妹 · 图集 6 张" },
                    style = t.sectionTitle.copy(fontSize = 21.nonScaledSp),
                    color = c.textPrimary,
                )
                Text(
                    text = state.resourceMeta.ifEmpty { "原图下载 · WebP · 建议整组保存" },
                    style = t.body,
                    color = c.textSecondary,
                )
            }

            // Version card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(r.xl))
                    .background(Color(0xFFEAF7F5))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "当前选择", style = t.captionSemiBold, color = c.accent)
                Text(
                    text = state.selectedVersion?.description
                        ?: "图片 ${state.currentIndex + 1} · 原始尺寸 · 将按标题顺序命名保存",
                    style = t.body,
                    color = c.textPrimary,
                )
            }
        }

        Spacer(modifier = Modifier.height(s.md))

        // Action bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
