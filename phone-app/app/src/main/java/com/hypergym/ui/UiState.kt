package com.hypergym.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hypergym.data.TrainingDay

/**
 * App 级 UI 状态（由 MainActivity 持有并更新，Compose 订阅渲染）。
 * 手环联通逻辑仍全部在 MainActivity，这里只负责展示。
 */
class UiState {
    /** 连接状态（P 码流程文本） */
    var status by mutableStateOf("等待连接...")

    /** 存储后端描述 + 已存天数 */
    var storage by mutableStateOf("存储: 初始化中...")

    /** true=SAF 目录模式（安全），false=内部存储（卸载会丢） */
    var storageSafe by mutableStateOf(false)

    /** 已解析的训练日数据（数据页图表/列表用） */
    var days by mutableStateOf<List<TrainingDay>>(emptyList())

    /** 调试日志（最新在前） */
    val logLines = mutableStateListOf<String>()

    /** 最近收到的原始 JSON 消息（最新在前） */
    val receivedLines = mutableStateListOf<String>()
}

/** 传输页按钮的回调（由 MainActivity 提供实现） */
data class AppCallbacks(
    val onClear: () -> Unit,
    val onReconnect: () -> Unit,
    val onSendTest: () -> Unit,
    val onPickFolder: () -> Unit,
    val onReload: () -> Unit,
    val onDeleteDay: (String) -> Unit,
)
