package com.hypergym.data

/**
 * 动作 → 肌群分类。
 *
 * 手环端记录的往往是很短的动作名（如「卧推」「六角杠铃」「大剪刀」「倒蹬」「T杠划船」），
 * 直接按关键词只能命中一部分，剩下会堆积到「其他」，导致肌群占比饼图失真。
 *
 * 这里改成「以动作库为准」：先用动作库（assets/exercises/exercises.json）初始化，
 * 把每个动作库动作与其「身体部位」（category → 中文组）建立索引；对一条记录的动作名，
 * 依次尝试：
 *   1. 手环专属动作名直查（与动作库命名不同、但对应关系确定，如 倒蹬→腿、T杠划船→背）；
 *   2. 动作库模糊匹配（利用最长公共子串 + 二元组 Dice 相似度，命名不一致时也能命中大体部位）；
 *   3. 关键词兜底；
 *   4. 归入「其他」。
 * 这样占比数据源自动作库各动作所训练的肌群，划分更清晰。
 */
object MuscleMap {

    data class Group(val name: String, val color: Long)

    /** 分组展示顺序；颜色为同色系低饱和、柔和过渡色板（与全局主题一致）。 */
    val groups = listOf(
        Group("胸",   0xFFFA734F),   // 珊瑚
        Group("肩",   0xFFF0A47F),   // 浅珊瑚
        Group("背",   0xFF95DAE7),   // 天空蓝
        Group("腿",   0xFF9CCFBF),   // 柔和绿
        Group("臂",   0xFFA98BB4),   // 柔和紫
        Group("核心", 0xFF7FB3D5),   // 柔和蓝紫
        Group("有氧", 0xFFB7C8A0),   // 橄榄绿
        Group("颈",   0xFFD9A79B),   // 暖棕粉
        Group("其他", 0xFFC7BEAF),   // 中性米灰（未命中）
    )

    private val groupByName = groups.associateBy { it.name }

    // ---------------- 动作库索引（惰性初始化，见 init） ----------------

    @Volatile
    private var libraryIndex: List<Pair<String, String>>? = null   // (规范化动作名, 组名)
    @Volatile
    private var initialized = false
    private val groupCache = HashMap<String, String>()

    /** 用动作库初始化分类索引；幂等，只构建一次。 */
    fun init(library: List<Exercise>) {
        if (initialized) return
        libraryIndex = buildList {
            for (e in library) {
                val g = ExerciseLibrary.groupOf(e.category)
                if (g == "其他") continue
                for (name in aliasesOf(e.name)) add(name to g)
            }
        }
        initialized = true
    }

    /** 一个动作可能带器材/部位前缀后缀，建模时拆出可复用的核心词，提高命中率。 */
    private fun aliasesOf(name: String): List<String> {
        val n = name.trim().replace(" ", "")
        val out = mutableListOf(n)
        // 去掉常见的「弹力带/杠铃/哑铃/绳索/器械/自重」等器材前缀，得到「动作本体」，便于短名命中
        val strip = listOf("弹力带", "杠铃", "哑铃", "绳索", "器械", "辅助", "负重", "史密斯机", "药球", "稳定球", "壶铃", "片") 
        var cur = n
        var changed = true
        while (changed) {
            changed = false
            for (p in strip) {
                if (cur.startsWith(p) && cur.length > p.length) {
                    cur = cur.substring(p.length); changed = true
                }
            }
        }
        if (cur != n) out.add(cur)
        return out
    }

    // ---------------- 分类入口 ----------------

    /** 手环记录的动作名 → 肌群组名。 */
    fun groupOf(exercise: String): String {
        val e = exercise.trim()
        if (e.isEmpty()) return "其他"
        groupCache[e]?.let { return it }
        val g = classify(e)
        groupCache[e] = g
        return g
    }

    /** 单纯的器材名（不含动作本体），无法据此判断训练部位，跳过动作库匹配以防误判。 */
    private val GENERIC_ONLY = setOf("哑铃", "杠铃", "器械", "绳索", "自体重")

    private fun classify(e: String): String {
        // 1) 手环专属动作名直查（与动作库命名不同）
        BAND_ALIASES[e]?.let { return it }

        // 2) 动作库模糊匹配（优先于关键词：占比以动作库各动作训练的部位为准）
        if (e !in GENERIC_ONLY) libraryBest(e)?.let { return it }

        // 3) 关键词兜底
        keywordGroup(e)?.let { return it }

        // 4) 其他
        return "其他"
    }

