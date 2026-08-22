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

/** 设计语言：暖橙 + 白卡 + 柔和阴影（与 ui-prototype 一致） */
object HColors {
    val Primary = Color(0xFFE06040)          // 暖橙主色
    val PrimaryDeep = Color(0xFFC84E32)      // 渐变尾
    val PrimaryLight = Color(0xFFF2A58D)     // 渐变中间
    val PrimaryContainer = Color(0xFFFFE3D9)
    val OnPrimaryContainer = Color(0xFF5C1A0B)
    val Blue = Color(0xFF7090E0)             // 图表/辅助
    val Green = Color(0xFF3E9E7B)            // 正向/达标
    val Purple = Color(0xFF402070)           // 深紫
    val Background = Color(0xFFF4F5F7)       // 页底
    val Card = Color(0xFFFFFFFF)             // 白卡（纯白）
    val Border = Color(0xFFE8EAEE)           // 描边
    val TextPrimary = Color(0xFF2B2B2B)
    val TextSecondary = Color(0xFF7A7F87)
}

/** 卡片圆角（区块化） */
val CardRadius = RoundedCornerShape(18.dp)

/** 统一配色调色板：动作数据汇总 / 数据透视 / 肌群占比共用，保证同页风格一致 */
val ChartPalette = listOf(
    Color(0xFFE06040), Color(0xFF7090E0), Color(0xFF3E9E7B), Color(0xFFF2A65A),
    Color(0xFF9B7BC4), Color(0xFF4FB8C9), Color(0xFFE07856), Color(0xFF7E9B6E),
    Color(0xFFC97B8B), Color(0xFF5C8DBC), Color(0xFFD99A4E), Color(0xFF8A9AA8),
)

private val LightColors = lightColorScheme(
    primary = HColors.Primary,
    onPrimary = Color.White,
    primaryContainer = HColors.PrimaryContainer,
    onPrimaryContainer = HColors.OnPrimaryContainer,
    secondary = HColors.Blue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4EAFB),
    onSecondaryContainer = Color(0xFF1E2A4A),
    tertiary = HColors.Green,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDF1E9),
    onTertiaryContainer = Color(0xFF0F3326),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = HColors.Background,
    onBackground = HColors.TextPrimary,
    surface = HColors.Card,
    onSurface = HColors.TextPrimary,
    surfaceVariant = Color(0xFFEAECEF),
    onSurfaceVariant = HColors.TextSecondary,
    outline = Color(0xFFA4A9B0),
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
        drawCircle(color = HColors.Primary.copy(alpha = 0.20f), radius = w * 0.42f, center = Offset(w * 1.12f, -h * 0.06f), style = Stroke(5f))
        drawCircle(color = HColors.Primary.copy(alpha = 0.14f), radius = w * 0.16f, center = Offset(w * 0.10f, -h * 0.02f), style = Stroke(2f))
        drawCircle(color = HColors.Blue.copy(alpha = 0.16f), radius = w * 0.32f, center = Offset(-w * 0.08f, h * 0.30f), style = Stroke(3f))
        drawCircle(color = HColors.Blue.copy(alpha = 0.13f), radius = w * 0.26f, center = Offset(w * 1.06f, h * 0.50f), style = Stroke(3f))
        drawCircle(color = HColors.Primary.copy(alpha = 0.13f), radius = w * 0.20f, center = Offset(w * 0.02f, h * 0.76f), style = Stroke(2.5f))
        // 圆弧（底部右侧半环）
        drawArc(
            color = HColors.Primary.copy(alpha = 0.12f),
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.70f, h * 0.88f),
            size = Size(w * 0.4f, w * 0.4f),
            style = Stroke(width = 2f),
        )
        // 直线（粗中细、上中下）
        drawLine(HColors.Primary.copy(alpha = 0.20f), Offset(-w * 0.02f, h * 0.20f), Offset(w * 0.30f, h * 0.06f), 6f, StrokeCap.Round)
        drawLine(HColors.Blue.copy(alpha = 0.16f), Offset(w * 0.80f, h * 0.05f), Offset(w * 1.06f, h * 0.14f), 3f, StrokeCap.Round)
        drawLine(HColors.Primary.copy(alpha = 0.13f), Offset(w * 0.40f, -h * 0.01f), Offset(w * 0.52f, h * 0.05f), 1.5f)
        drawLine(HColors.Primary.copy(alpha = 0.14f), Offset(w * 0.02f, h * 0.52f), Offset(w * 0.40f, h * 0.44f), 2f)
        drawLine(HColors.Blue.copy(alpha = 0.13f), Offset(w * 0.58f, h * 0.80f), Offset(w * 0.95f, h * 0.88f), 3f, StrokeCap.Round)
    }
}
