package com.hypergym.data

/**
 * 动作 → 肌群映射表（关键词匹配，中文动作名）。
 * 未匹配到的动作归入「其他」。
 */
object MuscleMap {

    data class Group(val name: String, val color: Long)

    val groups = listOf(
        Group("胸", 0xFFEF5350),
        Group("肩", 0xFFFFA726),
        Group("背", 0xFF42A5F5),
        Group("腿", 0xFF66BB6A),
        Group("臂", 0xFFAB47BC),
        Group("核心", 0xFF26C6DA),
        Group("其他", 0xFF90A4AE),
    )

    // 注意顺序即优先级（如「双杠臂屈伸」先命中胸，「直立划船」先命中肩）
    private val keywords = mapOf(
        "胸" to listOf("卧推", "俯卧撑", "飞鸟", "夹胸", "双杠臂屈伸", "推胸", "蝴蝶机"),
        "肩" to listOf("推举", "侧平举", "前平举", "耸肩", "颈后推", "直立划船"),
        "背" to listOf("划船", "引体", "下拉", "直臂下压", "面拉", "挺身"),
        "腿" to listOf("深蹲", "腿举", "箭步", "硬拉", "腿屈伸", "腿弯举", "提踵", "臀桥", "高脚杯", "髋"),
        "臂" to listOf("弯举", "臂屈伸", "锤式"),
        "核心" to listOf("卷腹", "平板", "举腿", "健腹", "俄罗斯转体", "仰卧起坐"),
    )

    fun groupOf(exercise: String): String {
        val e = exercise.trim()
        for ((g, kws) in keywords) {
            if (kws.any { e.contains(it) }) return g
        }
        return "其他"
    }

    fun colorOf(group: String): Long =
        groups.firstOrNull { it.name == group }?.color ?: 0xFF90A4AE
}
