package com.hypergym.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypergym.data.ExerciseRecord

/** 白卡（圆角 + 柔和阴影 + 内边距），区块化统一容器 */
@Composable
fun BlockCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardRadius,
        colors = CardDefaults.cardColors(containerColor = HColors.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

/** 卡片标题 */
@Composable
fun CardTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = HColors.TextPrimary, modifier = modifier)
}

/** 页头 */
@Composable
fun PageHeader(title: String, subtitle: String, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = HColors.TextPrimary)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, fontSize = 12.sp, color = HColors.TextSecondary, modifier = Modifier.padding(top = 2.dp))
            }
        }
        trailing?.invoke()
    }
}

/** 分段 pills（周/月/全部） */
@Composable
fun RangePills(
    options: List<Pair<String, String>>,
    selected: String,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(HColors.Card)
            .padding(4.dp),
    ) {
        options.forEach { (key, label) ->
            val sel = key == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (sel) HColors.Primary else Color.Transparent)
                    .clickable { onSelect(key) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (sel) Color.White else HColors.TextSecondary,
                )
            }
        }
    }
}

/** 小色点 */
@Composable
fun LegendDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(9.dp).clip(RoundedCornerShape(50)).background(color))
}

// ---------------- 动作明细（数据页 / 日记页共用）----------------

/**
 * 同一天里同名动作归为一组。
 *
 * 手环可能对同一动作记录多个重量（例如高位下拉 38kg×5组 与 30kg×1组）。
 * 若按记录逐条渲染，动作名会重复出现且各占一整块；分组后动作名只出现一次，
 * 组内各重量分行列出，既省空间又便于对照。
 */
data class ExerciseGroup(val name: String, val items: List<ExerciseRecord>) {
    val totalSets: Int get() = items.sumOf { it.sets.size }
    val totalVolume: Double get() = items.sumOf { ex -> ex.sets.sumOf { it.volume } }
}

/** 按动作名「首次出现」顺序分组，保持手环记录的原始顺序 */
fun groupByExercise(records: List<ExerciseRecord>): List<ExerciseGroup> {
    val map = LinkedHashMap<String, MutableList<ExerciseRecord>>()
    records.forEach { r -> map.getOrPut(r.exercise) { mutableListOf() }.add(r) }
    return map.map { (name, items) -> ExerciseGroup(name, items) }
}

/** 「38.0kg × 5组」/「自重 × 5组」 */
private fun weightLabelOf(ex: ExerciseRecord): String =
    if (ex.weight <= 0.0) "自重 × ${ex.sets.size}组" else "${ex.weight}kg × ${ex.sets.size}组"

/** 「容量 1,900 kg」/「共50个」 */
private fun setsSummaryOf(ex: ExerciseRecord): String =
    if (ex.weight <= 0.0) "共${ex.sets.sumOf { it.reps }}个"
    else "容量 ${fmtComma(ex.sets.sumOf { it.volume })} kg"

/** 每行最多放几个组数 chip；超出的部分折到下一行 */
private const val SETS_PER_ROW = 6

/**
 * 次数 chips + 汇总数字（汇总数字**一律右对齐**，与组数多少无关）。
 *
 * 组数多的时候只有 **chips** 会折行：每行最多 [SETS_PER_ROW] 个，多出来的落到下一行；
 * 汇总数字始终跟在最后一行并贴右。这样无论 4 组还是 8 组，汇总数字都落在同一条竖线上，
 * 便于纵向扫读、也更醒目。
 *
 * 背景：旧实现把汇总数字塞在 chips 的同一个 Row 里，8 组时 chips 占满宽度，
 * 汇总被压成逐字竖排（"共80个" 变成一列、"容量 4,560 kg" 被切成两行）。
 */
@Composable
private fun SetsLine(ex: ExerciseRecord) {
    val rows = ex.sets.chunked(SETS_PER_ROW)
    Column(
        Modifier.fillMaxWidth().padding(top = 3.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        rows.forEachIndexed { idx, chunk ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                chunk.forEach { s -> SetChip("${s.reps}次") }
                // 汇总只挂在最后一行，Spacer(weight) 把它推到最右侧
                if (idx == rows.lastIndex) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        setsSummaryOf(ex),
                        fontSize = 11.sp,
                        color = HColors.Primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/**
 * 一个动作的整块展示（全部与动作名左对齐，不做缩进）。
 * 只有一个重量时外观与原来一致（动作名 + 重量在右，chips + 容量在下）；
 * 有多个重量时动作名右侧显示该动作合计，下方按重量分行列出各自明细。
 */
@Composable
fun ExerciseGroupRow(g: ExerciseGroup) {
    val multi = g.items.size > 1
    Column(Modifier.padding(vertical = 5.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                g.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = HColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (multi) "${g.totalSets}组 · 合计 ${fmtComma(g.totalVolume)} kg"
                else weightLabelOf(g.items.first()),
                fontSize = 12.sp,
                color = if (multi) HColors.Primary else HColors.TextSecondary,
                fontWeight = if (multi) FontWeight.Bold else FontWeight.Normal,
            )
        }
        if (multi) {
            g.items.forEach { ex ->
                Text(
                    weightLabelOf(ex),
                    fontSize = 12.sp,
                    color = HColors.TextSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                SetsLine(ex)
            }
        } else {
            SetsLine(g.items.first())
        }
    }
}

/** 次数小标签 */
@Composable
fun SetChip(text: String) {
    Text(
        text,
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(HColors.PrimaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = Color(0xFF9A3412),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
    )
}
