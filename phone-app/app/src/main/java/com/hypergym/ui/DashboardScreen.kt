package com.hypergym.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypergym.data.ExerciseRecord
import com.hypergym.data.StatsEngine
import com.hypergym.data.TrainingDay
import java.util.Calendar

private val RestGray = Color(0xFFE8E1D4)

private fun shiftMonth(key: String, delta: Int): String {
    val y = key.substring(0, 4).toInt()
    val m = key.substring(5, 7).toInt()
    val total = y * 12 + (m - 1) + delta
    val ny = total / 12
    val nm = total % 12 + 1
    return "%04d-%02d".format(ny, nm)
}

private fun monthStats(days: List<TrainingDay>, monthKey: String, today: String): StatsEngine.PeriodStats {
    val start = "$monthKey-01"
    val filtered = days.filter { it.date >= start && it.date <= today }
    val vol = filtered.sumOf { it.totalVolume() }
    return StatsEngine.PeriodStats(
        sessions = filtered.size,
        totalVolume = vol,
        totalSets = filtered.sumOf { it.totalSets() },
        avgVolume = if (filtered.isEmpty()) 0.0 else vol / filtered.size,
    )
}

private fun monthDelta(days: List<TrainingDay>, monthKey: String, today: String): Double? {
    val curStart = "$monthKey-01"
    val prevStart = "${shiftMonth(monthKey, -1)}-01"
    val cur = days.filter { it.date >= curStart && it.date <= today }.sumOf { it.totalVolume() }
    val prev = days.filter { it.date >= prevStart && it.date < curStart }.sumOf { it.totalVolume() }
    return if (prev > 0) (cur - prev) / prev else null
}

private fun monthTrendPoints(days: List<TrainingDay>, monthKey: String, today: String): List<BarPoint> {
    val volByDate = days.associate { it.date to it.totalVolume() }
    val firstCal = StatsEngine.parseDate("$monthKey-01") ?: return emptyList()
    val totalDays = firstCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val endDay = if (monthKey == today.substring(0, 7)) today.substring(8).toInt() else totalDays
    return (endDay downTo 1).map { d ->
        val date = "$monthKey-${d.toString().padStart(2, '0')}"
        BarPoint(DateUtils.mdLabel(date), volByDate[date] ?: 0.0)
    }
}

/** 数据页：热力图(可展开/点选) + 当天内容 + 周/月/全部总量 + 次数/组数 + 容量趋势柱状图 */
@Composable
fun DashboardScreen(days: List<TrainingDay>, modifier: Modifier = Modifier) {
    val sorted = remember(days) { days.sortedBy { it.date } }
    val today = remember { DateUtils.today() }
    val volByDate = remember(sorted) { sorted.associate { it.date to it.totalVolume() } }
    val dayMap = remember(sorted) { sorted.associateBy { it.date } }
    val maxVol = remember(sorted) { sorted.maxOfOrNull { it.totalVolume() } ?: 1.0 }
    var range by remember { mutableStateOf(StatsEngine.Range.WEEK) }
    var selected by remember(sorted) { mutableStateOf(sorted.lastOrNull()?.date ?: today) }
    var expanded by remember { mutableStateOf(false) }
    var displayedMonth by remember(today) { mutableStateOf(today.substring(0, 7)) }
    var selectedMonth by remember(today) { mutableStateOf(today.substring(0, 7)) }
    var monthPickerOpen by remember { mutableStateOf(false) }
    val allMonths = remember(sorted, today) {
        (sorted.map { it.date.substring(0, 7) }.toSet() + today.substring(0, 7)).sortedDescending()
    }
    val stats = remember(sorted, range, selectedMonth, today) {
        when (range) {
            StatsEngine.Range.MONTH -> monthStats(sorted, selectedMonth, today)
            else -> StatsEngine.periodStats(sorted, range, today)
        }
    }
    val delta = remember(sorted, range, selectedMonth, today) {
        when (range) {
            StatsEngine.Range.MONTH -> monthDelta(sorted, selectedMonth, today)
            else -> StatsEngine.deltaPercent(sorted, range, today)
        }
    }
    val points = remember(sorted, range, selectedMonth, today) {
        when (range) {
            StatsEngine.Range.MONTH -> monthTrendPoints(sorted, selectedMonth, today)
            else -> trendPoints(sorted, range, today)
        }
    }
    val selectMonth: (String) -> Unit = { m ->
        selectedMonth = m
        range = StatsEngine.Range.MONTH
        displayedMonth = m
        expanded = true
        monthPickerOpen = false
        selected = sorted.filter { it.date.startsWith(m) }.lastOrNull()?.date ?: "$m-01"
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            PageHeader(
                title = "训练数据",
                subtitle = "共 ${sorted.size} 天训练记录",
                trailing = {
                    Surface(
                        shape = CircleShape,
                        color = HColors.Card,
                        shadowElevation = 1.dp,
                        onClick = { monthPickerOpen = !monthPickerOpen },
                    ) {
                        Text(
                            "${selectedMonth.substring(5, 7).toInt()}月",
                            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            color = HColors.Primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
            )
        }
        if (monthPickerOpen) {
            item { MonthPickerRow(allMonths, selectedMonth, selectMonth) }
        }
        item {
            HeatmapCard(
                volByDate = volByDate,
                maxVol = maxVol,
                today = today,
                selected = selected,
                expanded = expanded,
                displayedMonth = displayedMonth,
                onMonthShift = { delta -> displayedMonth = shiftMonth(displayedMonth, delta) },
                onToggle = { expanded = !expanded },
                onSelect = { selected = it },
            )
        }
        item { DayContentCard(day = dayMap[selected], date = selected, today = today) }
        item {
            RangePills(
                options = listOf("WEEK" to "周", "MONTH" to "月", "ALL" to "全部"),
                selected = range.name,
                onSelect = { range = StatsEngine.Range.valueOf(it) },
            )
        }
        item { HeroCard(stats = stats, delta = delta, range = range, selectedMonth = selectedMonth) }
        item { StatGrid(stats = stats) }
        item { TrendCard(points = points, total = stats.totalVolume) }
    }
}

// ---------------- 热力图 ----------------

@Composable
private fun HeatmapCard(
    volByDate: Map<String, Double>,
    maxVol: Double,
    today: String,
    selected: String,
    expanded: Boolean,
    displayedMonth: String,
    onMonthShift: (Int) -> Unit,
    onToggle: () -> Unit,
    onSelect: (String) -> Unit,
) {
    BlockCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            CardTitle("训练热力图")
            Text(
                if (expanded) "收起 ▴" else "展开整月 ▾",
                Modifier
                    .clip(CircleShape)
                    .background(HColors.Background)
                    .clickable { onToggle() }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                color = HColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(14.dp))
        if (expanded) {
            MonthNav(displayedMonth, onMonthShift)
            Spacer(Modifier.height(8.dp))
            MonthGrid(volByDate, maxVol, displayedMonth, today, selected, onSelect)
        } else {
            WeekStrip(volByDate, maxVol, today, selected, onSelect)
        }
    }
}

@Composable
private fun MonthNav(monthKey: String, onShift: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(
            "‹",
            Modifier.clip(CircleShape).clickable { onShift(-1) }.padding(horizontal = 14.dp, vertical = 2.dp),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = HColors.Primary,
        )
        Text(
            "${monthKey.substring(0, 4)}年${monthKey.substring(5, 7).toInt()}月",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = HColors.TextPrimary,
        )
        Text(
            "›",
            Modifier.clip(CircleShape).clickable { onShift(1) }.padding(horizontal = 14.dp, vertical = 2.dp),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = HColors.Primary,
        )
    }
}

