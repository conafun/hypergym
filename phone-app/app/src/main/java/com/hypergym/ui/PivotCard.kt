package com.hypergym.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

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
    // 图例筛选：null=全部显示；非空=仅显示选中序列
    var selectedSeries by remember { mutableStateOf<String?>(null) }

    val data = remember(days, dim, metric, agg, mode, detail) {
        buildPivot(days, dim, metric, agg, mode, detail)
    }
    // 当前生效的选中项（当数据维度变化导致该序列不存在时自动回落为全部显示）
    val selName = selectedSeries?.takeIf { s -> data.series.any { it.name == s } }
    // 图上点选的槽位（null=未选中）；数据一变就清空，避免指向已不存在的点
    var selectedIndex by remember(data) { mutableStateOf<Int?>(null) }
    // 图表与读数区共用同一份可见序列，保证口径一致
    val visibleSeries = if (selName != null) data.series.filter { it.name == selName } else data.series

    BlockCard(modifier) {
        CardTitle("数据透视")
        Spacer(Modifier.height(2.dp))
        Text("X轴：$dim ｜ Y轴：$metric · $agg", fontSize = 11.sp, color = HColors.TextSecondary)

        PivotChart(
            data = data,
            visible = visibleSeries,
            chartType = chartType,
            selectedIndex = selectedIndex,
            onSelectIndex = { i -> selectedIndex = if (selectedIndex == i) null else i },
            modifier = Modifier.padding(top = 8.dp),
        )
        val picked = selectedIndex?.takeIf { it in data.labels.indices }
        if (picked != null) {
            Spacer(Modifier.height(10.dp))
            PivotReadout(data, visibleSeries, picked)
        } else if (data.labels.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("点击图表可查看某一点的具体数值", fontSize = 9.sp, color = HColors.TextSecondary)
        }

        // 多系列图例（可点选筛选：点某项只显示该项，再点恢复全部）
        if (data.series.size > 1) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    if (selName == null) "点击图例可筛选单项" else "已筛选：$selName",
                    fontSize = 9.sp, color = HColors.TextSecondary,
                )
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                data.series.forEach { s ->
                    val isSelected = selName == s.name
                    val dimmed = selName != null && !isSelected
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).clickable { selectedSeries = if (isSelected) null else s.name }.padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        LegendDot(if (dimmed) s.color.copy(alpha = 0.4f) else s.color)
                        Text(s.name, fontSize = 11.sp, color = if (isSelected) HColors.Primary else if (dimmed) HColors.TextSecondary.copy(alpha = 0.5f) else HColors.TextSecondary, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
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
private fun PivotChart(
    data: PivotData,
    visible: List<PivotSeries>,
    chartType: String,
    selectedIndex: Int?,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(550, easing = LinearOutSlowInEasing))
    }
    val scroll = rememberScrollState()
    // 与「动作数据汇总」保持一致：数据变化后默认滚到最右端，优先展示最近的日期
    LaunchedEffect(data) {
        snapshotFlow { scroll.maxValue }.filter { it > 0 }.first()
        scroll.scrollTo(scroll.maxValue)
    }
    val n = data.labels.size
    val slotW = 30.dp
    val chartWidth = maxOf(320.dp, slotW * n.coerceAtLeast(1))
    val leftPadDp = 38.dp
    val rightPadDp = 8.dp

    Box(modifier.fillMaxWidth().horizontalScroll(scroll)) {
        Canvas(
            Modifier
                .width(chartWidth)
                .height(180.dp)
                // 点一下某个柱/点，读出该位置各序列的数值
                .pointerInput(data, visible, chartType) {
                    detectTapGestures { off ->
                        val lp = leftPadDp.toPx()
                        val rp = rightPadDp.toPx()
                        val w = size.width - lp - rp
                        if (n > 0 && w > 0f) {
                            onSelectIndex(((off.x - lp) / (w / n)).toInt().coerceIn(0, n - 1))
                        }
                    }
                },
        ) {
            if (n == 0 || visible.isEmpty() || visible.all { it.values.isEmpty() }) return@Canvas
            val maxV = (visible.flatMap { it.values }.maxOrNull() ?: 0.0).let { if (it <= 0.0) 1.0 else it }
            val leftPad = leftPadDp.toPx()
            val rightPad = rightPadDp.toPx()
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
                drawLine(Color(0xFFEFEFF2), Offset(gx, y), Offset(size.width - rightPad, y), 1f)
                val t = textMeasurer.measure(AnnotatedString(fmtComma(v)), TextStyle(fontSize = 8.sp, color = Color(0xFF9AA0AA)))
                drawText(t, topLeft = Offset(gx - t.size.width - 5.dp.toPx(), y - t.size.height / 2))
            }

            // 横轴只标首尾两个：数据一多，逐点标注会全部挤在一起
            fun drawAxisLabel(i: Int) {
                if (i != 0 && i != n - 1) return
                val lt = textMeasurer.measure(AnnotatedString(data.labels[i]), TextStyle(fontSize = 8.sp, color = Color(0xFF9AA0AA)))
                drawText(lt, topLeft = Offset(cx(i) - lt.size.width / 2, size.height - lt.size.height - 2.dp.toPx()))
            }

            if (chartType == "柱状") {
                data.labels.forEachIndexed { i, _ ->
                    val c = cx(i)
                    val series = visible
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
                    drawAxisLabel(i)
                }
            } else {
                val totalSlots = (n - 1).coerceAtLeast(1)
                val reveal = step * totalSlots
                visible.forEach { s ->
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
                data.labels.forEachIndexed { i, _ -> drawAxisLabel(i) }
            }

            // 选中点：竖向参考线 + 各序列在该点的圆点，画在最上层以免被柱体遮住
            if (selectedIndex != null && selectedIndex in 0 until n) {
                val c = cx(selectedIndex)
                drawLine(HColors.Primary.copy(alpha = 0.30f), Offset(c, topPad), Offset(c, baseY), 1.5.dp.toPx())
                visible.forEach { s ->
                    val p = Offset(c, yOf(selectedIndex, s.values.getOrNull(selectedIndex) ?: 0.0))
                    drawCircle(s.color, 4.5.dp.toPx(), p)
                    drawCircle(Color.White, 1.8.dp.toPx(), p)
                }
            }
        }
    }
}

/** 点选后的读数：该点的标签 + 各序列数值（与图表共用同一份可见序列） */
@Composable
private fun PivotReadout(data: PivotData, visible: List<PivotSeries>, index: Int) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            data.labels.getOrNull(index) ?: "",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = HColors.TextPrimary,
        )
        Spacer(Modifier.height(4.dp))
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            visible.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    LegendDot(s.color)
                    if (s.name.isNotEmpty()) {
                        Text(s.name, fontSize = 11.sp, color = HColors.TextSecondary)
                    }
                    Text(
                        fmtComma(s.values.getOrNull(index) ?: 0.0),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = HColors.TextPrimary,
                    )
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
                    Text(lab, fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, color = if (sel) Color.White else HColors.TextSecondary)
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
            // 平均重量只统计有配重的组（自重 weight=0 不计入重量类汇总）
            "平均重量" -> {
                val weighted = records.filter { it.weight > 0.0 }
                val ws = weighted.sumOf { it.sets.size }
                if (ws > 0) weighted.sumOf { ex -> ex.weight * ex.sets.size } / ws else 0.0
            }
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
