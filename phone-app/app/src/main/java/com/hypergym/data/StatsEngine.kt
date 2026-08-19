package com.hypergym.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 展示层统计引擎：全部是纯函数，输入 TrainingDay 列表，输出可直接渲染的数据。
 * 日期统一用 ISO "yyyy-MM-dd" 字符串（字典序即时间序）。
 */
object StatsEngine {

    private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    enum class Range(val days: Int?, val label: String) {
        WEEK(7, "周"),
        MONTH(30, "月"),
        ALL(null, "全部"),
    }

    data class PeriodStats(
        val sessions: Int,
        val totalVolume: Double,
        val totalSets: Int,
        val avgVolume: Double,
    )

    data class HeatCell(val date: String, val volume: Double, val trained: Boolean)

    data class TrendPoint(val x: Int, val y: Double, val label: String)

    data class TrendSeries(
        val points: List<TrendPoint>,
        val deltaPct: Double?,     // 环比上一等长周期，无法比较时为 null
        val unitLabel: String,
    )

    // ---------------- 基础日期工具 ----------------

    fun parseDate(s: String): Calendar? = try {
        Calendar.getInstance().apply { time = fmt.parse(s)!! }
    } catch (t: Throwable) {
        null
    }

    fun formatDate(cal: Calendar): String = fmt.format(cal.time)

    fun todayString(): String = fmt.format(Calendar.getInstance().time)

    private fun dayStrOffset(todayStr: String, offsetDays: Int): String {
        val c = parseDate(todayStr) ?: return todayStr
        c.add(Calendar.DATE, offsetDays)
        return formatDate(c)
    }

