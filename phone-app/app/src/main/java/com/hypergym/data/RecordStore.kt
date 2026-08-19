package com.hypergym.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import java.util.concurrent.Executors

/**
 * 训练记录存储层（单例）。
 *
 * 设计目标：文件在数据就在 —— 卸载重装手机 App 后数据不丢。
 *
 * 策略：
 *  1. 主存储 = SAF 目录（用户首次选择一次文件夹，如 Documents/HyperGym，授权持久化）
 *  2. 兜底   = App 内部存储（未选目录/授权失效时使用，卸载即丢，UI 黄色警告）
 *  3. 分片   = 目录内多个 records-NNNN.jsonl 文件，每个最多 30 行（30 天记录），
 *              写满自动开下一个分片；不是一条数据一个文件
 *  4. 格式   = JSONL（每行一条 JSON），append-only + fsync，绝不整文件重写（除压缩）
 *  5. 去重   = 以 date 为主键，同日期后到覆盖先到（内存索引）
 *  6. 启动/回前台 = 扫描目录全部 .jsonl 文件重建索引（含旧版 records.jsonl），
 *              展示页直接显示全部数据，无需手环重新发送
 *  7. 线程   = 单线程队列串行化所有磁盘操作
 */
data class IngestResult(
    val ok: Boolean,
    val date: String?,
    val reason: String,
    val dayCount: Int,
    val fileCount: Int,
    val totalLines: Int,
    val backendDesc: String
)

class RecordStore private constructor(private val context: Context) {

