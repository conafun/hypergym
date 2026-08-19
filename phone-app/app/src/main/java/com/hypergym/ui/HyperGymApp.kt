package com.hypergym.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** 根界面：底部 5 个文字 tab + 左右滑动切换 —— 数据 / 肌群 / 日记 / 动作 / 传输 */
@Composable
fun HyperGymApp(state: UiState, callbacks: AppCallbacks) {
    HyperGymTheme {
        val tabs = listOf("数据", "肌群", "日记", "动作", "传输")
        val pagerState = rememberPagerState(pageCount = { tabs.size })
        val scope = rememberCoroutineScope()
        Scaffold(
            containerColor = HColors.Background,
            bottomBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(HColors.Card)
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                ) {
                    tabs.forEachIndexed { i, label ->
                        val sel = pagerState.currentPage == i
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { scope.launch { pagerState.animateScrollToPage(i) } }
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                label,
                                fontSize = 14.sp,
                                fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = if (sel) HColors.Primary else HColors.TextSecondary,
                            )
                            Box(
                                Modifier
                                    .padding(top = 4.dp)
                                    .size(if (sel) 5.dp else 0.dp)
                                    .clip(CircleShape)
                                    .background(HColors.Primary),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                DecorativeBackground(Modifier.fillMaxSize())
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    when (page) {
                        0 -> DashboardScreen(state.days)
                        1 -> MuscleScreen(state.days)
                        2 -> DiaryScreen(state.days, callbacks.onDeleteDay)
                        3 -> ExerciseScreen()
                        4 -> DebugScreen(state, callbacks)
                    }
                }
            }
        }
    }
}
