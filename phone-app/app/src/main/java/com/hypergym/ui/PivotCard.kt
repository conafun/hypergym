package com.hypergym.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypergym.data.ExerciseRecord
import com.hypergym.data.MuscleMap
import com.hypergym.data.StatsEngine
import com.hypergym.data.TrainingDay
import java.util.Calendar

private val DIM_OPTIONS = listOf("日期" to "日期", "周" to "周", "肌群" to "肌群", "动作" to "动作")
private val METRIC_OPTIONS = listOf(
    "总容量" to "总容量(kg)", "总组数" to "总组数", "总次数" to "总次数",
    "平均重量" to "平均重量(kg)", "平均次数" to "平均次数",
)
private val DETAIL_OPTIONS = listOf("动作" to "动作", "肌群" to "肌群")

private data class PivotSeries(val name: String, val color: Color, val values: List<Double>)
private data class PivotData(val labels: List<String>, val series: List<PivotSeries>)

/** 数据透视卡片：X轴维度 × Y轴指标 × 总和/平均 × 单项/多项 × 柱状/折线，均可自定义，柱状/折线带微动画 */
@Composable
fun PivotCard(days: List<TrainingDay>, modifier: Modifier = Modifier) {
    var dim by remember { mutableStateOf("日期") }
    var metric by remember { mutableStateOf("总容量") }
    var agg by remember { mutableStateOf("总和") }
    var mode by remember { mutableStateOf("单项") }
    var detail by remember { mutableStateOf("动作") }
    var chartType by remember { mutableStateOf("柱状") }

    val data = remember(days, dim, metric, agg, mode, detail) {
        buildPivot(days, dim, metric, agg, mode, detail)
    }

    BlockCard(modifier) {
        CardTitle("数据透视")
        Spacer(Modifier.height(2.dp))
        Text("X轴：$dim ｜ Y轴：$metric · $agg", fontSize = 11.sp, color = HColors.TextSecondary)

        PivotChart(data, chartType, Modifier.padding(top = 8.dp))

        // 多系列图例
        if (data.series.size > 1) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                data.series.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        LegendDot(s.color)
                        Text(s.name, fontSize = 11.sp, color = HColors.TextSecondary)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 控制区：X/Y 下拉 + 聚合/模式/图表
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PivotDropdown("X轴维度", dim, DIM_OPTIONS, { dim = it }, Modifier.weight(1f))
            PivotDropdown("Y轴指标", metric, METRIC_OPTIONS, { metric = it }, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PillGroup("聚合方式", listOf("总和" to "总和", "平均" to "平均"), agg, { agg = it }, Modifier.weight(1f))
            PillGroup("单项/多项", listOf("单项" to "单项", "多项" to "多项"), mode, { mode = it }, Modifier.weight(1f))
        }
        if (mode == "多项") {
            PivotDropdown("明细维度", detail, DETAIL_OPTIONS, { detail = it }, Modifier.fillMaxWidth().padding(top = 10.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PillGroup("图表类型", listOf("柱状" to "柱状", "折线" to "折线"), chartType, { chartType = it }, Modifier.weight(1f))
        }
    }
}

// ---------------- 图表 ----------------

@Composable
private fun PivotChart(data: PivotData, chartType: String, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(550, easing = LinearOutSlowInEasing))
    }
    val scroll = rememberScrollState()
    val n = data.labels.size
    val slotW = 30.dp
    val chartWidth = maxOf(320.dp, slotW * n.coerceAtLeast(1))

    Box(modifier.fillMaxWidth().horizontalScroll(scroll)) {
        Canvas(Modifier.width(chartWidth).height(180.dp)) {
            if (n == 0 || data.series.isEmpty() || data.series.all { it.values.isEmpty() }) return@Canvas
            val maxV = (data.series.flatMap { it.values }.maxOrNull() ?: 0.0).let { if (it <= 0.0) 1.0 else it }
            val leftPad = 38.dp.toPx()
            val rightPad = 8.dp.toPx()
            val topPad = 12.dp.toPx()
            val bottomPad = 24.dp.toPx()
            val plotW = size.width - leftPad - rightPad
            val plotH = size.height - topPad - bottomPad
            val gx = leftPad
            val step = progress.value
            val baseY = topPad + plotH
            val slot = plotW / n
            fun cx(i: Int) = gx + slot * i + slot / 2
            fun yOf(i: Int, v: Double) = baseY - (v / maxV).toFloat() * plotH

            // 横向网格线 + 左侧刻度
            for (g in 0..4) {
                val v = maxV * (4 - g) / 4
                val y = topPad + plotH * g / 4
                drawLine(Color(0xFFE8E1D4), Offset(gx, y), Offset(size.width - rightPad, y), 1f)
                val t = textMeasurer.measure(AnnotatedString(fmtComma(v)), TextStyle(fontSize = 8.sp, color = Color(0xFF000000)))
                drawText(t, topLeft = Offset(gx - t.size.width - 5.dp.toPx(), y - t.size.height / 2))
            }

            if (chartType == "柱状") {
                data.labels.forEachIndexed { i, _ ->
                    val c = cx(i)
                    val series = data.series
                    if (series.size == 1) {
                        val v = series[0].values[i]
                        val h = (v / maxV).toFloat() * plotH * step
                        val bw = slot * 0.44f
                        drawRoundRect(series[0].color, Offset(c - bw / 2, baseY - h), Size(bw, h.coerceAtLeast(1.dp.toPx())), CornerRadius(3.dp.toPx()))
                    } else {
                        val inner = slot * 0.82f
                        val bw = inner / series.size
                        series.forEachIndexed { si, s ->
                            val v = s.values[i]
                            val h = (v / maxV).toFloat() * plotH * step
                            val x = c - inner / 2 + bw * si
                            drawRoundRect(s.color, Offset(x + bw * 0.1f, baseY - h), Size(bw * 0.8f, h.coerceAtLeast(1.dp.toPx())), CornerRadius(3.dp.toPx()))
                        }
                    }
                    val lt = textMeasurer.measure(AnnotatedString(data.labels[i]), TextStyle(fontSize = 8.sp, color = Color(0xFF000000)))
                    drawText(lt, topLeft = Offset(c - lt.size.width / 2, size.height - lt.size.height - 2.dp.toPx()))
                }
            } else {
                val totalSlots = (n - 1).coerceAtLeast(1)
                val reveal = step * totalSlots
                data.series.forEach { s ->
                    var prev: Offset? = null
                    data.labels.forEachIndexed { i, _ ->
                        val cur = Offset(cx(i), yOf(i, s.values[i]))
                        prev?.let { p ->
                            val segIdx = i - 1
                            if (segIdx <= reveal) {
                                drawLine(s.color, p, cur, 2.2.dp.toPx(), StrokeCap.Round)
                            } else if (segIdx - 1 <= reveal) {
                                val t = (reveal - (segIdx - 1)).coerceIn(0f, 1f)
                                drawLine(s.color, p, Offset(p.x + (cur.x - p.x) * t, p.y + (cur.y - p.y) * t), 2.2.dp.toPx(), StrokeCap.Round)
                            }
                        }
                        drawCircle(s.color, 3.dp.toPx(), cur)
                        prev = cur
                    }
                }
                data.labels.forEachIndexed { i, _ ->
                    val c = cx(i)
                    val lt = textMeasurer.measure(AnnotatedString(data.labels[i]), TextStyle(fontSize = 8.sp, color = Color(0xFF000000)))
                    drawText(lt, topLeft = Offset(c - lt.size.width / 2, size.height - lt.size.height - 2.dp.toPx()))
                }
            }
        }
    }
}

