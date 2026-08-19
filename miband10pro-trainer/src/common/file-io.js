import file from '@system.file'

var STORAGE_PATH = 'internal://files/training_data.json'

function readRecords(callback) {
  file.readText({
    uri: STORAGE_PATH,
    success: function(res) {
      var data = {}
      try { data = JSON.parse(res.text).records || {} }
      catch (e) {}
      callback(data)
    },
    fail: function() { callback({}) }
  })
}

function saveRecords(records, callback) {
  var data = { app: 'strength-trainer', version: 1, records: records || {} }
  file.writeText({
    uri: STORAGE_PATH,
    text: JSON.stringify(data),
    success: function() { if (callback) callback(true) },
    fail: function() { if (callback) callback(false) }
  })
}

export default { readRecords: readRecords, saveRecords: saveRecords }
