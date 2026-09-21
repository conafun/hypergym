import file from '@system.file'

var STORAGE_PATH = 'internal://files/training_data.json'

/**
 * 「文件读到了但解析不了」的保护位。
 *
 * 背景：保存是**全量覆盖写**（writeText 覆盖整个文件）。原来的实现在解析失败时
 * 用空 catch 吞掉异常、把结果当成「没有记录」，于是内存里变成空库；用户下一次
 * 正常点「结束」，就把磁盘上那份（可能只是坏了一部分、仍然可以抢救的）数据
 * 整份覆盖成「内容为空」的完好文件 —— 数据就此永久丢失。
 *
 * 所以这里做两件事：
 *  1. 解析失败时明确记录 `readBroken = true`（并打日志），不再假装是空库；
 *  2. 只要 `readBroken` 为真，**拒绝任何保存**。宁可这一次保存失败（界面会提示
 *     「保存失败」），也绝不覆盖原文件。重启应用后该标志自然复位。
 *
 * 注意：「文件不存在」（首次使用）不算损坏 —— 那种情况返回空库是正确的。
 */
var readBroken = false

/**
 * 内存里已知的 PR 表（动作名 → 手机下发的 PR 重量）。
 *
 * 它只是**兜底缓存**：`saveData` 在调用方没带 `prs` 时用它，避免某个只关心训练记录的
 * 保存点不小心把磁盘上的 PR 表抹掉（这个文件是全量覆盖写）。
 */
var cachedPrs = null

/** 「PR 表为空」时返回空对象，调用方不必再判空 */
function normalizePrs(raw) {
  if (!raw || typeof raw !== 'object') return {}
  var out = {}
  var keys = Object.keys(raw)
  for (var i = 0; i < keys.length; i++) {
    var v = raw[keys[i]]
    if (typeof v === 'number' && isFinite(v) && v > 0) out[keys[i]] = v
  }
  return out
}

/** 读回训练记录 + PR 表；回调参数 { records, prs }，两者都保证是对象 */
function readData(callback) {
  file.readText({
    uri: STORAGE_PATH,
    success: function(res) {
      var parsed
      try {
        parsed = JSON.parse(res.text)
      } catch (e) {
        readBroken = true
        console.log('[file-io] 训练数据解析失败，已进入保护模式（本次运行内禁止保存）: ' +
          (e && e.message ? e.message : e))
        callback({ records: {}, prs: {} })
        return
      }
      cachedPrs = normalizePrs(parsed && parsed.prs)
      callback({
        records: (parsed && parsed.records) || {},
        prs: cachedPrs
      })
    },
    fail: function() {
      // 文件不存在（首次使用）或读取失败：按空库处理。
      // 这里不置 readBroken —— 读取失败时后续保存仍会尝试写入，
      // 而写入本身若失败会走 callback(false)，不会造成静默覆盖。
      callback({ records: {}, prs: {} })
    }
  })
}

/**
 * 全量写回训练记录 + PR 表。
 *
 * `version` 由 1 升到 2（新增 `prs` 字段）。老文件里没有 `prs`，读出来就是空表 ——
 * 这正好让「手环上没有 PR 数据」这种情况（升级前就存在的文件、或重装 rpk）自动成立，
 * 手机上每次都是全量下发，于是首屏自然就补齐了，不需要额外的判断代码。
 */
function saveData(state, callback) {
  if (readBroken) {
    console.log('[file-io] 上次读取解析失败，拒绝保存以免覆盖原文件')
    if (callback) callback(false)
    return
  }
  var records = (state && state.records) || {}
  // 调用方没带 prs 时用内存里的兜底，避免只关心记录的保存点把 PR 表抹掉
  var prs = state && state.prs ? normalizePrs(state.prs) : (cachedPrs || {})
  cachedPrs = prs
  var data = {
    app: 'strength-trainer',
    version: 2,
    records: records,
    prs: prs
  }
  file.writeText({
    uri: STORAGE_PATH,
    text: JSON.stringify(data),
    success: function() { if (callback) callback(true) },
    fail: function() { if (callback) callback(false) }
  })
}

export default { readData: readData, saveData: saveData }
