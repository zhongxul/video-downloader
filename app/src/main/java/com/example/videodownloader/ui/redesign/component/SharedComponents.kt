package com.example.videodownloader.ui.redesign.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.videodownloader.ui.redesign.theme.AppTheme

enum class NavItem(val label: String) {
    DOWNLOAD("下载"),
    LIBRARY("资源库"),
    PROFILE("我的"),
}

@Composable
fun AppTopBar(
    title: String? = null,
    showBack: Boolean = false,
    onBack: () -> Unit = {},
    rightActionText: String? = null,
    onRightAction: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    val t = AppTheme.typo
    val r = AppTheme.radius

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(c.bgApp)
            .statusBarsPadding()
            .height(64.dp)
            .padding(horizontal = 20.dp),
    ) {
        if (showBack) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(48.dp)
                    .clip(RoundedCornerShape(r.pill))
                    .background(c.bgCard)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "返回",
                    tint = c.textPrimary,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                style = t.pageTitle,
                color = c.textPrimary,
                modifier = Modifier.align(Alignment.Center),
                maxLines = 1,
            )
        }

        if (!rightActionText.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .heightIn(min = 48.dp)
                    .widthIn(min = 88.dp)
                    .clip(RoundedCornerShape(r.pill))
                    .background(c.bgCard)
                    .clickable(onClick = onRightAction)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = rightActionText, style = t.label, color = c.primary)
            }
        }
    }
}

@Composable
fun AppBottomNav(
    selected: NavItem,
    onSelect: (NavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    val t = AppTheme.typo
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xF8FFFFFF))
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavItem.entries.forEach { item ->
            val isActive = item == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(item) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = item.icon(),
                    contentDescription = item.label,
                    tint = if (isActive) c.primary else c.textSecondary,
                    modifier = Modifier.size(30.dp),
                )
                Text(
                    text = item.label,
                    style = if (isActive) t.navLabelActive else t.navLabel,
                    color = if (isActive) c.primary else c.textSecondary,
                )
            }
        }
    }
}

private fun NavItem.icon(): ImageVector {
    return when (this) {
        NavItem.DOWNLOAD -> Icons.Default.FileDownload
        NavItem.LIBRARY -> Icons.Default.VideoLibrary
        NavItem.PROFILE -> Icons.Default.Person
    }
}

@Composable
fun AppPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = AppTheme.colors.primary,
    contentColor: Color = Color.White,
) {
    val r = AppTheme.radius
    val t = AppTheme.typo

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(r.pill))
            .background(containerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = t.button, color = contentColor)
    }
}

@Composable
fun AppSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    val r = AppTheme.radius
    val t = AppTheme.typo

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(r.pill))
            .background(c.bgCard)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = t.button, color = c.primary)
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = AppTheme.colors.bgCard,
    content: @Composable () -> Unit,
) {
    val r = AppTheme.radius
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(r.xl))
            .background(backgroundColor)
    ) {
        content()
    }
}

@Composable
fun AppChip(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = AppTheme.colors.bgCard,
    textColor: Color = AppTheme.colors.textPrimary,
    onClick: (() -> Unit)? = null,
) {
    val t = AppTheme.typo
    val r = AppTheme.radius

    val base = modifier
        .heightIn(min = 48.dp)
        .clip(RoundedCornerShape(r.pill))
        .background(backgroundColor)
        .padding(horizontal = 18.dp, vertical = 12.dp)

    val mod = if (onClick != null) base.clickable(onClick = onClick) else base

    Box(modifier = mod, contentAlignment = Alignment.Center) {
        Text(text = text, style = t.label, color = textColor)
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    val t = AppTheme.typo
    val r = AppTheme.radius

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(r.lg))
            .shadow(6.dp, RoundedCornerShape(r.lg))
            .background(c.bgCard)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = label, style = t.caption, color = c.textSecondary)
        Text(text = value, style = t.dataLarge, color = c.textPrimary)
    }
}
