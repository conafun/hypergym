package com.hypergym.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** 设计语言：低饱和、柔和过渡的暖调配色。
 *  主色珊瑚 #FA734F，辅助天空蓝 #95DAE7，背景奶油 #F6F3E8，文字/强调棕 #7C645E。 */
object HColors {
    val Primary = Color(0xFFFA734F)          // 珊瑚（主色，与新图标暖橙一脉）
    val PrimaryDeep = Color(0xFFE85C3A)      // 渐变尾：更深的珊瑚
    val PrimaryLight = Color(0xFFF5A48C)     // 渐变中间：浅珊瑚
    val PrimaryContainer = Color(0xFFFBE5DC) // 珊瑚浅底
    val OnPrimaryContainer = Color(0xFF8C4A2A)
    val Blue = Color(0xFF95DAE7)             // 天空蓝（辅助/图表）
    val BlueDeep = Color(0xFF6DB9CC)         // 更深的天空蓝
    val Green = Color(0xFF9CCFBF)            // 柔和绿（正向/达标）
    val Purple = Color(0xFFA98BB4)           // 柔和紫
    val Background = Color(0xFFF6F3E8)       // 奶油底
    val Card = Color(0xFFFFFFFF)             // 卡片（纯白）
    val Border = Color(0xFFE8E1D4)           // 分隔（暖米灰）
    val TextPrimary = Color(0xFF2B2B2B)      // 主文字（深灰黑）
    val TextSecondary = Color(0xFF7A7F87)    // 次级文字（中灰）
}

/** 卡片圆角（区块化） */
val CardRadius = RoundedCornerShape(18.dp)

/** 统一配色调色板：动作数据汇总 / 数据透视 / 肌群占比共用。
 *  低饱和、同色系柔和过渡（珊瑚/天空蓝/柔和绿/淡紫等），避免强对比。 */
val ChartPalette = listOf(
    Color(0xFFFA734F), Color(0xFF95DAE7), Color(0xFF9CCFBF), Color(0xFFA98BB4),
    Color(0xFFF0B27A), Color(0xFF7FB3D5), Color(0xFFE8B4B0), Color(0xFFB7C8A0),
    Color(0xFFD9A79B), Color(0xFF8FC5C9), Color(0xFFC9A064), Color(0xFFA9A1B0),
)

private val LightColors = lightColorScheme(
    primary = HColors.Primary,
    onPrimary = HColors.TextPrimary,
    primaryContainer = HColors.PrimaryContainer,
    onPrimaryContainer = HColors.TextPrimary,
    secondary = HColors.Blue,
    onSecondary = HColors.TextPrimary,
    secondaryContainer = Color(0xFFDDF0F5),
    onSecondaryContainer = HColors.TextPrimary,
    tertiary = HColors.Green,
    onTertiary = HColors.TextPrimary,
    tertiaryContainer = Color(0xFFE1EFE9),
    onTertiaryContainer = HColors.TextPrimary,
    error = Color(0xFFD65C54),
    onError = HColors.TextPrimary,
    errorContainer = Color(0xFFF6DFDC),
    onErrorContainer = HColors.TextPrimary,
    background = HColors.Background,
    onBackground = HColors.TextPrimary,
    surface = HColors.Card,
    onSurface = HColors.TextPrimary,
    surfaceVariant = Color(0xFFEDE7DA),
    onSurfaceVariant = HColors.TextSecondary,
    outline = Color(0xFFC9BFA9),
    outlineVariant = HColors.Border,
)

@Composable
fun HyperGymTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}

/** 稀疏几何线条底纹（圆环 + 直线，点缀用，低透明度） */
@Composable
fun DecorativeBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize().background(HColors.Background)) {
        val w = size.width
        val h = size.height
        // 圆环（上/中/下分布，粗细不一）
        drawCircle(color = HColors.Primary.copy(alpha = 0.18f), radius = w * 0.42f, center = Offset(w * 1.12f, -h * 0.06f), style = Stroke(5f))
        drawCircle(color = HColors.Primary.copy(alpha = 0.12f), radius = w * 0.16f, center = Offset(w * 0.10f, -h * 0.02f), style = Stroke(2f))
        drawCircle(color = HColors.Blue.copy(alpha = 0.16f), radius = w * 0.32f, center = Offset(-w * 0.08f, h * 0.30f), style = Stroke(3f))
        drawCircle(color = HColors.Blue.copy(alpha = 0.13f), radius = w * 0.26f, center = Offset(w * 1.06f, h * 0.50f), style = Stroke(3f))
        drawCircle(color = HColors.Primary.copy(alpha = 0.12f), radius = w * 0.20f, center = Offset(w * 0.02f, h * 0.76f), style = Stroke(2.5f))
        // 圆弧（底部右侧半环）
        drawArc(
            color = HColors.Primary.copy(alpha = 0.11f),
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.70f, h * 0.88f),
            size = Size(w * 0.4f, w * 0.4f),
            style = Stroke(width = 2f),
        )
        // 直线（粗中细、上中下）
        drawLine(HColors.Primary.copy(alpha = 0.18f), Offset(-w * 0.02f, h * 0.20f), Offset(w * 0.30f, h * 0.06f), 6f, StrokeCap.Round)
        drawLine(HColors.Blue.copy(alpha = 0.16f), Offset(w * 0.80f, h * 0.05f), Offset(w * 1.06f, h * 0.14f), 3f, StrokeCap.Round)
        drawLine(HColors.Primary.copy(alpha = 0.12f), Offset(w * 0.40f, -h * 0.01f), Offset(w * 0.52f, h * 0.05f), 1.5f)
        drawLine(HColors.Primary.copy(alpha = 0.13f), Offset(w * 0.02f, h * 0.52f), Offset(w * 0.40f, h * 0.44f), 2f)
        drawLine(HColors.Blue.copy(alpha = 0.13f), Offset(w * 0.58f, h * 0.80f), Offset(w * 0.95f, h * 0.88f), 3f, StrokeCap.Round)
    }
}
