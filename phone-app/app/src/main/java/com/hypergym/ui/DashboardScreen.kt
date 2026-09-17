package com.hypergym.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypergym.data.MuscleMap
import com.hypergym.data.PrRangePrefs
import com.hypergym.data.StatsEngine
import com.hypergym.data.TrainingDay
import java.util.Calendar

private val RestGray = Color(0xFFEEF0F2)

/** 热力图档数：有训练的天按容量「名次」平均分成 4 档 */
private const val HEAT_LEVELS = 4

/**
 * 热力图色阶（未训练另用 [RestGray]）。两点考虑：
 *
 *  1. 用**实色**而不是给主色加透明度 —— alpha 叠在白卡上会发灰发脏。
 *  2. 日期数字**保持白色不变**，所以 4 个档位都必须落在「白字看得清」的深度范围内。
 *     实测白字放在 #E8836A 这类浅橙上对比度只有 2.7:1，根本读不出来；
 *     因此色阶取「鲑橙 → 主橙 → 深橙 → 砖红」，靠明度 + 饱和度共同拉开差别
 *     （白字对比度依次 3.15 : 3.55 : 4.57 : 6.38，全部达标；而浅橙 #E8836A 只有 2.66，读不出来）。
 */
private val HeatPalette = listOf(
    Color(0xFFDD7355),
    Color(0xFFE06040),
    Color(0xFFC84E32),
    Color(0xFFA63C24),
)

/** 日期数字颜色：已训练一律白字（**保持原样，不改**），只有未训练用深色 */
private fun heatLabelColor(level: Int?): Color =
    if (level != null) Color.White else HColors.TextPrimary

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
    // 必须按日期**升序**：旧日期在左、新日期在右，与「周 / 全部」一致。
    // （这里原本是 `endDay downTo 1`，导致「月」档把新日期排在左边，与另外两档相反。）
    return (1..endDay).map { d ->
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
    // 热力图分档：把有训练的天按容量从少到多排队，再按「名次」平均分成 HEAT_LEVELS 档。
    // 用名次而不是「当天 ÷ 最大值」——否则只要有一天特别多，其余天就全被压到同一片浅色里，
    // 看着几乎一样（这就是原来「看不出区别」的原因）。按名次分档则不管数据怎么分布都能铺满各档。
    val heatLevel = remember(sorted) {
        val vols = sorted.map { it.date to it.totalVolume() }.sortedBy { it.second }
        val n = vols.size
        if (n == 0) {
            emptyMap()
        } else {
            vols.mapIndexed { i, (date, _) ->
                date to (i * HEAT_LEVELS / n).coerceIn(0, HEAT_LEVELS - 1)
            }.toMap()
        }
    }
    // 各动作的历史最高重量（= PR）。始终基于全部历史，不随上方的 周/月/全部 切换变化。
    val prBest = remember(sorted) { StatsEngine.bestWeights(sorted) }
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
                heatLevel = heatLevel,
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
        if (prBest.isNotEmpty()) {
            item { PrCard(prBest) }
        }
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
    heatLevel: Map<String, Int>,
    today: String,
    selected: String,
    expanded: Boolean,
    displayedMonth: String,
    onMonthShift: (Int) -> Unit,
    onToggle: () -> Unit,
    onSelect: (String) -> Unit,
) {
    BlockCard {
        // 只要热力图本身：标题等说明性文字一律不加（自用 App，图形含义一目了然）
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
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
            MonthGrid(heatLevel, displayedMonth, today, selected, onSelect)
        } else {
            WeekStrip(heatLevel, today, selected, onSelect)
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
    heatLevel: Map<String, Int>,
    today: String,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (6 downTo 0).forEach { i ->
            val d = DateUtils.offset(today, -i)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                HeatCell(
                    level = heatLevel[d],
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
    heatLevel: Map<String, Int>,
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
                                level = heatLevel[date],
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
    level: Int?,
    label: String,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (level == null) RestGray else HeatPalette[level.coerceIn(0, HeatPalette.lastIndex)]
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
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = heatLabelColor(level))
    }
}

// ---------------- 当天训练内容 ----------------

@Composable
private fun DayContentCard(day: TrainingDay?, date: String, today: String) {
    val groups = remember(day) { day?.records?.let { groupByExercise(it) } ?: emptyList() }
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
        if (groups.isEmpty()) {
            Text("无训练记录", color = HColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 20.dp))
        } else {
            groups.forEachIndexed { i, g ->
                if (i > 0) HorizontalDivider(color = HColors.Border)
                ExerciseGroupRow(g)
            }
        }
    }
}

// ---------------- PR 纪录卡片 ----------------

/**
 * 「PR 纪录」卡片：每个动作的历史最高重量，换算成可直接上杠的配重。
 *
 * **肌群可折叠**（默认全部收起，点肌群行才展开，可同时展开多个）：
 * ```
 * PR 纪录                     实际用范围 [65]%–[70]%
 * ● 胸                                 4 个动作  ▸
 * ● 肩                                 5 个动作  ▸
 * ```
 * 展开后每个动作一行（动作行只用**肌群色点**标识归属，不重复写肌群名）：
 * ```
 * ● 卧推        75kg        50.0–52.5kg
 * ```
 *
 * 规则：
 *  - 分组顺序沿用 [MuscleMap.groups]；组内按重量降序（最重的排最前）
 *  - 只显示有配重（weight > 0）的动作 —— 自重按口径不算 PR
 *  - PR 取法不变（[StatsEngine.bestWeights]），始终基于全部历史，不随「周 / 月 / 全部」变化
 *  - 配色：PR 用主题橙，换算结果用主文字色（黑）
 *  - 「实际用」= PR × 上下限百分比，各自取到最近的 2.5kg（[StatsEngine.roundTo25]），
 *    两端相同则只显示一个值。百分比可手输且**持久保存**（[PrRangePrefs]），
 *    所以 PR 更新或百分比改动后，下面的数字都会立即重算。
 */
