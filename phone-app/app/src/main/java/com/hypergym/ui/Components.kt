package com.hypergym.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
                    color = Color(0xFF000000),
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
