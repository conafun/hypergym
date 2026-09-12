package com.hypergym.data

/**
 * ⚠️ 本文件由 `tools/sync-band-exercises.js` 自动生成，**请勿手工编辑**（改动会在下次构建时被覆盖）。
 *
 * 源头：`miband10pro-trainer/src/common/data.js`（手环端动作表 DEFAULT_EXERCISES / GROUP_ORDER / GROUP_COLORS）
 * 重新生成：`node tools/sync-band-exercises.js`（build-debug.ps1 每次构建前会自动执行）
 *
 * 手环端动作表是动作分类的唯一权威来源；手机端只做「按名字精确查表 + 少量历史兜底」，
 * 详见 [MuscleMap]。
 *
 * 本次生成：26 个动作，分组 胸/肩/背/腿/臂/核心
 */
internal object BandExercises {

    /** 手环 GROUP_ORDER：分组展示顺序（只含当前动作表真实存在的分组） */
    val groupOrder: List<String> = listOf(
        "胸", "肩", "背", "腿", "臂", "核心",
    )

    /** 手环 GROUP_COLORS：部位 → 颜色（0xAARRGGBB）。色值无效时为 null，Kotlin 侧回退默认色板 */
    val groupColors: Map<String, Long?> = mapOf(
        "胸" to 0xFFFA734FL,
        "肩" to 0xFFF0A47FL,
        "背" to 0xFF95DAE7L,
        "腿" to 0xFF9CCFBFL,
        "臂" to 0xFFA98BB4L,
        "核心" to 0xFF7FB3D5L,
        "其他" to 0xFFC7BEAFL,
    )

    /** 手环 DEFAULT_EXERCISES：动作名 → 部位。手机端分类的权威依据 */
    val groupByName: Map<String, String> = mapOf(
        "哑铃" to "肩",
        "侧平举" to "肩",
        "卷腹" to "核心",
        "卧推" to "胸",
        "水平胸推" to "胸",
        "坐姿肩推" to "肩",
        "坐姿飞鸟" to "胸",
        "下斜胸推" to "胸",
        "哈克深蹲" to "腿",
        "高位下拉" to "背",
        "大剪刀" to "背",
        "高位划船" to "背",
        "坐姿划船" to "背",
        "俯身划船" to "背",
        "杠铃深蹲" to "腿",
        "倒蹬" to "腿",
        "髋外展" to "腿",
        "髋内收" to "腿",
        "髋伸展" to "腿",
        "罗马椅" to "核心",
        "腿弯曲" to "腿",
        "轨道划船" to "背",
        "面拉" to "肩",
        "引体向上" to "背",
        "双杠臂屈伸" to "胸",
        "牧师椅" to "臂",
    )

    /** 当前手环动作表的动作数 */
    val count: Int get() = groupByName.size
}
