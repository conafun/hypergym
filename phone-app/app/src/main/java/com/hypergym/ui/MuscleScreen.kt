package com.hypergym.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypergym.data.StatsEngine
import com.hypergym.data.TrainingDay

private val EX_COLORS = listOf(
    Color(0xFFE06040), Color(0xFF7090E0), Color(0xFF3E9E7B), Color(0xFFF2A65A),
    Color(0xFF9B7BC4), Color(0xFF4FB8C9), Color(0xFFE07856), Color(0xFF7E9B6E),
    Color(0xFFC97B8B), Color(0xFF5C8DBC), Color(0xFFD99A4E), Color(0xFF8A9AA8),
)

private val MUSCLE_COLORS = mapOf(
    "胸" to Color(0xFFE06040),
    "肩" to Color(0xFFF2A65A),
    "背" to Color(0xFF7090E0),
    "腿" to Color(0xFF3E9E7B),
    "臂" to Color(0xFF9B7BC4),
    "核心" to Color(0xFF4FB8C9),
    "其他" to Color(0xFF9AA5B1),
)

/** 肌群页：周/月切换 + 动作数据汇总(分组柱状图) + 肌群占比饼图 + 均衡度建议 */
@Composable
fun MuscleScreen(days: List<TrainingDay>, modifier: Modifier = Modifier) {
    val sorted = remember(days) { days.sortedBy { it.date } }
    val today = remember { DateUtils.today() }
    var range by remember { mutableStateOf("WEEK") } // WEEK / MONTH

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
                GroupedBarChart(daysBars)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    distinctEx.forEach { name ->
                        Row(
                            Modifier
                                .background(HColors.Background, RoundedCornerShape(50))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LegendDot(exColor[name] ?: HColors.Primary)
                            Text(name, fontSize = 10.sp, color = HColors.TextSecondary)
                        }
                    }
                }
            }
        }
        item { MusclePieCard(dist, range) }
        item { AdviceCard(dist, range) }
    }
}

@Composable
private fun MusclePieCard(dist: List<Triple<String, Double, Long>>, range: String) {
    BlockCard {
        CardTitle(if (range == "WEEK") "本周肌群占比" else "本月肌群占比")
        Spacer(Modifier.height(12.dp))
        val total = dist.sumOf { it.second }.coerceAtLeast(1.0)
        val segs = dist.map { DonutSeg(MUSCLE_COLORS[it.first] ?: Color(0xFF9AA5B1), (it.second / total).toFloat()) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(segs, fmtComma(total), if (range == "WEEK") "本周总容量" else "本月总容量")
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                dist.forEach { (name, vol, _) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        LegendDot(MUSCLE_COLORS[name] ?: Color(0xFF9AA5B1))
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

@Composable
private fun AdviceCard(dist: List<Triple<String, Double, Long>>, range: String) {
    val chest = dist.firstOrNull { it.first == "胸" }?.second ?: 0.0
    val back = dist.firstOrNull { it.first == "背" }?.second ?: 0.0
    val period = if (range == "WEEK") "本周" else "本月"
    val text = if (chest > back * 1.5) {
        val ratio = if (back > 0) chest / back else 0.0
        "${period}「胸」(${fmtComma(chest)} kg)明显多于「背」(${fmtComma(back)} kg)，推拉比 ${String.format("%.1f", ratio)}:1，建议下次安排划船或引体向上，平衡推拉。"
    } else {
        "${period}「胸」(${fmtComma(chest)} kg)与「背」(${fmtComma(back)} kg)发展较均衡，保持当前节奏即可。"
    }
    BlockCard {
        CardTitle("均衡度建议")
        Spacer(Modifier.height(10.dp))
        Surface(shape = RoundedCornerShape(14.dp), color = HColors.Background) {
            Text(text, Modifier.padding(12.dp), fontSize = 13.sp, lineHeight = 20.sp, color = HColors.TextPrimary)
        }
    }
}
