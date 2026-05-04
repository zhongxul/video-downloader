package com.example.videodownloader.ui.redesign.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.videodownloader.ui.redesign.component.AppBottomNav
import com.example.videodownloader.ui.redesign.component.AppTopBar
import com.example.videodownloader.ui.redesign.component.NavItem
import com.example.videodownloader.ui.redesign.theme.AppDesignTheme
import com.example.videodownloader.ui.redesign.theme.AppTheme

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onNavigateToDownload: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToXCookieSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ProfileUiEvent.ShowToast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is ProfileUiEvent.OpenSystemSettings -> Unit
                ProfileUiEvent.NavigateToXCookieSettings -> onNavigateToXCookieSettings()
            }
        }
    }
    ProfileContent(
        state = state,
        onAction = viewModel::onAction,
        onNavSelect = { item ->
            when (item) {
                NavItem.DOWNLOAD -> onNavigateToDownload()
                NavItem.LIBRARY -> onNavigateToLibrary()
                else -> {}
            }
        },
    )
}

@Composable
private fun ProfileContent(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    onNavSelect: (NavItem) -> Unit,
) {
    val c = AppTheme.colors
    val t = AppTheme.typo
    val r = AppTheme.radius

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bgApp),
    ) {
        AppTopBar(title = "信息中心")
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Profile card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(r.xl))
                    .background(c.primary)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color(0x24FFFFFF)),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "本机下载中心",
                            style = t.pageTitle,
                            color = Color.White,
                        )
                        Text(
                            text = "默认保存视频、图片与解析记录",
                            style = t.caption,
                            color = Color(0xFFDCE6F0),
                        )
                    }
                }
            }

            // Settings card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(r.xl))
                    .background(c.bgCard)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = "常用设置", style = t.cardTitle, color = c.textPrimary)
                SettingRow(
                    title = "保存位置",
                    value = state.savePath,
                    onClick = { onAction(ProfileAction.OpenSavePath) },
                )
                SettingSwitchRow(
                    title = "下载完成后通知",
                    value = if (state.notificationEnabled) "已开启" else "已关闭",
                    checked = state.notificationEnabled,
                    onCheckedChange = { onAction(ProfileAction.ToggleNotification) },
                )
                SettingRow(
                    title = "X Cookie 设置",
                    value = "用于解析需要登录态的 X 内容",
                    onClick = { onAction(ProfileAction.OpenXCookieSettings) },
                )
            }

        }

        AppBottomNav(selected = NavItem.PROFILE, onSelect = onNavSelect)
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    value: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val c = AppTheme.colors
    val t = AppTheme.typo
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(c.surfaceTint)
            .border(1.dp, c.borderSoft, RoundedCornerShape(18.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = t.body.copy(fontWeight = FontWeight.SemiBold),
                color = c.textPrimary,
            )
            Text(
                text = value,
                style = t.caption,
                color = c.textSecondary,
                maxLines = 1,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    val c = AppTheme.colors
    val t = AppTheme.typo
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(c.surfaceTint)
            .border(1.dp, c.borderSoft, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = t.body.copy(fontWeight = FontWeight.SemiBold),
                color = c.textPrimary,
            )
            Text(
                text = value,
                style = t.caption,
                color = c.textSecondary,
                maxLines = 1,
            )
        }
        Text(text = "›", style = t.cardTitle, color = c.primary)
    }
}

@Preview(showBackground = true, widthDp = 430, heightDp = 932)
@Composable
private fun ProfileScreenPreview() {
    AppDesignTheme {
        ProfileContent(
            state = ProfileUiState(),
            onAction = {},
            onNavSelect = {},
        )
    }
}
