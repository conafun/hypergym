package com.hypergym

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.hypergym.data.RecordStore
import com.hypergym.ui.AppCallbacks
import com.hypergym.ui.HyperGymApp
import com.hypergym.ui.UiState
import com.xiaomi.xms.wearable.Wearable
import com.xiaomi.xms.wearable.auth.AuthApi
import com.xiaomi.xms.wearable.auth.Permission
import com.xiaomi.xms.wearable.message.MessageApi
import com.xiaomi.xms.wearable.message.OnMessageReceivedListener
import com.xiaomi.xms.wearable.node.Node
import com.xiaomi.xms.wearable.node.NodeApi
import com.xiaomi.xms.wearable.service.OnServiceConnectionListener
import com.xiaomi.xms.wearable.service.ServiceApi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 手环联通入口：全部 xms-wearable 逻辑在此（P01-P17 调试码不变），
 * UI 状态写入 UiState，由 Compose 渲染。
 */
class MainActivity : ComponentActivity() {

    private val ui = UiState()
    private lateinit var recordStore: RecordStore
    private var lastKnownDays = -1

    private var nodeApi: NodeApi? = null
    private var messageApi: MessageApi? = null
    private var authApi: AuthApi? = null
    private var serviceApi: ServiceApi? = null
    private var curNode: Node? = null
    private var listenerRegistered = false

    private val messageListener = OnMessageReceivedListener { nodeId, message ->
        val text = String(message)
        runOnUiThread {
            addLog("P12 收到手环消息(${message.size}字节): " + text.take(80))
            ui.receivedLines.add(0, text)
            while (ui.receivedLines.size > 50) ui.receivedLines.removeAt(ui.receivedLines.size - 1)
            ui.status = "已连接: $nodeId | 收到 ${ui.receivedLines.size} 条"
            // Auto-reply to band ping: proves both directions
            if (text.contains("\"type\":\"ping\"")) {
                replyPing(nodeId)
            }
            // Training records: parse → dedup by date → append to shard file (30 lines/file)
            if (text.contains("training-records")) {
                recordStore.ingest(text) { r ->
                    runOnUiThread {
                        if (r.ok) {
                            addLog("S05 已落盘 date=${r.date} | ${r.reason} | 共${r.dayCount}天 ${r.fileCount}个文件(${r.totalLines}行)")
                        } else {
                            addLog("S06 ${r.reason}")
                        }
                        refreshDays()
                        updateStorageUi()
                    }
                }
            }
        }
    }

    private fun addLog(msg: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        ui.logLines.add(0, "[$ts] $msg")
        while (ui.logLines.size > 200) ui.logLines.removeAt(ui.logLines.size - 1)
    }

    private fun refreshDays() {
        ui.days = recordStore.snapshotDays()
    }

    private fun updateStorageUi() {
        val count = recordStore.dayCount()
        val mode = recordStore.backendDesc()
        ui.storage = "存储: $mode | 已存 $count 天 · ${recordStore.fileCount()} 个文件"
        ui.storageSafe = !recordStore.isInternalMode()
    }