    fun weekdayChinese(dateStr: String): String {
        val cal = parseDate(dateStr) ?: return ""
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "周一"
            Calendar.TUESDAY -> "周二"
            Calendar.WEDNESDAY -> "周三"
            Calendar.THURSDAY -> "周四"
            Calendar.FRIDAY -> "周五"
            Calendar.SATURDAY -> "周六"
            else -> "周日"
        }
    }

    // ---------------- 周期统计 ----------------

    fun periodStats(days: List<TrainingDay>, range: Range, todayStr: String): PeriodStats {
        val filtered = when (range) {
            Range.WEEK -> {
                val s = dayStrOffset(todayStr, -6)
                days.filter { it.date in s..todayStr }
            }
            Range.MONTH -> {
                val s = dayStrOffset(todayStr, -29)
                days.filter { it.date in s..todayStr }
            }
            Range.ALL -> days
        }
        val vol = filtered.sumOf { it.totalVolume() }
        return PeriodStats(
            sessions = filtered.size,
            totalVolume = vol,
            totalSets = filtered.sumOf { it.totalSets() },
            avgVolume = if (filtered.isEmpty()) 0.0 else vol / filtered.size,
        )
    }

    private fun sumIn(days: List<TrainingDay>, start: String, end: String): Double =
        days.filter { it.date in start..end }.sumOf { it.totalVolume() }

    /** 周容量表：key = 该周周一的日期 */
    fun weeklyVolumes(days: List<TrainingDay>): Map<String, Double> {
        val map = LinkedHashMap<String, Double>()
        for (d in days) {
            val cal = parseDate(d.date) ?: continue
            cal.add(Calendar.DATE, -((cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7))
            val key = formatDate(cal)
            map[key] = (map[key] ?: 0.0) + d.totalVolume()
        }
        return map
    }

    /** 环比：本期总容量 vs 上一个等长周期；上期为 0 时返回 null */
    fun deltaPercent(days: List<TrainingDay>, range: Range, todayStr: String): Double? {
        val cur: Double
        val prev: Double
        when (range) {
            Range.WEEK -> {
                cur = sumIn(days, dayStrOffset(todayStr, -6), todayStr)
                prev = sumIn(days, dayStrOffset(todayStr, -13), dayStrOffset(todayStr, -7))
            }
            Range.MONTH -> {
                cur = sumIn(days, dayStrOffset(todayStr, -29), todayStr)
                prev = sumIn(days, dayStrOffset(todayStr, -59), dayStrOffset(todayStr, -30))
            }
            Range.ALL -> {
                val w = weeklyVolumes(days)
                val keys = w.keys.sorted()
                cur = keys.takeLast(4).sumOf { w[it] ?: 0.0 }
                prev = keys.dropLast(4).takeLast(4).sumOf { w[it] ?: 0.0 }
            }
        }
        if (prev <= 0.0) return null
        return (cur - prev) / prev
    }

    /** 趋势序列：周/月按天，全部按周汇总 */
    fun trendSeries(days: List<TrainingDay>, range: Range, todayStr: String): TrendSeries {
        val volByDate = days.associate { it.date to it.totalVolume() }
        val points: List<TrendPoint>
        val cur: Double
        val prev: Double
        when (range) {
            Range.WEEK, Range.MONTH -> {
                val n = range.days!!
                val start = dayStrOffset(todayStr, -(n - 1))
                points = (0 until n).map { i ->
                    val d = dayStrOffset(start, i)
                    TrendPoint(i + 1, volByDate[d] ?: 0.0, d.substring(5))
                }
                cur = points.sumOf { it.y }
                prev = when (range) {
                    Range.WEEK -> (0 until 7).sumOf { i ->
                        volByDate[dayStrOffset(todayStr, -13 + i)] ?: 0.0
                    }
                    else -> (0 until 30).sumOf { i ->
                        volByDate[dayStrOffset(todayStr, -59 + i)] ?: 0.0
                    }
                }
            }
            Range.ALL -> {
                val w = weeklyVolumes(days)
                val keys = w.keys.sorted()
                points = keys.mapIndexed { i, k -> TrendPoint(i + 1, w[k] ?: 0.0, k.substring(5)) }
                cur = keys.takeLast(4).sumOf { w[it] ?: 0.0 }
                prev = keys.dropLast(4).takeLast(4).sumOf { w[it] ?: 0.0 }
            }
        }
        val delta = if (prev > 0.0 && cur > 0.0) (cur - prev) / prev else null
        val unit = if (range == Range.ALL) "按周汇总 · 单位 kg·次" else "按天 · 单位 kg·次"
        return TrendSeries(points, delta, unit)
    }

    // ---------------- 热力图 ----------------

    /**
     * 近 [weeks] 周训练热力图：外层 = 周（旧→新），内层 = 周一..周日。
     * 每格一个 HeatCell（未训练 volume=0）。
     */
    fun heatmapWeeks(dayVolumes: Map<String, Double>, todayStr: String, weeks: Int = 5): List<List<HeatCell>> {
        val today = parseDate(todayStr) ?: return emptyList()
        val monday = (today.clone() as Calendar).apply {
            add(Calendar.DATE, -((get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7))
        }
        val first = (monday.clone() as Calendar).apply { add(Calendar.DATE, -(weeks - 1) * 7) }
        val cur = first.clone() as Calendar
        return (0 until weeks).map {
            (0 until 7).map {
                val d = formatDate(cur)
                val v = dayVolumes[d]
                cur.add(Calendar.DATE, 1)
                HeatCell(d, v ?: 0.0, v != null)
            }
        }
    }

    /** 连续训练周数（从本周往回数，本周没练则从上周起算） */
    fun weeklyStreak(dayVolumes: Map<String, Double>, todayStr: String): Int {
        val today = parseDate(todayStr) ?: return 0
        val monday = (today.clone() as Calendar).apply {
            add(Calendar.DATE, -((get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7))
        }
        val trained = dayVolumes.keys.toSet()
        var streak = 0
        var isCurrentWeek = true
        while (streak < 200) {
            val weekDates = (0 until 7).map { i ->
                formatDate((monday.clone() as Calendar).apply { add(Calendar.DATE, i) })
            }
            val has = weekDates.any { it in trained }
            if (has) {
                streak++
            } else if (!isCurrentWeek) {
                break
            }
            monday.add(Calendar.DATE, -7)
            isCurrentWeek = false
        }
        return streak
    }

    // ---------------- 肌群分布 ----------------

    /** 各肌群总容量，降序；Triple = (肌群名, 容量, 颜色) */
    fun muscleDistribution(days: List<TrainingDay>): List<Triple<String, Double, Long>> {
        val map = LinkedHashMap<String, Double>()
        for (d in days) {
            for (ex in d.records) {
                val g = MuscleMap.groupOf(ex.exercise)
                map[g] = (map[g] ?: 0.0) + ex.sets.sumOf { it.volume }
            }
        }
        return map.entries.sortedByDescending { it.value }
            .map { (name, vol) -> Triple(name, vol, MuscleMap.colorOf(name)) }
    }

    // ---------------- PR 检测 ----------------

    /**
     * 每个动作的全时段纪录：最大重量、最大 e1RM（Epley：重量×(1+次数/30)）。
     * 只标记「打破此前的纪录」的日子（首次记录不算 PR）。
     * 返回：date → PR 描述列表。
     */
    fun prFlags(days: List<TrainingDay>): Map<String, List<String>> {
        val sorted = days.sortedBy { it.date }
        val bestWeight = HashMap<String, Double>()
        val bestE1rm = HashMap<String, Double>()
        val out = LinkedHashMap<String, MutableList<String>>()
        for (day in sorted) {
            val prs = mutableListOf<String>()
            for (ex in day.records) {
                if (ex.sets.isEmpty()) continue
                val maxReps = ex.sets.maxOf { it.reps }
                val e1rm = ex.weight * (1.0 + maxReps / 30.0)
                val bw = bestWeight[ex.exercise]
                val be = bestE1rm[ex.exercise]
                if (bw != null && ex.weight > bw) {
                    prs.add("${ex.exercise} 重量PR ${fmtDouble(ex.weight)}kg")
                }
                if (be != null && e1rm > be) {
                    prs.add("${ex.exercise} 力量PR ${fmtDouble(e1rm)}kg")
                }
                if (bw == null || ex.weight > bw) bestWeight[ex.exercise] = ex.weight
                if (be == null || e1rm > be) bestE1rm[ex.exercise] = e1rm
            }
            if (prs.isNotEmpty()) out[day.date] = prs
        }
        return out
    }

    fun fmtDouble(v: Double): String {
        val r = kotlin.math.round(v * 10.0) / 10.0
        return if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
    }
}