@Composable
private fun PrCard(prBest: Map<String, Double>) {
    val grouped = remember(prBest) {
        MuscleMap.groups.mapNotNull { g ->
            val items = prBest.entries
                .filter { MuscleMap.groupOf(it.key) == g.name }
                .sortedByDescending { it.value }
            if (items.isEmpty()) null else g to items
        }
    }
    if (grouped.isEmpty()) return

    val context = LocalContext.current
    // 首次从持久化读，之后由输入框驱动；输入不合法（空/非数字）时回落到上一次的有效值
    val saved = remember { PrRangePrefs.load(context) }
    var loText by remember { mutableStateOf(saved.first.toString()) }
    var hiText by remember { mutableStateOf(saved.second.toString()) }
    val lo = loText.toIntOrNull()?.coerceIn(PrRangePrefs.MIN_PCT, PrRangePrefs.MAX_PCT) ?: saved.first
    val hi = hiText.toIntOrNull()?.coerceIn(PrRangePrefs.MIN_PCT, PrRangePrefs.MAX_PCT) ?: saved.second
    val pctLo = minOf(lo, hi)     // 允许反着填，小的当低值、大的当高值
    val pctHi = maxOf(lo, hi)

    var openGroups by remember { mutableStateOf(emptySet<String>()) }

    BlockCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CardTitle("PR 纪录", Modifier.weight(1f))
            Text("实际用范围", fontSize = 10.sp, color = HColors.TextSecondary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            PctField(loText) { v -> loText = v; persistPct(context, v, hiText) }
            PctSign()
            Text(
                "–",
                fontSize = 11.sp,
                color = HColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 3.dp),
            )
            PctField(hiText) { v -> hiText = v; persistPct(context, loText, v) }
            PctSign()
        }
        Spacer(Modifier.height(6.dp))

        grouped.forEachIndexed { gi, (group, items) ->
            val open = group.name in openGroups
            if (gi > 0) HorizontalDivider(thickness = 1.dp, color = HColors.Border)
            Column(Modifier.fillMaxWidth()) {
                // 折叠头：点整行开/关
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            openGroups = if (open) openGroups - group.name else openGroups + group.name
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GroupDot(group.name)
                    Spacer(Modifier.width(8.dp))
                    Text(group.name, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = HColors.TextPrimary)
                    Spacer(Modifier.weight(1f))
                    Text("${items.size} 个动作", fontSize = 11.sp, color = HColors.TextSecondary)
                    Spacer(Modifier.width(6.dp))
                    Text(if (open) "▾" else "▸", fontSize = 10.sp, color = HColors.TextSecondary)
                }
                if (open) {
                    Column(Modifier.fillMaxWidth().padding(start = 14.dp, bottom = 6.dp)) {
                        items.forEach { (name, weight) ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                GroupDot(group.name)
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    name,
                                    Modifier.weight(1f),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = HColors.TextPrimary,
                                )
                                Text(
                                    "${StatsEngine.fmtDouble(weight)}kg",
                                    Modifier.width(56.dp),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HColors.Primary,
                                    textAlign = TextAlign.End,
                                )
                                Text(
                                    useRangeLabel(weight, pctLo, pctHi),
                                    Modifier.width(86.dp),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HColors.TextPrimary,
                                    textAlign = TextAlign.End,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 肌群色点 */
@Composable
private fun GroupDot(groupName: String) {
    Box(Modifier.size(6.dp).clip(CircleShape).background(Color(MuscleMap.colorOf(groupName))))
}

/**
 * 「实际用」文案：PR × 上下限百分比，各自取到最近的 2.5kg（四舍五入）；两端相同则只显示一个值。
 * 结果固定 1 位小数，所以只会出现 .0 / .5。
 */
private fun useRangeLabel(weight: Double, pctLo: Int, pctHi: Int): String {
    val low = StatsEngine.roundTo25(weight * pctLo / 100.0)
    val high = StatsEngine.roundTo25(weight * pctHi / 100.0)
    return if (low == high) "${StatsEngine.fmtOneDecimal(low)}kg"
    else "${StatsEngine.fmtOneDecimal(low)}–${StatsEngine.fmtOneDecimal(high)}kg"
}

/** 百分比输入框：只收数字、最多 3 位，居中显示 */
@Composable
private fun PctField(value: String, onChange: (String) -> Unit) {
    Box(
        Modifier
            .width(38.dp)
            .height(26.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(HColors.Background)
            .border(1.dp, HColors.Border, RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = value,
            onValueChange = { s -> onChange(s.filter { it.isDigit() }.take(3)) },
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = HColors.Primary,
                textAlign = TextAlign.Center,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            cursorBrush = SolidColor(HColors.Primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 百分比后面的「%」 */
@Composable
private fun PctSign() {
    Text(
        "%",
        fontSize = 10.5.sp,
        color = HColors.TextSecondary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 3.dp),
    )
}

/** 两个百分比都合法才落盘，避免输入途中把半截数字写进去 */
private fun persistPct(context: Context, lo: String, hi: String) {
    val a = lo.toIntOrNull() ?: return
    val b = hi.toIntOrNull() ?: return
    if (a in PrRangePrefs.MIN_PCT..PrRangePrefs.MAX_PCT &&
        b in PrRangePrefs.MIN_PCT..PrRangePrefs.MAX_PCT
    ) {
        PrRangePrefs.save(context, a, b)
    }
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
                    color = if (sel) Color.White else HColors.TextSecondary,
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
