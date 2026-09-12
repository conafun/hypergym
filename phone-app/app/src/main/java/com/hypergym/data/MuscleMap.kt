package com.hypergym.data

import java.util.concurrent.ConcurrentHashMap

/**
 * 动作 → 肌群分类。
 *
 * ## 权威来源
 *
 * 手环端动作表（`miband10pro-trainer/src/common/data.js` 的 `DEFAULT_EXERCISES`）是**唯一**
 * 权威来源：手环没有增删动作的 UI，`loadExercises()` 全项目也从未被调用，所以运行期动作表
 * 就等于那个数组。它由 `tools/sync-band-exercises.js` 生成为 [BandExercises]，手机端构建前
 * 自动同步 —— 以后改 data.js，APK 会跟着变。
 *
 * 手机端记录的动作名**全部**来自手环 payload（手机没有手工录入入口），因此这张精确映射表
 * 能覆盖所有当前动作，不需要靠猜。
 *
 * ## 判定顺序（纯函数，不依赖任何初始化）
 *
 *  1. [BandExercises.groupByName] —— 当前手环动作表，精确命中（主路径）
 *  2. [LEGACY_ALIASES] —— 已从动作表移除、但历史记录里还可能有的旧动作名
 *  3. [keywords] —— 关键词兜底
 *  4. 「其他」
 *
 * ## 为什么删掉了原来的「动作库模糊匹配」
 *
 * 旧实现用 1324 条英文动作库 + 最长公共子串 + 二元组 Dice 相似度去猜部位，有两个硬伤：
 *
 *  - **有顺序依赖**：`init()` 只在肌群页调用过一次，没进过肌群页就静默退化成纯关键词匹配，
 *    于是同一个动作的分类结果取决于用户点过哪些页面（且 `groupCache` 是普通 HashMap，有并发写风险）。
 *  - **会误判**：例如「水平胸推」被匹配到相似度只有 0.60 的「后水平」从而算成**背**，
 *    「罗马椅」「腿弯曲」则完全没命中而掉进「其他」——26 个动作里有 6 个分错。
 *
 * 改成精确查表后，上述问题一并消失，分类也不再需要 Context、不需要解析 941 KB 的动作库。
 * 动作浏览页仍然用 [ExerciseLibrary]，那是独立的另一件事（英文动作库的百科/教学数据）。
 */
object MuscleMap {

    data class Group(val name: String, val color: Long)

    /**
     * 手环动作表里没有、但历史记录里出现过的分组配色。
     *
     * 胸/肩/背/腿/臂/核心/其他 一律以手环 `GROUP_COLORS` 为准（见 [BandExercises.groupColors]），
     * 这里只补手环没有的两组，保证老数据也有颜色可显示。
     */
    private val EXTRA_COLORS = linkedMapOf(
        "有氧" to 0xFFB7C8A0L,   // 橄榄绿
        "颈"   to 0xFFD9A79BL,   // 暖棕粉
    )

    /** 未命中任何分组时的中性米灰 */
    private const val FALLBACK_COLOR = 0xFFC7BEAFL

    /**
     * 完整色板，**键的顺序即分组展示顺序**：先按手环 GROUP_ORDER，再补手环没有的分组，
     * 最后是「其他」。色值优先取手环 GROUP_COLORS（缺失时回退默认色）。
     */
    private val palette: Map<String, Long> = run {
        val m = LinkedHashMap<String, Long>()
        for (g in BandExercises.groupOrder) {
            m[g] = BandExercises.groupColors[g] ?: EXTRA_COLORS[g] ?: FALLBACK_COLOR
        }
        for ((k, v) in EXTRA_COLORS) if (k !in m) m[k] = v
        // 「其他」始终排最后：它表示未命中，不该混在真实部位中间
        m.remove("其他")
        m["其他"] = BandExercises.groupColors["其他"] ?: FALLBACK_COLOR
        m
    }

    /**
     * 分组列表（顺序沿用 [palette] 的键序）。
     * PR 卡片按这个顺序给动作分组，饼图图例也用它，保证各处顺序一致。
     */
    val groups: List<Group> = palette.map { (name, color) -> Group(name, color) }

