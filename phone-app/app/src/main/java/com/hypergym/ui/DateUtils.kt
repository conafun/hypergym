package com.hypergym.ui

import com.hypergym.data.StatsEngine
import java.util.Calendar

/** 展示层日期小工具（基于 StatsEngine 的 ISO 日期字符串） */
object DateUtils {
    fun today(): String = StatsEngine.todayString()

    fun offset(base: String, days: Int): String {
        val c = StatsEngine.parseDate(base) ?: return base
        c.add(Calendar.DATE, days)
        return StatsEngine.formatDate(c)
    }

    /** 0=周一 .. 6=周日 */
    fun weekdayIndex(date: String): Int {
        val c = StatsEngine.parseDate(date) ?: return 0
        return (c.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
    }

    fun weekdayLabel(date: String): String = StatsEngine.weekdayChinese(date)

    /** "MM-DD" */
    fun mdLabel(date: String): String = date.substring(5)

    /** "DD" */
    fun dayLabel(date: String): String = date.substring(8)

    fun monthKey(date: String): String = date.substring(0, 7)

    fun monthLabel(key: String): String = "${key.substring(5).toInt()}月"
}
