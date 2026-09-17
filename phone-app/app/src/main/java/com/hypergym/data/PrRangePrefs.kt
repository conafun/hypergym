package com.hypergym.data

import android.content.Context

/**
 * PR 卡片里「实际用范围」的两个百分比设置。
 *
 * 需求是**记住**：输入什么，下次打开 App 还是这个值，直到重新输入别的数字。
 * 所以落到 SharedPreferences —— 与 [RecordStore] 用同一个文件（`hypergym_store`），
 * 但键名独立，互不影响。
 *
 * 说明：这里只存「当前设定的范围」，不存换算结果 —— 换算永远由百分比与 PR 现算，
 * 所以 PR 更新时下面的数字会自动跟着变，不需要额外同步。
 */
object PrRangePrefs {

    private const val FILE = "hypergym_store"
    private const val KEY_LO = "pr_pct_lo"
    private const val KEY_HI = "pr_pct_hi"

    const val DEFAULT_LO = 65
    const val DEFAULT_HI = 70

    /** 合法范围：0 或负数没有意义，上限给到 200% 足够用 */
    const val MIN_PCT = 1
    const val MAX_PCT = 200

    /** 读回已保存的百分比；没有保存过（或越界）就回落到默认 65 / 70。 */
    fun load(context: Context): Pair<Int, Int> {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val lo = p.getInt(KEY_LO, DEFAULT_LO).coerceIn(MIN_PCT, MAX_PCT)
        val hi = p.getInt(KEY_HI, DEFAULT_HI).coerceIn(MIN_PCT, MAX_PCT)
        return lo to hi
    }

    /** 保存；越界值会被夹到 [MIN_PCT]..[MAX_PCT]。 */
    fun save(context: Context, lo: Int, hi: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt(KEY_LO, lo.coerceIn(MIN_PCT, MAX_PCT))
            .putInt(KEY_HI, hi.coerceIn(MIN_PCT, MAX_PCT))
            .apply()
    }
}
