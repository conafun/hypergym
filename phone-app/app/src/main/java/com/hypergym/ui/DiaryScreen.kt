package com.hypergym.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypergym.data.StatsEngine
import com.hypergym.data.TrainingDay

/** 训练日记：日期/星期/PR徽章/总容量(橙) + 动作明细；长按卡片进入删除模式（晃动 + 右上角叉） */
@Composable
fun DiaryScreen(
    days: List<TrainingDay>,
    onDeleteDay: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sorted = remember(days) { days.sortedByDescending { it.date } }
    val prMap = remember(days) { StatsEngine.prFlags(days) }
    var editingDate by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageHeader("训练日记", "${sorted.size} 天训练记录 · 长按卡片可删除") }
        items(sorted, key = { it.date }) { day ->
            DayCard(
                day = day,
                prs = prMap[day.date],
                editing = editingDate == day.date,
                onToggleEdit = {
                    editingDate = if (editingDate == day.date) null else day.date
                },
                onDelete = {
                    editingDate = null
                    onDeleteDay(day.date)
                },
            )
        }
    }
}

@Composable
private fun DayCard(
    day: TrainingDay,
    prs: List<String>?,
    editing: Boolean,
    onToggleEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val shake = rememberInfiniteTransition(label = "shake")
    val angle by shake.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(120, easing = LinearEasing), RepeatMode.Reverse),
        label = "shakeAngle",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { rotationZ = if (editing) angle else 0f },
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(editing) {
                    detectTapGestures(
                        onTap = { if (editing) onToggleEdit() },
                        onLongPress = { if (!editing) onToggleEdit() },
                    )
                },
            shape = CardRadius,
            color = HColors.Card,
            shadowElevation = if (editing) 6.dp else 1.dp,
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(day.date, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = HColors.TextPrimary)
                    Surface(shape = RoundedCornerShape(6.dp), color = HColors.Background, modifier = Modifier.padding(start = 6.dp)) {
                        Text(
                            DateUtils.weekdayLabel(day.date),
                            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 11.sp,
                            color = HColors.TextSecondary,
                        )
                    }
                    if (prs != null) {
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFFFF3E0), modifier = Modifier.padding(start = 6.dp)) {
                            Text(
                                "🏆 PR",
                                Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309),
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${fmtComma(day.totalVolume())} kg",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = HColors.Primary,
                    )
                }
                day.records.forEachIndexed { i, ex ->
                    if (i > 0) HorizontalDivider(color = HColors.Border)
                    ExerciseRow(ex)
                }
            }
        }

        // 删除按钮：编辑模式下从右上角弹出
        AnimatedVisibility(
            visible = editing,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (-6).dp),
            enter = scaleIn(),
            exit = scaleOut(),
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE5484D))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Text("✕", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
