package com.example.videodownloader.ui.redesign.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.videodownloader.ui.redesign.theme.AppTheme

enum class NavItem(val label: String) {
    DOWNLOAD("下载"),
    LIBRARY("资源库"),
    PROFILE("我的"),
}

@Composable
fun AppBottomNav(
    selected: NavItem,
    onSelect: (NavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    val t = AppTheme.typo
    val r = AppTheme.radius

    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(r.xl), clip = false)
            .clip(RoundedCornerShape(r.xl))
            .background(Color(0xE8FFFFFF))
            .padding(horizontal = 16.dp, vertical = 18.dp),
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
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isActive) c.primary else c.surfaceTint),
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
