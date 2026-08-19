package com.hypergym.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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

/** 分组柱状图：每天一组，同天不同动作不同色，柱高 = 容量，柱顶数值 */
@Composable
fun GroupedBarChart(days: List<DayBars>, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()
    val barW = 18.dp
    val barGap = 3.dp
    val groupGap = 26.dp
    val maxBars = days.maxOfOrNull { it.bars.size } ?: 1
    val groupW = barW * maxBars + barGap * (maxBars - 1) + groupGap
    val chartWidth = maxOf(320.dp, groupW * days.size)
    val textMeasurer = rememberTextMeasurer()
    Box(modifier = modifier.fillMaxWidth().horizontalScroll(scrollState)) {
        Canvas(Modifier.width(chartWidth).height(170.dp)) {
            val maxV = days.flatMap { it.bars }.maxOfOrNull { it.value } ?: 1.0
            val topPad = 22.dp.toPx()
            val bottomPad = 24.dp.toPx()
            val plotH = size.height - topPad - bottomPad
            val left = 8.dp.toPx()
            val gwp = groupW.toPx()
            val bwp = barW.toPx()
            val bgp = barGap.toPx()
            val ggp = groupGap.toPx()
            days.forEachIndexed { gi, day ->
                val gx = left + gi * gwp
                val n = day.bars.size
                val totalArea = n * bwp + (n - 1) * bgp
                val startX = gx + (gwp - ggp - totalArea) / 2
                day.bars.forEachIndexed { bi, b ->
                    val x = startX + bi * (bwp + bgp)
                    val h = (b.value / maxV).toFloat() * plotH
                    val y = topPad + plotH - h
                    drawRoundRect(
                        color = b.color,
                        topLeft = Offset(x, y),
                        size = Size(bwp, maxOf(h, 1.dp.toPx())),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                    )
                    val t = textMeasurer.measure(
                        AnnotatedString(Math.round(b.value).toString()),
                        style = TextStyle(fontSize = 7.sp, color = Color(0xFF6B7280)),
                    )
                    drawText(t, topLeft = Offset(x + bwp / 2 - t.size.width / 2, y - t.size.height - 3.dp.toPx()))
                }
                val lt = textMeasurer.measure(
                    AnnotatedString(day.label),
                    style = TextStyle(fontSize = 9.sp, color = HColors.TextSecondary),
                )
                drawText(lt, topLeft = Offset(gx + (gwp - ggp) / 2 - lt.size.width / 2, size.height - lt.size.height - 4.dp.toPx()))
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
