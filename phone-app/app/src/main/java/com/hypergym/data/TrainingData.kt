package com.hypergym.data

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
