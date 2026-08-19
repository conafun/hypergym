package com.hypergym.data

import android.content.Context
import org.json.JSONArray

/** 动作库单个动作（数据来自 assets/exercises/exercises.json，纯中文教学） */
data class Exercise(
    val id: String,
    val name: String,
    val category: String,          // 英文部位：chest/back/upper legs...
    val equipment: String,         // 英文器材
    val target: String,            // 英文目标肌群
    val muscleGroup: String,       // 英文主肌群
    val secondary: List<String>,   // 英文次要肌群
    val steps: List<String>,       // 中文分步教学
    val image: String,             // "images/0001-xxx.jpg"
    val video: String,             // "videos/0001-xxx.mp4"
)

object ExerciseLibrary {

    /** 浏览页的肌群分组顺序 */
    val GROUPS = listOf("全部", "胸", "肩", "背", "腿", "臂", "核心", "有氧", "颈")

    @Volatile
    private var cache: List<Exercise>? = null

    fun load(context: Context): List<Exercise> {
        cache?.let { return it }
        return synchronized(this) {
            cache ?: parse(context).also { cache = it }
        }
    }

    private fun parse(context: Context): List<Exercise> {
        val text = context.assets.open("exercises/exercises.json")
            .bufferedReader().use { it.readText() }
        val arr = JSONArray(text)
        val list = ArrayList<Exercise>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val sec = o.optJSONArray("secondary") ?: JSONArray()
            val secondary = (0 until sec.length()).map { sec.getString(it) }
            val stepsArr = o.optJSONArray("steps") ?: JSONArray()
            val steps = (0 until stepsArr.length()).map { stepsArr.getString(it) }
            list.add(
                Exercise(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    category = o.optString("category"),
                    equipment = o.optString("equipment"),
                    target = o.optString("target"),
                    muscleGroup = o.optString("muscle_group"),
                    secondary = secondary,
                    steps = steps,
                    image = o.optString("image"),
                    video = o.optString("video"),
                )
            )
        }
        return list
    }

    /** 英文部位 → 中文分组 */
    fun groupOf(category: String): String = when (category) {
        "chest" -> "胸"
        "shoulders" -> "肩"
        "back" -> "背"
        "upper legs", "lower legs" -> "腿"
        "upper arms", "lower arms" -> "臂"
        "waist" -> "核心"
        "cardio" -> "有氧"
        "neck" -> "颈"
        else -> "其他"
    }

    fun zhMuscle(en: String): String = MUSCLE_ZH[en] ?: en
    fun zhEquipment(en: String): String = EQUIP_ZH[en] ?: en

    private val MUSCLE_ZH = mapOf(
        "abs" to "腹肌", "abdominals" to "腹肌", "lower abs" to "下腹",
        "quads" to "股四头肌", "quadriceps" to "股四头肌",
        "lats" to "背阔肌", "latissimus dorsi" to "背阔肌",
        "calves" to "小腿", "pectorals" to "胸肌", "chest" to "胸", "upper chest" to "上胸",
        "glutes" to "臀肌", "hamstrings" to "腘绳肌",
        "adductors" to "内收肌", "abductors" to "外展肌",
        "triceps" to "肱三头肌", "biceps" to "肱二头肌", "brachialis" to "肱肌",
        "delts" to "三角肌", "deltoids" to "三角肌", "rear deltoids" to "后三角肌",
        "shoulders" to "肩", "forearms" to "前臂",
        "traps" to "斜方肌", "trapezius" to "斜方肌",
        "upper back" to "上背", "back" to "背", "lower back" to "下背", "spine" to "脊柱",
        "core" to "核心", "obliques" to "腹斜肌", "hip flexors" to "髋屈肌",
        "rhomboids" to "菱形肌", "rotator cuff" to "肩袖",
        "serratus anterior" to "前锯肌", "levator scapulae" to "肩胛提肌",
        "soleus" to "比目鱼肌", "wrist extensors" to "腕伸肌", "wrist flexors" to "腕屈肌",
        "wrists" to "手腕", "hands" to "手", "ankles" to "脚踝",
        "ankle stabilizers" to "踝稳定肌", "feet" to "脚", "shins" to "胫骨前肌",
        "groin" to "腹股沟", "inner thighs" to "大腿内侧",
        "cardiovascular system" to "心肺", "grip muscles" to "握力肌",
        "sternocleidomastoid" to "胸锁乳突肌",
    )

    private val EQUIP_ZH = mapOf(
        "body weight" to "自重", "cable" to "绳索", "leverage machine" to "杠杆器械",
        "assisted" to "辅助", "medicine ball" to "药球", "stability ball" to "稳定球",
        "band" to "弹力带", "resistance band" to "阻力带", "barbell" to "杠铃",
        "rope" to "绳索", "dumbbell" to "哑铃", "ez barbell" to "EZ杠",
        "sled machine" to "雪橇机", "upper body ergometer" to "上肢测功仪",
        "kettlebell" to "壶铃", "olympic barbell" to "奥林匹克杠铃", "weighted" to "负重",
        "bosu ball" to "波速球", "roller" to "滚轮", "skierg machine" to "滑雪测功仪",
        "hammer" to "锤子", "smith machine" to "史密斯机", "wheel roller" to "健腹轮",
        "stationary bike" to "动感单车", "tire" to "轮胎", "trap bar" to "六角杠",
        "elliptical machine" to "椭圆机", "stepmill machine" to "踏步机",
    )
}