@Composable
private fun WeekStrip(
    volByDate: Map<String, Double>,
    maxVol: Double,
    today: String,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (6 downTo 0).forEach { i ->
            val d = DateUtils.offset(today, -i)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                HeatCell(
                    volume = volByDate[d] ?: 0.0,
                    maxVol = maxVol,
                    trained = volByDate.containsKey(d),
                    label = DateUtils.dayLabel(d),
                    isToday = d == today,
                    isSelected = d == selected,
                    onClick = { onSelect(d) },
                )
                Text(
                    DateUtils.weekdayLabel(d).removePrefix("周"),
                    fontSize = 10.sp,
                    color = HColors.TextSecondary,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun MonthGrid(
    volByDate: Map<String, Double>,
    maxVol: Double,
    monthKey: String,
    today: String,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val cells = remember(monthKey) {
        val first = StatsEngine.parseDate("$monthKey-01") ?: return@remember emptyList<Pair<String, String?>>()
        val firstMonIdx = DateUtils.weekdayIndex(StatsEngine.formatDate(first))
        val daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH)
        val list = mutableListOf<Pair<String, String?>>()
        repeat(firstMonIdx) { list.add("" to null) }
        for (d in 1..daysInMonth) {
            val date = "$monthKey-${d.toString().padStart(2, '0')}"
            list.add(d.toString() to date)
        }
        while (list.size % 7 != 0) list.add("" to null)
        list
    }
    val isCurrentMonth = monthKey == today.substring(0, 7)
    Column {
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { w ->
                Text(w, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 10.sp, color = HColors.TextSecondary)
            }
        }
        Spacer(Modifier.height(6.dp))
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { (label, date) ->
                    Box(Modifier.weight(1f)) {
                        val isFuture = date != null && date > today
                        when {
                            label.isEmpty() || date == null -> Spacer(Modifier.aspectRatio(1f))
                            isFuture -> Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(11.dp)).background(RestGray.copy(alpha = 0.4f)))
                            else -> HeatCell(
                                volume = volByDate[date] ?: 0.0,
                                maxVol = maxVol,
                                trained = volByDate.containsKey(date),
                                label = label,
                                isToday = isCurrentMonth && date == today,
                                isSelected = date == selected,
                                onClick = { onSelect(date) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun HeatCell(
    volume: Double,
    maxVol: Double,
    trained: Boolean,
    label: String,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (trained) {
        val alpha = 0.35f + 0.65f * (volume / maxVol).toFloat().coerceIn(0f, 1f)
        HColors.Primary.copy(alpha = alpha)
    } else {
        RestGray
    }
    val ring = when {
        isSelected -> Color.White
        isToday -> HColors.Primary
        else -> null
    }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(11.dp))
            .background(bg)
            .then(if (ring != null) Modifier.border(2.dp, ring, RoundedCornerShape(11.dp)) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (trained) HColors.TextPrimary else HColors.TextSecondary)
    }
}

// ---------------- 当天训练内容 ----------------

@Composable
private fun DayContentCard(day: TrainingDay?, date: String, today: String) {
    BlockCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            CardTitle(if (date == today) "今天 · ${DateUtils.weekdayLabel(date)}" else "$date · ${DateUtils.weekdayLabel(date)}")
            Text(
                if (day != null) "${fmtComma(day.totalVolume())} kg" else "—",
                color = HColors.Primary,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        if (day == null || day.records.isEmpty()) {
            Text("无训练记录", color = HColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 20.dp))
        } else {
            day.records.forEachIndexed { i, ex ->
                if (i > 0) HorizontalDivider(color = HColors.Border)
                ExerciseRow(ex)
            }
        }
    }
}

@Composable
fun ExerciseRow(ex: ExerciseRecord) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                ex.exercise,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = HColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text("${ex.weight}kg × ${ex.sets.size}组", fontSize = 12.sp, color = HColors.TextSecondary)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                ex.sets.forEach { s -> SetChip("${s.reps}次") }
                Text(
                    "容量 ${fmtComma(ex.sets.sumOf { it.volume })} kg",
                    fontSize = 11.sp,
                    color = HColors.Primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
fun SetChip(text: String) {
    Text(
        text,
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(HColors.PrimaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = HColors.TextPrimary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

// ---------------- 英雄卡 / 统计 / 趋势 ----------------

@Composable
private fun HeroCard(stats: StatsEngine.PeriodStats, delta: Double?, range: StatsEngine.Range, selectedMonth: String) {
    val title = when (range) {
        StatsEngine.Range.WEEK -> "本周总容量"
        StatsEngine.Range.MONTH -> "${selectedMonth.substring(5, 7).toInt()}月总容量"
        StatsEngine.Range.ALL -> "累计总容量"
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(HColors.Primary, HColors.PrimaryLight, HColors.PrimaryDeep)))
            .padding(20.dp),
    ) {
        Column {
            Text(title, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(fmtComma(stats.totalVolume), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
                Text(" kg", color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp, modifier = Modifier.padding(bottom = 6.dp))
            }
            Row(
                Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val deltaText = when {
                    delta == null -> "—"
                    delta >= 0 -> "↑${Math.round(delta * 100)}% vs 上期"
                    else -> "↓${Math.round(-delta * 100)}% vs 上期"
                }
                Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.22f)) {
                    Text(deltaText, Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text("${stats.sessions} 次训练", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun StatGrid(stats: StatsEngine.PeriodStats) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCell("训练次数", "${stats.sessions} 次", Modifier.weight(1f))
        StatCell("总组数", "${stats.totalSets} 组", Modifier.weight(1f))
    }
}

@Composable
private fun StatCell(name: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = HColors.Card, shadowElevation = 1.dp) {
        Column(Modifier.padding(14.dp)) {
            Text(name, color = HColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(value, color = HColors.Primary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun TrendCard(points: List<BarPoint>, total: Double) {
    BlockCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            CardTitle("容量趋势")
            Text("${fmtComma(total)} kg", color = HColors.Primary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(10.dp))
        VolumeBarChart(points)
    }
}

@Composable
private fun MonthPickerRow(months: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        months.forEach { m ->
            val sel = m == selected
            Surface(
                shape = CircleShape,
                color = if (sel) HColors.Primary else HColors.Card,
                shadowElevation = if (sel) 0.dp else 1.dp,
                onClick = { onSelect(m) },
            ) {
                Text(
                    "${m.substring(5, 7).toInt()}月",
                    Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    color = if (sel) HColors.TextPrimary else HColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun trendPoints(days: List<TrainingDay>, range: StatsEngine.Range, today: String): List<BarPoint> {
    val volByDate = days.associate { it.date to it.totalVolume() }
    if (range == StatsEngine.Range.ALL) {
        val map = LinkedHashMap<String, Double>()
        days.forEach { d -> val k = d.date.substring(0, 7); map[k] = (map[k] ?: 0.0) + d.totalVolume() }
        return map.entries.sortedBy { it.key }.map { BarPoint("${it.key.substring(5).toInt()}月", it.value) }
    }
    val n = if (range == StatsEngine.Range.WEEK) 7 else 30
    return (n - 1 downTo 0).map { i ->
        val d = DateUtils.offset(today, -i)
        BarPoint(DateUtils.mdLabel(d), volByDate[d] ?: 0.0)
    }
}
