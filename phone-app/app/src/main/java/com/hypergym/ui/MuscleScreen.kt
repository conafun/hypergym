package com.hypergym.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypergym.data.ExerciseLibrary
import com.hypergym.data.MuscleMap
import com.hypergym.data.StatsEngine
import com.hypergym.data.TrainingDay

private val EX_COLORS = ChartPalette

/** 肌群占比颜色：使用 MuscleMap 的高饱和、强对比色板，保证饼图/图例一致且鲜明 */
private fun muscleColor(name: String): Color = Color(MuscleMap.colorOf(name))

/** 肌群页：周/月切换 + 动作数据汇总(分组柱状图) + 肌群占比饼图 + 均衡度建议 */
@Composable
fun MuscleScreen(days: List<TrainingDay>, modifier: Modifier = Modifier) {
    // 用动作库初始化肌群分类（各动作所训练的部位），一次即可
    val context = LocalContext.current
    val library = remember { ExerciseLibrary.load(context) }
    remember(library) { MuscleMap.init(library) }

    val sorted = remember(days) { days.sortedBy { it.date } }
    val today = remember { DateUtils.today() }
    var range by remember { mutableStateOf("WEEK") } // WEEK / MONTH
    var selectedDay by remember(range) { mutableStateOf<String?>(null) }

    val start = remember(range, today) { DateUtils.offset(today, if (range == "WEEK") -6 else -29) }
    val filtered = remember(sorted, start) { sorted.filter { it.date >= start } }

    val distinctEx = remember(filtered) {
        val list = mutableListOf<String>()
        filtered.forEach { d -> d.records.forEach { r -> if (r.exercise !in list) list.add(r.exercise) } }
        list
    }
    val exColor = remember(distinctEx) { distinctEx.mapIndexed { i, n -> n to EX_COLORS[i % EX_COLORS.size] }.toMap() }
    val daysBars = remember(filtered, exColor) {
        filtered.map { day ->
            DayBars(
                label = DateUtils.mdLabel(day.date),
                bars = day.records.map { ex ->
                    ExBar(ex.exercise, ex.sets.sumOf { it.volume }, exColor[ex.exercise] ?: HColors.Primary)
                },
            )
        }
    }
    val dist = remember(filtered) { StatsEngine.muscleDistribution(filtered) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { PageHeader("肌群分布", "各部位训练容量占比") }
        item {
            RangePills(listOf("WEEK" to "周", "MONTH" to "月"), range) { range = it }
        }
        item {
            BlockCard {
                CardTitle("动作数据汇总")
                Spacer(Modifier.height(10.dp))
                // 柱状图：点击某天柱状图 → 下方按当天数据显示图例（可换行，两行内完全展示）
                GroupedBarChart(
                    days = daysBars,
                    selectedLabel = selectedDay,
                    onSelect = { day -> selectedDay = if (selectedDay == day) null else day },
                )
                val selBars = daysBars.firstOrNull { it.label == selectedDay }?.bars
                if (selBars != null) {
                    Spacer(Modifier.height(12.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        selBars.forEach { b ->
                            Row(
                                Modifier
                                    .background(HColors.Background, RoundedCornerShape(50))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                LegendDot(b.color)
                                Text(b.name, fontSize = 11.sp, color = HColors.TextPrimary)
                                Text(
                                    "${fmtComma(b.value)} kg",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HColors.TextSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }
        item { PivotCard(filtered) }
        item { MusclePieCard(dist, range) }
    }
}

@Composable
private fun MusclePieCard(dist: List<Triple<String, Double, Long>>, range: String) {
    BlockCard {
        CardTitle(if (range == "WEEK") "本周肌群占比" else "本月肌群占比")
        Spacer(Modifier.height(12.dp))
        val total = dist.sumOf { it.second }.coerceAtLeast(1.0)
        val segs = dist.map { DonutSeg(muscleColor(it.first), (it.second / total).toFloat()) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(segs, fmtComma(total), if (range == "WEEK") "本周总容量" else "本月总容量")
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                dist.forEach { (name, vol, _) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        LegendDot(muscleColor(name))
                        Text(name, Modifier.weight(1f), fontSize = 12.sp, color = HColors.TextPrimary)
                        Text(
                            "${(vol / total * 100).let { String.format("%.1f", it) }}%",
                            fontSize = 11.sp,
                            color = HColors.TextSecondary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text("${fmtComma(vol)} kg", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = HColors.TextPrimary)
                    }
                }
            }
        }
    }
}