    /**
     * 在动作库索引中找与该动作名最相近的动作，返回其部位组名。
     * 评分：最长公共子串 + 二元组 Dice 相似度 + 包含/被包含加成。低于阈值视为不匹配。
     */
    private fun libraryBest(e: String): String? {
        val idx = libraryIndex ?: return null
        var bestScore = -1.0
        var bestGroup: String? = null
        for ((name, group) in idx) {
            val s = similarity(e, name)
            if (s > bestScore) {
                bestScore = s
                bestGroup = group
            }
        }
        return if (bestScore >= MATCH_THRESHOLD) bestGroup else null
    }

    private fun normalize(s: String): String = s.trim().replace(" ", "")

    private fun similarity(aRaw: String, bRaw: String): Double {
        val a = normalize(aRaw); val b = normalize(bRaw)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        var s = dice(a, b)
        if (b.contains(a)) s += 0.5        // 库名包含记录名（常见：记录是短名，库名是完整动作）
        else if (a.contains(b)) s += 0.3   // 记录名包含库名
        // 最长公共子串作进一步佐证
        val lcs = longestCommon(a, b)
        if (lcs > 0) s += lcs.toDouble() / maxOf(a.length, b.length) * 0.4
        return s
    }

    private fun longestCommon(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        var best = 0
        for (i in 1..a.length) for (j in 1..b.length) {
            if (a[i - 1] == b[j - 1]) {
                dp[i][j] = dp[i - 1][j - 1] + 1
                if (dp[i][j] > best) best = dp[i][j]
            }
        }
        return best
    }

    private fun dice(aRaw: String, bRaw: String): Double {
        val a = normalize(aRaw); val b = normalize(bRaw)
        if (a.length < 2 || b.length < 2) return 0.0
        fun bigrams(s: String): List<String> = (0 until s.length - 1).map { s.substring(it, it + 2) }
        val A = bigrams(a); val B = bigrams(b)
        val count = HashMap<String, Int>()
        for (x in A) count[x] = (count[x] ?: 0) + 1
        var inter = 0
        for (x in B) {
            val c = count[x] ?: 0
            if (c > 0) { inter++; count[x] = c - 1 }
        }
        return 2.0 * inter / (A.size + B.size)
    }

    // ---------------- 关键词兜底 ----------------

    // 注意顺序即优先级（如「双杠臂屈伸」先命中胸，「直立划船」先命中肩）
    private val keywords = mapOf(
        "胸" to listOf("卧推", "俯卧撑", "飞鸟", "夹胸", "双杠臂屈伸", "推胸", "蝴蝶机", "扩胸"),
        "肩" to listOf("推举", "侧平举", "前平举", "耸肩", "颈后推", "直立划船", "肩推", "阿诺德"),
        "背" to listOf("划船", "引体", "下拉", "直臂下压", "面拉", "挺身"),
        "腿" to listOf("深蹲", "腿举", "箭步", "硬拉", "腿屈伸", "腿弯举", "提踵", "臀桥", "高脚杯", "髋", "倒蹬", "蹬腿", "深蹲跳"),
        "臂" to listOf("弯举", "臂屈伸", "锤式", "三头", "二头", "前臂", "手腕"),
        "核心" to listOf("卷腹", "平板", "举腿", "健腹", "俄罗斯转体", "仰卧起坐", "侧屈", "山羊"),
        "有氧" to listOf("跑步", "开合跳", "跳绳", "椭圆", "单车", "划船机", "波比", "跳", "踏步", "登山"),
        "颈" to listOf("颈"),
    )

    private fun keywordGroup(e: String): String? {
        for ((group, kws) in keywords) {
            if (kws.any { e.contains(it) }) return group
        }
        return null
    }

    // ---------------- 手环专属动作名映射 ----------------

    /** 手环端内置动作名与动作库命名不一致，但对应部位确定，这里落死。 */
    private val BAND_ALIASES = mapOf(
        "六角杠铃" to "腿",   // trap bar 硬拉/深蹲
        "大剪刀"   to "腿",   // 腿部器械
        "倒蹬"     to "腿",   // 倒蹬机（leg press）
        "T杠划船"  to "背",   // T-bar row
        "T杠"      to "背",
        "坐姿划船" to "背",
        "高位下拉" to "背",
    )

    // ---------------- 颜色 ----------------

    fun colorOf(group: String): Long =
        groupByName[group]?.color ?: 0xFFC7BEAF

    /** 动作库匹配的最低相似度阈值；低于阈值视为不可信，回退关键词/其他。 */
    private const val MATCH_THRESHOLD = 0.60
}