    companion object {
        private const val PREFS = "hypergym_store"
        private const val KEY_TREE_URI = "tree_uri"
        private const val KEY_PICKER_ASKED = "picker_asked"
        /** 全部分片总行数超过此值时压缩重写（按 date 去重后重新按 30 行/片分片） */
        private const val COMPACT_WHEN_MORE_THAN = 1200

        const val REQUEST_FOLDER = 2001

        @Volatile
        private var inst: RecordStore? = null

        fun get(context: Context): RecordStore =
            inst ?: synchronized(this) {
                inst ?: RecordStore(context.applicationContext).also { inst = it }
            }

        fun folderPickerIntent(): Intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "record-store").apply { isDaemon = true }
    }
    /** date → 记录（主键去重）；只在 executor 线程写，读需同步 */
    private val days = LinkedHashMap<String, TrainingDay>()
    /** 每个数据文件的行数；只在 executor 线程写，读需同步 */
    private val shardCounts = mutableMapOf<String, Int>()
    /** 默认内部存储兜底，init() 时若 SAF 可用则切换 */
    private var backend: DataBackend? = InternalBackend(File(context.filesDir, "hypergym"))
    /** 当前正在追加的分片名（满 30 行后换下一个） */
    private var currentShard: String? = null
    private var nextNumber = 1
    private var totalLines = 0

    // ---------------- 初始化 ----------------

    /** 回调在后台线程，调用方需自行切 UI 线程 */
    fun init(onDone: (backendDesc: String, dayCount: Int, fileCount: Int, warn: String?) -> Unit) {
        executor.submit {
            var warn: String? = "未选数据目录，当前用内部存储（卸载重装会丢，建议点「数据目录」）"
            val tree = persistedTreeUri()
            if (tree != null) {
                val saf = SafBackend(context, tree)
                if (saf.available()) {
                    backend = saf
                    warn = null
                } else {
                    warn = "已保存的目录不可用（${saf.describe()}），回退内部存储"
                }
            }
            val b = backend ?: InternalBackend(File(context.filesDir, "hypergym"))
            // 内部模式：旧文件直接搬进内部分片目录；SAF 模式：先拿回旧行，等索引载入后合并
            val legacyLines = migrateLegacyInternalFiles()
            loadAllIntoIndex()
            if (legacyLines != null) {
                // SAF 模式：旧行按 date 补进索引（不覆盖 SAF 已有数据），统一重写进 SAF 分片
                synchronized(days) {
                    for (l in legacyLines) {
                        TrainingParser.parse(l)?.let { d ->
                            if (!days.containsKey(d.date)) days[d.date] = d
                        }
                    }
                }
                if (legacyLines.any { TrainingParser.parse(it) != null } && persistAll()) {
                    deleteLegacyInternalFiles()
                }
            }
            // v2.1 升级：SAF 目录里若还有旧版/残留 records*.jsonl 单文件，合并进新分片并清理
            if (b is SafBackend && b.listDataFiles().any { it.number == 0 && isRecordsJsonl(it.name) }) {
                persistAll()
            }
            if (totalLines > COMPACT_WHEN_MORE_THAN) persistAll()
            onDone(b.describe(), dayCount(), fileCount(), warn)
        }
    }

    /**
     * 一次性迁移：v2.1 及以前内部兜底把数据写在 filesDir 根目录的 *.jsonl
     * （records.jsonl 等），新版本内部目录改为 filesDir/hypergym/。
     * - 内部模式：旧行搬进内部分片目录并删除旧文件，返回 null；
     * - SAF 模式：返回旧行（不删除），由调用方合并进 SAF 分片成功后再删。
     */
    private fun migrateLegacyInternalFiles(): List<String>? {
        val legacy = try {
            context.filesDir.listFiles()?.filter { it.isFile && it.name.endsWith(SHARD_SUFFIX) }
        } catch (t: Throwable) {
            null
        } ?: return null
        if (legacy.isEmpty()) return null
        val lines = legacy.flatMap { f ->
            try {
                String(f.readBytes(), Charsets.UTF_8)
                    .lines().map { it.trim() }.filter { it.isNotEmpty() }
            } catch (t: Throwable) {
                emptyList()
            }
        }
        if (lines.isEmpty()) return null
        val b = backend
        if (b is InternalBackend) {
            try {
                val files = b.listDataFiles()
                var num = 1
                while (files.any { it.number == num }) num++
                b.createShard(num, lines)
                deleteLegacyInternalFiles()
            } catch (t: Throwable) {
                // 迁移失败不影响主流程
            }
            return null
        }
        return lines
    }

    private fun deleteLegacyInternalFiles() {
        val legacy = try {
            context.filesDir.listFiles()?.filter { it.isFile && it.name.endsWith(SHARD_SUFFIX) }
        } catch (t: Throwable) {
            null
        } ?: return
        for (f in legacy) {
            try {
                f.delete()
            } catch (t: Throwable) {
                // 删不掉就留着，下次启动重复迁移（date 去重保证无副作用）
            }
        }
    }

    /** 扫描目录全部 .jsonl 文件重建内存索引（启动/回前台/切目录后调用）。仅 executor 线程。 */
    private fun loadAllIntoIndex() {
        val b = backend ?: return
        val files = try {
            b.listDataFiles()
        } catch (t: Throwable) {
            emptyList()
        }
        val newCounts = mutableMapOf<String, Int>()
        val newDays = LinkedHashMap<String, TrainingDay>()
        var maxNum = 0
        var lines = 0
        for (f in files) {
            val ls = try {
                b.readLines(f.name)
            } catch (t: Throwable) {
                emptyList()
            }
            newCounts[f.name] = ls.size
            lines += ls.size
            for (l in ls) {
                TrainingParser.parse(l)?.let { newDays[it.date] = it } // 同日期后行覆盖前行
            }
            if (f.number > maxNum) maxNum = f.number
        }
        synchronized(days) {
            days.clear()
            days.putAll(newDays)
        }
        synchronized(shardCounts) {
            shardCounts.clear()
            shardCounts.putAll(newCounts)
        }
        totalLines = lines
        nextNumber = maxNum + 1
        currentShard = null
    }

    /** 回前台/手动触发时重扫目录：外部新出现的文件（重装前留下的等）也立即并入显示。回调在后台线程。 */
    fun reload(onDone: (dayCount: Int, fileCount: Int) -> Unit) {
        executor.submit {
            loadAllIntoIndex()
            onDone(dayCount(), fileCount())
        }
    }

    // ---------------- SAF 目录选择 ----------------

    private fun persistedTreeUri(): Uri? {
        val s = prefs.getString(KEY_TREE_URI, null) ?: return null
        return try {
            Uri.parse(s)
        } catch (t: Throwable) {
            null
        }
    }

    /** 首次启动且从未选过目录时自动弹一次选择器 */
    fun shouldAutoPrompt(): Boolean =
        persistedTreeUri() == null && !prefs.getBoolean(KEY_PICKER_ASKED, false)

    fun markPickerAsked() {
        prefs.edit().putBoolean(KEY_PICKER_ASKED, true).apply()
    }

    /** 用户选完文件夹（uri=null 表示取消）。回调在后台线程。 */
    fun onFolderPicked(uri: Uri?, onDone: (String) -> Unit) {
        executor.submit {
            val msg: String
            if (uri == null) {
                msg = "未选择目录，保持当前存储模式"
            } else {
                msg = try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    prefs.edit().putString(KEY_TREE_URI, uri.toString()).apply()
                    val saf = SafBackend(context, uri)
                    if (saf.available()) {
                        // 当前(旧目录/内部)数据 与 新目录既有数据 合并：新目录打底，当前数据同日期覆盖
                        val oldLines = readAllLinesOf(backend)
                        val newExisting = readAllLinesOf(saf)
                        backend = saf
                        synchronized(days) {
                            days.clear()
                            for (l in newExisting) {
                                TrainingParser.parse(l)?.let { days[it.date] = it }
                            }
                            for (l in oldLines) {
                                TrainingParser.parse(l)?.let { days[it.date] = it }
                            }
                        }
                        persistAll()
                        "目录已切换: ${saf.describe()}（合并新目录 ${newExisting.size} 行 + 当前 ${oldLines.size} 行 → ${fileCount()} 个分片）"
                    } else {
                        "所选目录不可写，保持当前存储模式"
                    }
                } catch (t: Throwable) {
                    "保存目录授权失败: ${t.message}"
                }
            }
            onDone(msg)
        }
    }

    private fun readAllLinesOf(b: DataBackend?): List<String> {
        if (b == null) return emptyList()
        val out = mutableListOf<String>()
        for (f in b.listDataFiles()) {
            try {
                out.addAll(b.readLines(f.name))
            } catch (t: Throwable) {
                // 单文件读取失败不影响其他文件
            }
        }
        return out
    }

    // ---------------- 写入 ----------------

    /** 收到一条手环消息后调用：解析 → 内存去重 → 追加落盘（满30行自动开新分片）。回调在后台线程。 */
    fun ingest(jsonText: String, onResult: (IngestResult) -> Unit) {
        executor.submit {
            val b = backend
            if (b == null) {
                onResult(IngestResult(false, null, "存储未初始化", 0, 0, 0, "?"))
                return@submit
            }
            val day = TrainingParser.parse(jsonText)
            if (day == null) {
                onResult(
                    IngestResult(false, null, "非训练记录或解析失败，未落盘",
                        dayCount(), fileCount(), totalLines, b.describe())
                )
                return@submit
            }
            val isNewDate: Boolean
            synchronized(days) {
                isNewDate = !days.containsKey(day.date)
                days[day.date] = day
            }
            var rolled = ""
            try {
                val name = currentShard
                if (name == null || (shardCounts[name] ?: 0) >= MAX_LINES_PER_SHARD) {
                    // 当前分片不存在或已满 30 行：开新分片（编号避开目录里已存在的）
                    val files = b.listDataFiles()
                    var num = nextNumber
                    while (files.any { it.number == num }) num++
                    val newName = b.createShard(num, emptyList())
                    synchronized(shardCounts) {
                        shardCounts[newName] = 0
                    }
                    currentShard = newName
                    nextNumber = num + 1
                    rolled = " 新建分片$newName"
                }
                val target = currentShard!!
                b.appendLine(target, day.rawJson)
                synchronized(shardCounts) {
                    shardCounts[target] = (shardCounts[target] ?: 0) + 1
                }
                totalLines++
            } catch (t: Throwable) {
                onResult(
                    IngestResult(false, day.date, "落盘失败: ${t.message}",
                        dayCount(), fileCount(), totalLines, b.describe())
                )
                return@submit
            }
            var reason = if (isNewDate) "新增日期" else "覆盖同日期"
            reason += rolled
            if (totalLines > COMPACT_WHEN_MORE_THAN) {
                val beforeDays = dayCount()
                persistAll()
                reason += "，压缩重写(${beforeDays}天→${fileCount()}个分片)"
            }
            onResult(
                IngestResult(true, day.date, reason, dayCount(), fileCount(), totalLines, b.describe())
            )
        }
    }

    /** 全量重写（内存索引 → 30行/片分片），用于压缩去重与切目录合并。返回是否成功。 */
    private fun persistAll(): Boolean {
        val b = backend ?: return false
        val existing = try {
            b.listDataFiles()
        } catch (t: Throwable) {
            return false
        }
        val firstNew = (existing.maxOfOrNull { it.number } ?: 0) + 1
        val lines: List<String>
        synchronized(days) {
            lines = days.values.map { it.rawJson }
        }
        var num = firstNew
        try {
            for (chunk in lines.chunked(MAX_LINES_PER_SHARD)) {
                while (existing.any { it.number == num }) num++
                b.createShard(num, chunk)
                num++
            }
        } catch (t: Throwable) {
            // 重写失败不影响主流程：旧文件还在，下次超限再试
            return false
        }
        // 新分片全部写成功后，删除旧文件：records-NNNN.jsonl 旧分片 + 旧版/残留 records*.jsonl 单文件
        // （目录里其他 .jsonl 保留只读，日期去重保证数据不受影响）
        for (f in existing) {
            if ((f.number in 1 until firstNew) || isRecordsJsonl(f.name)) {
                try {
                    b.deleteFile(f.name)
                } catch (t: Throwable) {
                    // 删不掉就留着，日期去重保证数据不受影响
                }
            }
        }
        loadAllIntoIndex()
        return true
    }

    // ---------------- 删除 ----------------

    /**
     * 删除指定日期（date 为主键）的训练记录：内存索引移除 → 全量重写分片落盘。
     * 重写后该日期的 JSON 行即从数据文件中消失，数据/肌群/日记各页通过快照联动更新。
     * 落盘失败时把内存里刚删的日期补回，保证内存与磁盘一致。回调在后台线程。
     */
    fun deleteByDate(date: String, onDone: (ok: Boolean, dayCount: Int, fileCount: Int) -> Unit) {
        executor.submit {
            val removed: TrainingDay?
            synchronized(days) {
                removed = days.remove(date)
            }
            var ok: Boolean
            if (removed == null) {
                ok = true // 本来就不存在，无需落盘
            } else {
                ok = persistAll()
                if (!ok) {
                    synchronized(days) {
                        days[date] = removed
                    }
                }
            }
            onDone(ok, dayCount(), fileCount())
        }
    }

    // ---------------- UI 读取 ----------------

    fun dayCount(): Int = synchronized(days) { days.size }
    fun fileCount(): Int = synchronized(shardCounts) { shardCounts.size }
    fun backendDesc(): String = backend?.describe() ?: "未初始化"
    fun isInternalMode(): Boolean = backend is InternalBackend
    fun snapshotDays(): List<TrainingDay> = synchronized(days) { days.values.toList() }
}
