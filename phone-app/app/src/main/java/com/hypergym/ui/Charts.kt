package com.hypergym.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 千分位格式化，如 1680 -> "1,680" */
fun fmtComma(v: Double): String {
    val s = Math.round(v).toLong().toString()
    return s.reversed().chunked(3).joinToString(",").reversed()
}

data class BarPoint(val label: String, val value: Double)

/** 柱状图（带柱顶数值 + 横轴标签，横向滚动） */
@Composable
fun VolumeBarChart(points: List<BarPoint>, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()
    val slot = 34.dp
    val chartWidth = maxOf(320.dp, slot * points.size)
    val textMeasurer = rememberTextMeasurer()
    Box(modifier = modifier.fillMaxWidth().horizontalScroll(scrollState)) {
        Canvas(Modifier.width(chartWidth).height(160.dp)) {
            val max = points.maxOfOrNull { it.value } ?: 1.0
            val topPad = 22.dp.toPx()
            val bottomPad = 24.dp.toPx()
            val left = 8.dp.toPx()
            val right = 8.dp.toPx()
            val plotW = size.width - left - right
            val plotH = size.height - topPad - bottomPad
            val slotPx = plotW / points.size
            val bw = minOf(26.dp.toPx(), slotPx * 0.45f)
            points.forEachIndexed { i, p ->
                val cx = left + i * slotPx + slotPx / 2
                val h = (p.value / max).toFloat() * plotH
                val y = topPad + plotH - h
                drawRoundRect(
                    color = HColors.Primary.copy(alpha = 0.45f + 0.55f * (p.value / max).toFloat()),
                    topLeft = Offset(cx - bw / 2, y),
                    size = Size(bw, maxOf(h, if (p.value > 0) 2.dp.toPx() else 0f)),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
                if (p.value > 0) {
                    val t = textMeasurer.measure(
                        AnnotatedString(fmtComma(p.value)),
                        style = TextStyle(fontSize = 8.sp, color = Color(0xFF6B7280)),
                    )
                    drawText(t, topLeft = Offset(cx - t.size.width / 2f, y - t.size.height - 3.dp.toPx()))
                }
            }
            val step = maxOf(1, points.size / 6)
            points.forEachIndexed { i, p ->
                if (i % step == 0 || i == points.size - 1) {
                    val cx = left + i * slotPx + slotPx / 2
                    val t = textMeasurer.measure(
                        AnnotatedString(p.label),
                        style = TextStyle(fontSize = 9.sp, color = HColors.TextSecondary),
                    )
                    drawText(t, topLeft = Offset(cx - t.size.width / 2f, size.height - t.size.height - 4.dp.toPx()))
                }
            }
        }
    }
}

data class ExBar(val name: String, val value: Double, val color: Color)
data class DayBars(val label: String, val bars: List<ExBar>)

/** 分组柱状图：每天一组，同天不同动作不同色，柱宽较细；点击选中某天，由外部显示该天图例 */
@Composable
fun GroupedBarChart(
    days: List<DayBars>,
    selectedLabel: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val barW = 9.dp
    val barGap = 2.dp
    val groupGap = 20.dp
    val barH = 120.dp
    val maxBars = days.maxOfOrNull { it.bars.size } ?: 1
    val groupW = barW * maxBars + barGap * (maxBars - 1) + groupGap
    val maxV = days.flatMap { it.bars }.maxOfOrNull { it.value } ?: 1.0

    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.Start,
    ) {
        days.forEach { day ->
            val sel = day.label == selectedLabel
            Column(
                modifier = Modifier
                    .width(groupW)
                    .clickable { onSelect(day.label) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.height(barH),
                    horizontalArrangement = Arrangement.spacedBy(barGap),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    day.bars.forEach { b ->
                        Box(
                            Modifier
                                .width(barW)
                                .height(((b.value / maxV).toFloat().coerceAtLeast(0.03f) * barH.value).dp)
                                .background(b.color, RoundedCornerShape(3.dp)),
                        )
                    }
                }
                Text(
                    day.label,
                    fontSize = 9.sp,
                    color = if (sel) HColors.Primary else HColors.TextSecondary,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
    }
}

data class DonutSeg(val color: Color, val fraction: Float)

/** 圆环饼图 + 中心文字 */
@Composable
fun DonutChart(
    segs: List<DonutSeg>,
    centerValue: String,
    centerLabel: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(132.dp)) {
            val stroke = 26.dp.toPx()
            val inset = stroke / 2
            var start = -90f
            segs.forEach { seg ->
                val sweep = seg.fraction * 360f
                drawArc(
                    color = seg.color,
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerValue, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = HColors.TextPrimary)
            Text(centerLabel, fontSize = 11.sp, color = HColors.TextSecondary)
        }
    }
}