    /** 重扫数据目录（App 回前台/手动按钮触发）：文件夹里所有 .jsonl 立即并入显示，无需手环重传 */
    private fun reloadFromDisk(forceLog: Boolean) {
        recordStore.reload { days, files ->
            runOnUiThread {
                if (forceLog || days != lastKnownDays) {
                    lastKnownDays = days
                    addLog("S02r 目录重扫: $days 天 / $files 个文件")
                }
                refreshDays()
                updateStorageUi()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 测试期保持屏幕常亮，防止 HyperOS 挂起/杀掉前台 App
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        nodeApi = Wearable.getNodeApi(applicationContext)
        messageApi = Wearable.getMessageApi(applicationContext)
        authApi = Wearable.getAuthApi(applicationContext)
        serviceApi = Wearable.getServiceApi(applicationContext)
        recordStore = RecordStore.get(this)

        setContent {
            HyperGymApp(
                state = ui,
                callbacks = AppCallbacks(
                    onClear = { ui.receivedLines.clear() },
                    onReconnect = { fullFlow() },
                    onSendTest = { sendTest() },
                    onPickFolder = { pickFolder() },
                    onReload = { reloadFromDisk(forceLog = true) },
                    onDeleteDay = { date ->
                        recordStore.deleteByDate(date) { ok, days, files ->
                            runOnUiThread {
                                addLog(
                                    if (ok) "S08 已删除 $date · 剩 $days 天 / $files 个文件"
                                    else "S08 删除 $date 失败（磁盘未变更）"
                                )
                                refreshDays()
                                updateStorageUi()
                            }
                        }
                    },
                ),
            )
        }

        addLog("P01 SDK初始化完成")
        addLog("P02 注册服务连接监听...")
        // Check wearable service API level - decisive for protocol support
        serviceApi?.getServiceApiLevel()
            ?.addOnSuccessListener { level ->
                runOnUiThread { addLog("P02b 服务API级别=" + level) }
            }
            ?.addOnFailureListener { e ->
                runOnUiThread { addLog("P02b 服务API级别查询失败: " + e.message) }
            }
        serviceApi?.registerServiceConnectionListener(object : OnServiceConnectionListener {
            override fun onServiceConnected() {
                runOnUiThread {
                    addLog("P03 服务已连接")
                    ui.status = "服务已连接, 正在获取设备..."
                }
                fullFlow()
            }
            override fun onServiceDisconnected() {
                runOnUiThread {
                    addLog("P04 服务断开!")
                    ui.status = "服务连接断开, 点重连"
                    listenerRegistered = false
                }
            }
        })

        // 训练记录存储：SAF 目录为主（卸载重装不丢），内部存储兜底。
        // 启动即扫描目录内全部 .jsonl 分片并重建索引 → 数据页直接显示，无需手环重传。
        recordStore.init { desc, count, fileCount, warn ->
            runOnUiThread {
                addLog("S01 存储后端: $desc")
                addLog("S02 已载入 $count 天训练记录（$fileCount 个文件）")
                warn?.let { addLog("S03 警告: $it") }
                lastKnownDays = count
                refreshDays()
                updateStorageUi()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                    recordStore.shouldAutoPrompt()
                ) {
                    recordStore.markPickerAsked()
                    addLog("S04 首次使用，请选择数据目录（如 Documents/HyperGym）...")
                    startActivityForResult(RecordStore.folderPickerIntent(), RecordStore.REQUEST_FOLDER)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 每次回到前台重扫数据目录：文件夹是数据源，外部新增/变更立即反映到展示页
        if (::recordStore.isInitialized) reloadFromDisk(forceLog = false)
    }

    private fun pickFolder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            addLog("S04 手动打开数据目录选择...")
            startActivityForResult(RecordStore.folderPickerIntent(), RecordStore.REQUEST_FOLDER)
        } else {
            addLog("S04 系统版本过低，不支持SAF目录（仅内部存储）")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, result: Intent?) {
        super.onActivityResult(requestCode, resultCode, result)
        if (requestCode == RecordStore.REQUEST_FOLDER) {
            recordStore.markPickerAsked()
            val uri = if (resultCode == RESULT_OK) result?.data else null
            recordStore.onFolderPicked(uri) { msg ->
                runOnUiThread {
                    addLog("S07 $msg")
                    refreshDays()
                    updateStorageUi()
                }
            }
        }
    }

    // Full flow: permissions → device → sdk permission → listener
    private fun fullFlow() {
        if (curNode == null || !listenerRegistered) {
            requestAndroidPermissions()
        } else {
            addLog("P10 监听已在运行(nodeId=${curNode?.id})")
            ui.status = "已连接: ${curNode?.id} | 等待接收数据..."
        }
    }

    private val PERMISSION_REQUEST_CODE = 1001

    private fun requestAndroidPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            if (checkSelfPermission("android.permission.ACCESS_FINE_LOCATION") != PackageManager.PERMISSION_GRANTED) {
                needed.add("android.permission.ACCESS_FINE_LOCATION")
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission("android.permission.BLUETOOTH_CONNECT") != PackageManager.PERMISSION_GRANTED) {
                needed.add("android.permission.BLUETOOTH_CONNECT")
            }
            if (checkSelfPermission("android.permission.BLUETOOTH_SCAN") != PackageManager.PERMISSION_GRANTED) {
                needed.add("android.permission.BLUETOOTH_SCAN")
            }
        }
        if (needed.isNotEmpty()) {
            addLog("P05 请求Android权限: ${needed.size}项")
            requestPermissions(needed.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            addLog("P05 Android权限OK")
            getConnectedDevice()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.count { it == PackageManager.PERMISSION_GRANTED }
        addLog("P05 权限结果 $granted/${permissions.size} 通过")
        getConnectedDevice()
    }

    private fun getConnectedDevice() {
        addLog("P06 查询已连接设备...")
        nodeApi?.connectedNodes?.addOnSuccessListener { nodes ->
            if (nodes.isNotEmpty()) {
                curNode = nodes[0]
                val nodeId = nodes[0].id
                addLog("P06 找到设备: $nodeId")
                ui.status = "设备: $nodeId | 申请SDK权限..."
                // Decisive check: does the wearable service know our band app?
                nodeApi?.isWearAppInstalled(nodeId)
                    ?.addOnSuccessListener { installed ->
                        runOnUiThread {
                            addLog(if (installed) "P17 手环端应用已注册: true"
                                   else "P17 手环端应用已注册: FALSE! 服务不认识手环App")
                        }
                    }
                    ?.addOnFailureListener { e ->
                        runOnUiThread { addLog("P17 查询手环应用失败: " + e.message) }
                    }
                requestSdkPermission()
            } else {
                addLog("P07 未找到设备! 请确认手环已连接运动健康")
                ui.status = "P07 未找到设备"
            }
        }?.addOnFailureListener {
            addLog("P07 查询设备失败: ${it.message}")
            ui.status = "获取设备失败: ${it.message}"
        }
    }

    private fun requestSdkPermission() {
        val node = curNode ?: return
        addLog("P08 申请SDK权限(DEVICE_MANAGER+NOTIFY)...")
        authApi?.requestPermission(node.id, Permission.DEVICE_MANAGER, Permission.NOTIFY)
            ?.addOnSuccessListener { permissions ->
                val granted = permissions.joinToString { it.name }
                addLog("P08 SDK权限授予: $granted")
                registerListener()
            }?.addOnFailureListener {
                addLog("P09 SDK权限失败: ${it.message}")
                registerListener()
            }
    }

    private fun registerListener() {
        val node = curNode ?: return
        addLog("P10 注册消息监听(nodeId=${node.id})...")
        messageApi?.addListener(node.id, messageListener)
            ?.addOnSuccessListener {
                listenerRegistered = true
                addLog("P10 监听注册成功, 等待手环数据...")
                ui.status = "已连接: ${node.id} | 等待接收数据..."
            }?.addOnFailureListener {
                addLog("P11 监听注册失败: ${it.message}")
                ui.status = "注册监听失败: ${it.message}"
            }
    }

    // Auto-reply to band ping
    private fun replyPing(nodeId: String) {
        val reply = "{\"type\":\"ping-reply\",\"t\":${System.currentTimeMillis()},\"src\":\"phone\"}"
        messageApi?.sendMessage(nodeId, reply.toByteArray())
            ?.addOnSuccessListener { addLog("P13 已回包给手环") }
            ?.addOnFailureListener { addLog("P14 回包失败: ${it.message}") }
    }

    // Manual test: phone → band direction
    private fun sendTest() {
        val node = curNode ?: run {
            addLog("P15 无设备, 无法测试发送")
            ui.status = "无设备"
            return
        }
        val msg = "{\"type\":\"ping-reply\",\"t\":${System.currentTimeMillis()},\"src\":\"phone-manual\"}"
        messageApi?.sendMessage(node.id, msg.toByteArray())
            ?.addOnSuccessListener { addLog("P15 测试发送成功(看手环B09/B10)") }
            ?.addOnFailureListener { addLog("P16 测试发送失败: ${it.message}") }
    }
}