    /** 分类结果缓存：分类是纯函数，缓存只为省掉重复的关键词扫描；用并发容器避免多线程写入问题。 */
    private val cache = ConcurrentHashMap<String, String>()

    /** 手环记录的动作名 → 肌群组名。 */
    fun groupOf(exercise: String): String {
        val e = exercise.trim()
        if (e.isEmpty()) return "其他"
        cache[e]?.let { return it }
        val g = classify(e)
        cache[e] = g
        return g
    }

    private fun classify(e: String): String {
        // 1) 手环动作表精确命中 —— 分类的唯一权威依据
        BandExercises.groupByName[e]?.let { return it }
        // 2) 历史动作名（已从动作表移除）
        LEGACY_ALIASES[e]?.let { return it }
        // 3) 关键词兜底
        keywordGroup(e)?.let { return it }
        // 4) 其他
        return "其他"
    }

    /**
     * 历史动作名 → 部位。
     *
     * 这些名字曾经在（或曾被认为在）手环动作表里，旧记录中仍有，但已查不到 [BandExercises]，
     * 只能在此手工落死。`tools/sync-band-exercises.js` 检测到动作被移除时会主动提醒补这里。
     *
     * 注意：**仍留在当前动作表里的名字不要写进来**（精确表优先级更高，写这里只会造成两份定义）。
     *
     * 已知历史名（来自早期手环动作表和测试数据 `.research/seed/records-0001.jsonl`）：
     * 六角杠铃、T杠划船/T杠、腿举、推举、弯举、飞鸟 —— 后四个由 [keywords] 兜住。
     */
    private val LEGACY_ALIASES = mapOf(
        "六角杠铃" to "腿",   // trap bar 硬拉 / 深蹲
        "T杠划船"  to "背",   // T-bar row
        "T杠"      to "背",
    )

    // ---------------- 关键词兜底 ----------------

    // 顺序即优先级（如「双杠臂屈伸」要先命中胸）。这里的口径与手环动作表保持一致：
    // 「面拉」按手环算肩（不是背），「罗马椅」算核心，「牧师椅」算臂。
    //
    // 关键词里的「胸推」「肩推」「腿弯」这些是**为了历史动作名**留的：
    // 手环动作表曾经有过「坐姿胸推 / 胸推 / 肩推」等名字（见 git 历史），
    // 现在虽已移除，但旧记录里还在，删掉关键词会让它们掉进「其他」。
    private val keywords = linkedMapOf(
        "胸" to listOf("卧推", "俯卧撑", "飞鸟", "夹胸", "双杠臂屈伸", "胸推", "推胸", "蝴蝶机", "扩胸"),
        "肩" to listOf("推举", "侧平举", "前平举", "耸肩", "颈后推", "直立划船", "肩推", "阿诺德", "面拉"),
        "背" to listOf("划船", "引体", "下拉", "直臂下压", "挺身"),
        "腿" to listOf("深蹲", "腿举", "腿弯", "箭步", "硬拉", "腿屈伸", "提踵", "臀桥", "高脚杯", "髋", "倒蹬", "蹬腿", "深蹲跳"),
        "臂" to listOf("弯举", "臂屈伸", "锤式", "三头", "二头", "前臂", "手腕", "牧师椅"),
        "核心" to listOf("卷腹", "平板", "举腿", "健腹", "俄罗斯转体", "仰卧起坐", "侧屈", "山羊", "罗马椅"),
        "有氧" to listOf("跑步", "开合跳", "跳绳", "椭圆", "单车", "划船机", "波比", "跳", "踏步", "登山"),
        "颈" to listOf("颈"),
    )

    private fun keywordGroup(e: String): String? {
        for ((group, kws) in keywords) {
            if (kws.any { e.contains(it) }) return group
        }
        return null
    }

    // ---------------- 颜色 ----------------

    fun colorOf(group: String): Long = palette[group] ?: FALLBACK_COLOR
}
