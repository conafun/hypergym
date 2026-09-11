package com.hypergym.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 力量训练数据模型（与手环 payload 一一对应）
 *
 * 手环 → 手机 JSON 协议（v1.0.61）:
 * {
 *   "type": "training-records",
 *   "date": "2026-08-15",
 *   "records": [
 *     { "exercise": "杠铃卧推", "weight": 60, "sets": [ { "set": 1, "reps": 8, "volume": 480 } ] }
 *   ]
 * }
 */

data class SetRecord(
    val set: Int,
    val reps: Int,
    val volume: Double
)

data class ExerciseRecord(
    val exercise: String,
    val weight: Double,
    val sets: List<SetRecord>
)

data class TrainingDay(
    val date: String,        // "YYYY-MM-DD"，存储层以它做主键去重
    val records: List<ExerciseRecord>,
    val rawJson: String      // 原始行，落盘/重写时原样使用
) {
    fun totalVolume(): Double = records.sumOf { ex -> ex.sets.sumOf { it.volume } }
    fun totalSets(): Int = records.sumOf { it.sets.size }
    fun exerciseCount(): Int = records.size
}

object TrainingParser {

    /** 解析手环消息；不是 training-records 或结构非法时返回 null */
    fun parse(text: String): TrainingDay? {
        val obj = try {
            JSONObject(text)
        } catch (t: Throwable) {
            return null
        }
        if (obj.optString("type") != "training-records") return null
        val date = obj.optString("date", "").trim()
        if (date.isEmpty()) return null

        val records = mutableListOf<ExerciseRecord>()
        val arr = obj.optJSONArray("records")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                val sets = mutableListOf<SetRecord>()
                val setsArr = e.optJSONArray("sets")
                if (setsArr != null) {
                    for (j in 0 until setsArr.length()) {
                        val s = setsArr.optJSONObject(j) ?: continue
                        sets.add(
                            SetRecord(
                                set = s.optInt("set", j + 1),
                                reps = s.optInt("reps", 0),
                                volume = s.optDouble("volume", 0.0)
                            )
                        )
                    }
                }
                records.add(
                    ExerciseRecord(
                        exercise = e.optString("exercise", "未命名动作"),
                        weight = e.optDouble("weight", 0.0),
                        sets = sets
                    )
                )
            }
        }
        return TrainingDay(date, records, text)
    }
}

/**
 * 同一天多次收到数据时的合并规则。
 *
 * 背景：手环发送的是「**当天全部累计记录**」（`index.ux` 的 `_sendDayRecords` 直接取
 * `int_records[date]`），不是增量。所以同一天可能先后收到多份快照，必须合并而不是覆盖。
 *
 * 合并以 **(动作名, 重量)** 为键：
 *  - payload 里出现的键 → 用 payload 的组**替换**旧条目
 *    （手环对同一个键发的是它累计之后的完整组列表，所以「替换」等价于「累加」）
 *  - payload 里没有的键 → **原样保留**
 *
 * 这样两种诉求同时成立：
 *  - 「先发 7 个动作、再发 3 个动作」→ 得到 10 个动作（**累加**）
 *  - 「同一天重复发同一份」→ 结果不变（**幂等**，不会翻倍成 14 个）
 */
object TrainingMerger {

    private fun keyOf(r: ExerciseRecord): Pair<String, Double> = r.exercise to r.weight

    /**
     * 同一个键（动作 + 重量）下的组合并：**多重集合并**。
     *
     * 保留 [base] 原有顺序，再把 [incoming] 里"多出来的"组按原顺序追加到后面，最后统一重新编号。
     * 判断"是不是同一组"用 (次数, 容量) 组合——因为手环对同一键发的永远是它累计之后的完整组列表：
     *
     *  - 手环正常（发来的是超集）→ 结果恰好等于 incoming，不会多也不会少
     *  - 重复发送同一份 → incoming 的组都被匹配掉，结果不变（**幂等**）
     *  - 手环中途丢了数据（发来的比手机上少）→ 手机上已有的组**不会被吃掉**，只把新增的追加进来
     *  - 同一次训练里做了几组完全相同的数据 → 按"份数"匹配，不会误删也不会重复
     */
    private fun unionSets(base: List<SetRecord>, incoming: List<SetRecord>): List<SetRecord> {
        val remaining = HashMap<Pair<Int, Double>, Int>()
        for (s in base) {
            val k = s.reps to s.volume
            remaining[k] = (remaining[k] ?: 0) + 1
        }
        val out = base.toMutableList()
        for (s in incoming) {
            val k = s.reps to s.volume
            val left = remaining[k] ?: 0
            if (left > 0) {
                remaining[k] = left - 1   // 这一组手机上已经有了
            } else {
                out.add(s)                // 这是新增的组
            }
        }
        return out.mapIndexed { i, s -> s.copy(set = i + 1) }
    }

    /**
     * 把 [incoming] 合并进 [base]。
     * [base] 为 null 或日期不同时直接返回 [incoming]——保留手环原始行，不做任何改写。
     */
    fun merge(base: TrainingDay?, incoming: TrainingDay): TrainingDay {
        if (base == null || base.date != incoming.date) return incoming

        // LinkedHashMap 保证顺序：先放原有条目（顺序不变），被合并的键留在原位
        val merged = LinkedHashMap<Pair<String, Double>, ExerciseRecord>()
        for (r in base.records) merged[keyOf(r)] = r
        for (r in incoming.records) {
            val k = keyOf(r)
            val old = merged[k]
            merged[k] = if (old == null) r else r.copy(sets = unionSets(old.sets, r.sets))
        }

        val folded = TrainingDay(incoming.date, merged.values.toList(), incoming.rawJson)
        // 落盘用的 rawJson 必须换成「合并后的完整快照」，
        // 否则 persistAll 重写分片时会把当天只剩最后一次收到的部分数据写出去
        return folded.copy(rawJson = toJson(folded))
    }

    /** 序列化成与手环 payload 完全一致的一行 JSON（每天一行完整快照） */
    fun toJson(day: TrainingDay): String {
        val recordsArr = JSONArray()
        for (ex in day.records) {
            val setsArr = JSONArray()
            for (s in ex.sets) {
                setsArr.put(
                    JSONObject()
                        .put("set", s.set)
                        .put("reps", s.reps)
                        .put("volume", s.volume)
                )
            }
            recordsArr.put(
                JSONObject()
                    .put("exercise", ex.exercise)
                    .put("weight", ex.weight)
                    .put("sets", setsArr)
            )
        }
        return JSONObject()
            .put("type", "training-records")
            .put("date", day.date)
            .put("records", recordsArr)
            .toString()
    }
}
