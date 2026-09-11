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

function readRecords(callback) {
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
        callback({})
        return
      }
      callback((parsed && parsed.records) || {})
    },
    fail: function() {
      // 文件不存在（首次使用）或读取失败：按空库处理。
      // 这里不置 readBroken —— 读取失败时后续保存仍会尝试写入，
      // 而写入本身若失败会走 callback(false)，不会造成静默覆盖。
      callback({})
    }
  })
}

function saveRecords(records, callback) {
  if (readBroken) {
    console.log('[file-io] 上次读取解析失败，拒绝保存以免覆盖原文件')
    if (callback) callback(false)
    return
  }
  var data = { app: 'strength-trainer', version: 1, records: records || {} }
  file.writeText({
    uri: STORAGE_PATH,
    text: JSON.stringify(data),
    success: function() { if (callback) callback(true) },
    fail: function() { if (callback) callback(false) }
  })
}

export default { readRecords: readRecords, saveRecords: saveRecords }
