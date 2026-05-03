package com.example.videodownloader.ui.redesign.download

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.videodownloader.ui.redesign.component.AppBottomNav
import com.example.videodownloader.ui.redesign.component.AppPrimaryButton
import com.example.videodownloader.ui.redesign.component.NavItem
import com.example.videodownloader.ui.redesign.component.StatCard
import com.example.videodownloader.ui.redesign.theme.AppDesignTheme
import com.example.videodownloader.ui.redesign.theme.AppTheme

@Composable
fun DownloadScreen(
    viewModel: DownloadViewModel,
    onNavigateToLibrary: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToParseResult: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DownloadUiEvent.NavigateToParseResult -> onNavigateToParseResult(event.parseRecordId)
                is DownloadUiEvent.ShowToast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
        }
    }
    DownloadContent(
        state = state,
        onAction = viewModel::onAction,
        onNavSelect = { item ->
            when (item) {
                NavItem.LIBRARY -> onNavigateToLibrary()
                NavItem.PROFILE -> onNavigateToProfile()
                else -> {}
            }
        },
    )
}

@Composable
private fun DownloadContent(
    state: DownloadUiState,
    onAction: (DownloadAction) -> Unit,
    onNavSelect: (NavItem) -> Unit,
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
            // Hero block
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(r.xl))
                    .background(c.bgCard)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = "下载工作台", style = t.captionSemiBold, color = c.accent)
                Text(
                    text = "一键解析、确认资源、持续管理下载进度",
                    style = t.heroTitle,
                    color = c.textPrimary,
                )
                Text(
                    text = "今天已完成 ${state.todayCompleted} 项，失败 0 项，抖音与 X 入口已就绪。",
                    style = t.body,
                    color = c.textSecondary,
                )
            }

            // Input block
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(r.xl))
                    .background(c.primary)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "粘贴分享链接后开始解析",
                    style = t.bodySemiBold,
                    color = Color(0xFFD9F3FF),
                )
                InputField(
                    value = state.linkInput,
                    onValueChange = { onAction(DownloadAction.LinkChanged(it)) },
                    onPaste = { onAction(DownloadAction.PasteFromClipboard) },
                )
                AppPrimaryButton(
                    text = if (state.isParsing) "解析中..." else "开始解析",
                    onClick = { onAction(DownloadAction.StartParse) },
                )
                state.parseError?.let { error ->
                    Text(text = error, style = t.captionSemiBold, color = Color.White)
                }
            }

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCard(
                    label = "今日完成",
                    value = state.todayCompleted.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "排队中",
                    value = state.queuedCount.toString(),
                    modifier = Modifier.weight(1f),
                )
            }

            // Recent parse card
            state.recentParse?.let { recent ->
                RecentParseCard(recent)
            }

            // Queue summary card
            state.queueSummary?.let { queue ->
                QueueSummaryCard(queue, onClick = { onAction(DownloadAction.NavigateToLibrary) })
            }
        }

        Spacer(modifier = Modifier.height(s.md))
        AppBottomNav(selected = NavItem.DOWNLOAD, onSelect = onNavSelect)
    }
}

@Composable
private fun InputField(
    value: String,
    onValueChange: (String) -> Unit,
    onPaste: () -> Unit,
) {
    val c = AppTheme.colors
    val t = AppTheme.typo
    val r = AppTheme.radius

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(r.lg))
            .background(Color.White)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = t.body.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = c.textPrimary,
            ),
            modifier = Modifier.weight(1f),
            singleLine = true,
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    Text(
                        text = "https://v.douyin.com/xxxxxxx/",
                        style = TextStyle(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = t.body.fontSize,
                            color = c.textMuted,
                        ),
                    )
                }
                innerTextField()
            },
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(c.let { AppTheme.radius.pill }))
                .background(c.surfaceTint)
                .clickable(onClick = onPaste)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(text = "粘贴", style = t.label, color = c.primary)
        }
    }
}

@Composable
private fun RecentParseCard(info: RecentParseInfo) {
    val c = AppTheme.colors
    val t = AppTheme.typo
    val r = AppTheme.radius

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(r.xl))
            .background(c.bgCard)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "最近一次解析", style = t.captionSemiBold, color = c.textSecondary)
        Text(
            text = "${info.title} · ${info.resourceDesc}",
            style = t.sectionTitle,
            color = c.textPrimary,
        )
        Text(
            text = "${info.timeAgo} · 可继续进入确认页或直接下载全部",
            style = t.body,
            color = c.textSecondary,
        )
    }
}

@Composable
private fun QueueSummaryCard(queue: QueueSummary, onClick: () -> Unit) {
    val c = AppTheme.colors
    val t = AppTheme.typo
    val r = AppTheme.radius

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(r.xl))
            .background(Color(0xFFEAF7F5))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "队列摘要", style = t.captionSemiBold, color = c.accent)
        Text(
            text = "${queue.waitingCount} 项等待下载 · ${queue.retryCount} 项需要重试 · 点击可进入资源库查看全部状态",
            style = t.body,
            color = c.textPrimary,
        )
    }
}

@Preview(showBackground = true, widthDp = 430, heightDp = 932)
@Composable
private fun DownloadScreenPreview() {
    AppDesignTheme {
        DownloadContent(
            state = DownloadUiState(
                todayCompleted = 12,
                queuedCount = 3,
                recentParse = RecentParseInfo(
                    title = "校园跑偶遇平和善良、温柔大方的学妹",
                    resourceDesc = "图集 6 张",
                    timeAgo = "3 分钟前",
                ),
                queueSummary = QueueSummary(waitingCount = 2, retryCount = 1),
            ),
            onAction = {},
            onNavSelect = {},
        )
    }
}
