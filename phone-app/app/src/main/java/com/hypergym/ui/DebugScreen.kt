package com.hypergym.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val WarnOrange = Color(0xFFE07B00)   // 警告橙

/** 传输页：状态/存储 + P/S 码日志 + 重连/测试发送/清除/数据目录/重扫目录 */
@Composable
fun DebugScreen(state: UiState, callbacks: AppCallbacks, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        PageHeader("传输", "手环联调 · 数据流日志")

        BlockCard {
            val statusColor = when {
                state.status.contains("断开") || state.status.contains("失败") || state.status.contains("未找到") -> Color(0xFFBA1A1A)
                state.status.contains("已连接") -> HColors.Green
                else -> HColors.TextPrimary
            }
            Text(state.status, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = statusColor)
            Text(
                state.storage,
                fontSize = 12.sp,
                color = if (state.storageSafe) HColors.Green else WarnOrange,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        BlockCard(Modifier.weight(1f)) {
            CardTitle("日志（P/S 码）")
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                items(state.logLines) { line ->
                    Text(
                        line,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp,
                        color = HColors.TextPrimary,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DiagButton("重连", HColors.Primary, Color.White, Modifier.weight(1f)) { callbacks.onReconnect() }
                DiagButton("测试发送", HColors.Primary, Color.White, Modifier.weight(1f)) { callbacks.onSendTest() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DiagButton("清除", HColors.Card, HColors.TextPrimary, Modifier.weight(1f)) { callbacks.onClear() }
                DiagButton("数据目录", HColors.Card, HColors.TextPrimary, Modifier.weight(1f)) { callbacks.onPickFolder() }
                DiagButton("重扫目录", HColors.Card, HColors.TextPrimary, Modifier.weight(1f)) { callbacks.onReload() }
            }
        }
    }
}

@Composable
private fun DiagButton(
    text: String,
    bg: Color,
    fg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = bg,
        shadowElevation = if (bg == HColors.Card) 1.dp else 0.dp,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fg)
        }
    }
}