// ---------------- 控件 ----------------

@Composable
private fun PivotDropdown(label: String, selected: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, fontSize = 10.sp, color = HColors.TextSecondary)
        Box(Modifier.padding(top = 4.dp)) {
            Surface(shape = RoundedCornerShape(10.dp), color = HColors.Background, modifier = Modifier.fillMaxWidth().clickable { open = true }) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(selected, fontSize = 13.sp, color = HColors.TextPrimary, modifier = Modifier.weight(1f))
                    Text("▾", fontSize = 12.sp, color = HColors.TextSecondary)
                }
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (key, lab) ->
                    DropdownMenuItem(
                        text = { Text(if (lab != key) lab else key, fontSize = 13.sp, color = if (key == selected) HColors.Primary else HColors.TextPrimary) },
                        onClick = { onSelect(key); open = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun PillGroup(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontSize = 10.sp, color = HColors.TextSecondary)
        Row(
            Modifier.padding(top = 4.dp).clip(RoundedCornerShape(50)).background(HColors.Background).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            options.forEach { (key, lab) ->
                val sel = key == selected
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(if (sel) HColors.Primary else Color.Transparent).clickable { onSelect(key) }.padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(lab, fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, color = Color(0xFF000000))
                }
            }
        }
    }
}

// ---------------- 数据计算 ----------------

