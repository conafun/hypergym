package com.hypergym.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream

/** 每个分片文件最多容纳多少条记录（= 多少行），写满后自动开下一个分片 */
const val MAX_LINES_PER_SHARD = 30

/** 旧版单文件（v2.1 及以前版本写的），读取兼容用 */
const val LEGACY_FILE_NAME = "records.jsonl"

const val SHARD_PREFIX = "records-"
const val SHARD_SUFFIX = ".jsonl"

/**
 * 数据文件描述。number=0 表示旧版 records.jsonl 或其他 .jsonl 文件（只读合并，不追加）。
 */
data class Shard(val name: String, val number: Int)

/** 编号 → 文件名，如 3 → "records-0003.jsonl" */
fun shardNameOf(number: Int): String =
    SHARD_PREFIX + number.toString().padStart(4, '0') + SHARD_SUFFIX

/** 是否我们自己生成的分片名（records-NNNN.jsonl），只有这类文件允许删除 */
fun isOwnShard(name: String): Boolean =
    name.startsWith(SHARD_PREFIX) && name.endsWith(SHARD_SUFFIX) &&
        name.removePrefix(SHARD_PREFIX).removeSuffix(SHARD_SUFFIX).toIntOrNull() != null

/**
 * 是否本应用写入的数据文件：分片 records-NNNN.jsonl，或旧版 records.jsonl，
 * 或同名残留（如 records (1).jsonl）。这些文件在合并重写后允许清理；
 * 目录里其他 .jsonl 只读不动。
 */
fun isRecordsJsonl(name: String): Boolean =
    name.startsWith("records") && name.endsWith(SHARD_SUFFIX)

/**
 * 存储后端抽象：目录内多个 JSONL 分片文件（每行一条 JSON 记录），
 * 每个分片最多 [MAX_LINES_PER_SHARD] 行，写满后另起新分片。
 *
 * 读取时目录里**所有** .jsonl 文件都会被扫描合并（含旧版 records.jsonl，
 * 甚至其他同名残留文件），因此「文件在数据就在」：重装 App 重新选目录后，
 * 全部历史数据立即恢复显示，无需手环重新发送。
 *
 * 两种实现：
 *  - InternalBackend: App 内部存储（卸载即丢，仅作兜底/临时）
 *  - SafBackend:      SAF 目录（用户授权持久化，卸载重装后文件仍在）★ 主存储
 */
interface DataBackend {
    /** 人类可读描述（日志/界面展示） */
    fun describe(): String

    /** 枚举目录里所有 .jsonl 数据文件（含旧版单文件），分片按编号升序 */
    fun listDataFiles(): List<Shard>

    /** 读回某文件全部非空行（保序） */
    fun readLines(name: String): List<String>

    /** 新建编号分片并写入 lines（可为空表），返回文件名 */
    fun createShard(number: Int, lines: List<String>): String

    /** 追加一行到指定文件（自动补换行）并 fsync 落盘 */
    fun appendLine(name: String, line: String)

    /** 删除指定文件（仅允许删本应用写入的 records*.jsonl 文件，其他文件不动） */
    fun deleteFile(name: String): Boolean
}

class InternalBackend(private val dir: File) : DataBackend {

    override fun describe(): String = "内部存储(${dir.absolutePath})"

    private fun fileFor(name: String): File = File(dir, name)

    private fun numberOrZero(name: String): Int =
        if (isOwnShard(name)) {
            name.removePrefix(SHARD_PREFIX).removeSuffix(SHARD_SUFFIX).toInt()
        } else {
            0
        }

    override fun listDataFiles(): List<Shard> {
        val files = dir.listFiles() ?: return emptyList()
        return files.asSequence()
            .filter { it.isFile && it.name.endsWith(SHARD_SUFFIX) }
            .map { Shard(it.name, numberOrZero(it.name)) }
            .sortedWith(compareBy({ it.number }, { it.name }))
            .toList()
    }

    override fun readLines(name: String): List<String> {
        val f = fileFor(name)
        if (!f.exists()) return emptyList()
        return try {
            String(f.readBytes(), Charsets.UTF_8)
                .lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    override fun createShard(number: Int, lines: List<String>): String {
        val name = shardNameOf(number)
        dir.mkdirs()
        FileOutputStream(fileFor(name), false).use { fos ->
            for (l in lines) fos.write((l + "\n").toByteArray(Charsets.UTF_8))
            fos.fd.sync()
        }
        return name
    }

    override fun appendLine(name: String, line: String) {
        dir.mkdirs()
        FileOutputStream(fileFor(name), true).use { fos ->
            fos.write((line + "\n").toByteArray(Charsets.UTF_8))
            fos.fd.sync()
        }
    }

    override fun deleteFile(name: String): Boolean {
        if (!isRecordsJsonl(name)) return false
        val f = fileFor(name)
        return !f.exists() || f.delete()
    }
}

class SafBackend(private val context: Context, treeUri: Uri) : DataBackend {

    private val root: DocumentFile? = DocumentFile.fromTreeUri(context, treeUri)

    fun available(): Boolean = root != null && root.exists() && root.canWrite()

    override fun describe(): String = "SAF目录(${root?.name ?: "?"})"

    override fun listDataFiles(): List<Shard> {
        val r = root ?: return emptyList()
        val out = mutableListOf<Shard>()
        for (f in r.listFiles()) {
            val n = f.name ?: continue
            if (!f.isFile || !n.endsWith(SHARD_SUFFIX)) continue
            val num = if (isOwnShard(n)) {
                n.removePrefix(SHARD_PREFIX).removeSuffix(SHARD_SUFFIX).toInt()
            } else {
                0
            }
            out.add(Shard(n, num))
        }
        return out.sortedWith(compareBy({ it.number }, { it.name }))
    }

    private fun findFile(name: String, createIfMissing: Boolean): DocumentFile? {
        if (root == null) return null
        root.findFile(name)?.let { return it }
        if (!createIfMissing) return null
        return root.createFile("application/octet-stream", name)
            ?: root.createFile("text/plain", name)
    }

    override fun readLines(name: String): List<String> {
        val doc = findFile(name, createIfMissing = false) ?: return emptyList()
        return try {
            context.contentResolver.openInputStream(doc.uri)?.use { ins ->
                String(ins.readBytes(), Charsets.UTF_8)
                    .lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            } ?: emptyList()
        } catch (t: Throwable) {
            emptyList()
        }
    }

    override fun createShard(number: Int, lines: List<String>): String {
        val name = shardNameOf(number)
        val doc = findFile(name, createIfMissing = true) ?: return name
        // "wt" = write + truncate
        val pfd = context.contentResolver.openFileDescriptor(doc.uri, "wt") ?: return name
        FileOutputStream(pfd.fileDescriptor).use { fos ->
            for (l in lines) fos.write((l + "\n").toByteArray(Charsets.UTF_8))
            pfd.fileDescriptor.sync()
        }
        return name
    }

    override fun appendLine(name: String, line: String) {
        val doc = findFile(name, createIfMissing = true) ?: return
        // "wa" = write + append，写入后 fsync 保证断电不丢
        val pfd = context.contentResolver.openFileDescriptor(doc.uri, "wa") ?: return
        FileOutputStream(pfd.fileDescriptor).use { fos ->
            fos.write((line + "\n").toByteArray(Charsets.UTF_8))
            pfd.fileDescriptor.sync()
        }
    }

    override fun deleteFile(name: String): Boolean {
        if (!isRecordsJsonl(name)) return false
        val doc = findFile(name, createIfMissing = false) ?: return true
        return try {
            doc.delete()
        } catch (t: Throwable) {
            false
        }
    }
}
