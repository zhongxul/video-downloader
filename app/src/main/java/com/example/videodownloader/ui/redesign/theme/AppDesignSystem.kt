package com.example.videodownloader.ui.redesign.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class AppColors(
    val primary: Color = Color(0xFF0F5D91),
    val primaryStrong: Color = Color(0xFF0A4B76),
    val accent: Color = Color(0xFF19B7A5),
    val bgApp: Color = Color(0xFFF4F7FB),
    val bgCard: Color = Color(0xFFFFFFFF),
    val surfaceTint: Color = Color(0xFFEAF2F8),
    val borderSoft: Color = Color(0xFFDCE6F0),
    val textPrimary: Color = Color(0xFF112031),
    val textSecondary: Color = Color(0xFF5B6B7C),
    val textMuted: Color = Color(0xFF8EA0B2),
    val success: Color = Color(0xFF18A058),
    val warning: Color = Color(0xFFE6A23C),
    val error: Color = Color(0xFFD64545),
)

@Immutable
data class AppTypo(
    val heroTitle: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
    ),
    val sectionTitle: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
    ),
    val cardTitle: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
    ),
    val pageTitle: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
    ),
    val body: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
    ),
    val bodySemiBold: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    ),
    val caption: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
    ),
    val captionSemiBold: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
    ),
    val label: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
    ),
    val button: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
    ),
    val dataLarge: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
    ),
    val dataMedium: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
    ),
    val dataSmall: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
    ),
    val navLabel: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
    ),
    val navLabelActive: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
    ),
)

@Immutable
data class AppSpacing(
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
)

@Immutable
data class AppRadius(
    val lg: Dp = 18.dp,
    val xl: Dp = 24.dp,
    val pill: Dp = 999.dp,
)

val LocalAppColors = staticCompositionLocalOf { AppColors() }
val LocalAppTypo = staticCompositionLocalOf { AppTypo() }
val LocalAppSpacing = staticCompositionLocalOf { AppSpacing() }
val LocalAppRadius = staticCompositionLocalOf { AppRadius() }

object AppTheme {
    val colors: AppColors @Composable get() = LocalAppColors.current
    val typo: AppTypo @Composable get() = LocalAppTypo.current
    val spacing: AppSpacing @Composable get() = LocalAppSpacing.current
    val radius: AppRadius @Composable get() = LocalAppRadius.current
}

@Composable
fun AppDesignTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalAppColors provides AppColors(),
        LocalAppTypo provides AppTypo(),
        LocalAppSpacing provides AppSpacing(),
        LocalAppRadius provides AppRadius(),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = AppColors().bgApp,
            content = content,
        )
    }
}
