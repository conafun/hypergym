import interconnect from '@system.interconnect'

var conn = null
var connected = false
var onStateChange = null
var onMessage = null
var onEvent = null

function fireEvent(evt, data) {
  if (onEvent) onEvent(evt, data)
}

function init(opts) {
  onStateChange = (opts && opts.onStateChange) || null
  onMessage = (opts && opts.onMessage) || null
  onEvent = (opts && opts.onEvent) || null
  try {
    conn = interconnect.instance()
    fireEvent('instance', null)
  } catch (e) {
    console.log('[ic] instance error: ' + (e && e.message ? e.message : e))
    fireEvent('instance-error', e && e.message ? e.message : String(e))
    return false
  }
  conn.onopen = function (res) {
    connected = true
    console.log('[ic] onopen')
    fireEvent('open', res)
    if (onStateChange) onStateChange('connected')
  }
  conn.onclose = function (data) {
    connected = false
    console.log('[ic] onclose code=' + (data && data.code) + ' ' + (data && data.data))
    fireEvent('close', data)
    if (onStateChange) onStateChange('disconnected')
  }
  conn.onerror = function (data) {
    connected = false
    console.log('[ic] onerror code=' + (data && data.code) + ' ' + (data && data.data))
    fireEvent('error', data)
    if (onStateChange) onStateChange('error')
  }
  conn.onmessage = function (data) {
    console.log('[ic] onmessage: ' + (data && data.data))
    fireEvent('message', data)
    if (onMessage) onMessage(data && data.data)
  }
  return true
}

function send(data, callback) {
  if (!conn) {
    if (callback) callback(false, 'not initialized')
    return
  }
  conn.send({
    data: data,
    success: function () {
      console.log('[ic] send ok')
      if (callback) callback(true)
    },
    fail: function (errData, code) {
      console.log('[ic] send fail code=' + code + ' ' + errData)
      if (callback) callback(false, code)
    }
  })
}

function getState(callback) {
  if (!conn) {
    if (callback) callback('no-conn')
    return
  }
  conn.getReadyState({
    success: function (res) {
      if (callback) callback(null, res && res.status)
    },
    fail: function (data, code) {
      if (callback) callback(code, data)
    }
  })
}

function diagnosis(callback) {
  if (!conn) {
    if (callback) callback('no-conn')
    return
  }
  try {
    conn.diagnosis({
      success: function (res) {
        if (callback) callback(null, res && res.status)
      },
      fail: function (data, code) {
        if (callback) callback(code, data)
      }
    })
  } catch (e) {
    if (callback) callback('exception')
  }
}

function isConnected() {
  return connected
}

function getConn() {
  return conn
}

export default {
  init: init,
  send: send,
  getState: getState,
  diagnosis: diagnosis,
  isConnected: isConnected,
  getConn: getConn
}
