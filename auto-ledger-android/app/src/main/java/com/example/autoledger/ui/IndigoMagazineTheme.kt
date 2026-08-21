package com.example.autoledger.ui

import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 杂志靛蓝瓷主题（第 0 步主题改造，commit 目标：pre-ui-theme 之后）。
 *
 * 设计语言：
 *  - 主色：靛蓝（瓷釉蓝），承载标题/强调/选中态
 *  - 底：瓷白 / 米白（暖调，非纯白），正文墨色（非纯黑）
 *  - 副色：陶赭（杂志温度，用于次要点缀，如今天边框）
 *  - 标题：衬线字体（系统 Noto Serif），正文无衬线 —— 杂志编辑感
 *  - 语义色（支出红 / 收入绿 / 分类色）仍由各屏幕硬编码保留，不并入主题
 */

/** 靛蓝瓷配色（浅色）。 */
val IndigoMagazineColors = lightColorScheme(
    primary = Color(0xFF2C3E99),        // 靛蓝主色
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E6FA),   // 淡靛青（选中底色/容器）
    onPrimaryContainer = Color(0xFF182454),
    secondary = Color(0xFF9A5B2E),       // 陶赭点缀
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF3E2D2),
    onSecondaryContainer = Color(0xFF4A2A10),
    background = Color(0xFFF6F2E9),      // 米白瓷底
    onBackground = Color(0xFF26231C),    // 墨色正文
    surface = Color(0xFFFBF8F1),         // 暖白卡片面
    onSurface = Color(0xFF26231C),
    surfaceVariant = Color(0xFFE9E2D3),  // 浅米（输入框/次要面）
    onSurfaceVariant = Color(0xFF6E6655),// 暖灰（辅助文字）
    outline = Color(0xFFB9B09B),
    outlineVariant = Color(0xFFD9D0BC),
    error = Color(0xFFC0392B),           // 柔红（支出/警示，保留中国语义）
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DFDB),
    onErrorContainer = Color(0xFF7A1508),
)

/** 杂志衬线标题 + 无衬线正文。 */
val IndigoMagazineTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.2.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    // body* / label* 保持默认无衬线，正文干净
)
