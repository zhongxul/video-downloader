package com.example.videodownloader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF0052CC),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE8FF),
    onPrimaryContainer = Color(0xFF001B4D),
    secondary = Color(0xFF00846C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC9F2E7),
    onSecondaryContainer = Color(0xFF002D24),
    tertiary = Color(0xFF6B4EFF),
    onTertiary = Color.White,
    background = Color(0xFFF4F7FD),
    onBackground = Color(0xFF121A2B),
    surface = Color.White,
    onSurface = Color(0xFF1B2435),
    surfaceVariant = Color(0xFFE6ECF8),
    onSurfaceVariant = Color(0xFF4A5770),
    outline = Color(0xFF8B96AD),
    error = Color(0xFFB42318),
    onError = Color.White,
    errorContainer = Color(0xFFFFE6E2),
    onErrorContainer = Color(0xFF601410),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB8FF),
    onPrimary = Color(0xFF002A75),
    primaryContainer = Color(0xFF003FAF),
    onPrimaryContainer = Color(0xFFDDE8FF),
    secondary = Color(0xFF7FDCC5),
    onSecondary = Color(0xFF00392E),
    secondaryContainer = Color(0xFF005141),
    onSecondaryContainer = Color(0xFFC9F2E7),
    tertiary = Color(0xFFC4B7FF),
    onTertiary = Color(0xFF301C8B),
    background = Color(0xFF0C1220),
    onBackground = Color(0xFFE7ECF6),
    surface = Color(0xFF121B2D),
    onSurface = Color(0xFFE7ECF6),
    surfaceVariant = Color(0xFF263148),
    onSurfaceVariant = Color(0xFFC2CBE0),
    outline = Color(0xFF8D97AE),
    error = Color(0xFFFFB4A9),
    onError = Color(0xFF680E0B),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD4),
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
)

@Composable
fun VideoDownloaderTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content,
    )
}