private data class Rec(val day: TrainingDay, val ex: ExerciseRecord)

private fun mondayDate(date: String): String {
    val c = StatsEngine.parseDate(date) ?: return date
    c.add(Calendar.DATE, -((c.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7))
    return StatsEngine.formatDate(c)
}

private fun buildPivot(days: List<TrainingDay>, dim: String, metric: String, agg: String, mode: String, detail: String): PivotData {
    val recs = days.flatMap { d -> d.records.map { Rec(d, it) } }
    if (recs.isEmpty()) return PivotData(emptyList(), emptyList())

    fun bucketKey(r: Rec): String = when (dim) {
        "日期" -> r.day.date
        "周" -> mondayDate(r.day.date)
        "肌群" -> MuscleMap.groupOf(r.ex.exercise)
        else -> r.ex.exercise
    }
    fun subKey(r: Rec): String = if (detail == "动作") r.ex.exercise else MuscleMap.groupOf(r.ex.exercise)

    val seriesNames = if (mode == "单项") listOf("") else recs.map { subKey(it) }.distinct()
    val bucketKeys = recs.map { bucketKey(it) }.distinct()

    fun metricOf(records: List<ExerciseRecord>): Double {
        val totalSets = records.sumOf { it.sets.size }
        return when (metric) {
            "总组数" -> totalSets.toDouble()
            "总次数" -> records.sumOf { ex -> ex.sets.sumOf { it.reps } }.toDouble()
            "平均重量" -> if (totalSets > 0) records.sumOf { ex -> ex.weight * ex.sets.size } / totalSets else 0.0
            "平均次数" -> {
                val r = records.sumOf { ex -> ex.sets.sumOf { it.reps } }
                if (totalSets > 0) r.toDouble() / totalSets else 0.0
            }
            else -> records.sumOf { ex -> ex.sets.sumOf { it.volume } }
        }
    }

    fun valueOf(bucket: String, series: String): Double {
        val grp = recs.filter { bucketKey(it) == bucket && (mode == "单项" || subKey(it) == series) }
        if (grp.isEmpty()) return 0.0
        val perDay = grp.groupBy { it.day.date }.values.map { metricOf(it.map { r -> r.ex }) }
        return if (agg == "平均") perDay.average() else perDay.sum()
    }

    val ordered: List<String> = if (dim == "日期" || dim == "周") bucketKeys.sorted()
    else bucketKeys.sortedByDescending { valueOf(it, seriesNames.firstOrNull() ?: "") }

    val series = seriesNames.mapIndexed { si, s ->
        val color = if (mode == "单项") HColors.Primary else ChartPalette[si % ChartPalette.size]
        PivotSeries(s, color, ordered.map { valueOf(it, s) })
    }
    val labels = ordered.map { b ->
        when (dim) {
            "日期" -> DateUtils.mdLabel(b)
            "周" -> DateUtils.mdLabel(b) + "周"
            else -> b
        }
    }
    return PivotData(labels, series)
}
