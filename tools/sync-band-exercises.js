#!/usr/bin/env node
/**
 * 手环动作表 → 手机端同步工具
 * ============================================================================
 * 手环端 `miband10pro-trainer/src/common/data.js` 是**动作分类的唯一权威来源**：
 * 手环没有增删动作的 UI，`loadExercises()` 全项目也从未被调用，所以运行期动作表
 * 就等于 `DEFAULT_EXERCISES`。手机端记录的动作名全部来自手环 payload（手机没有
 * 手工录入入口），因此「动作 → 肌群」的判定必须与这张表逐条一致，否则同一个动作
 * 会在两端算成不同肌群（PR 卡片、肌群占比、数据透视都会跟着错）。
 *
 * 本脚本把 data.js 的动作表求值出来，生成手机端 Kotlin 常量：
 *     phone-app/app/src/main/java/com/hypergym/data/BandExercises.kt
 * 由 build-debug.ps1 在**每次手机端构建前自动执行**，所以以后只要改 data.js，
 * 手机 APK 就会跟着一起变，不需要手工同步。
 *
 * 为什么生成 Kotlin 而不是 assets JSON：
 *   生成 Kotlin 是编译期常量，分类不再需要 Context、不存在「没初始化就静默降级」的
 *   顺序依赖（旧实现里 MuscleMap.init 只在肌群页被调用，导致同一动作的分类取决于
 *   用户点过哪些页面）。同时手机端也不必再为分类去解析 941 KB 的英文动作库。
 *
 * 用法：
 *     node tools/sync-band-exercises.js            # 生成/更新（有变化时打印差异）
 *     node tools/sync-band-exercises.js --check    # 只校验是否同步，漂移则 exit 1
 *     node tools/sync-band-exercises.js --quiet    # 只在出错或有变化时输出
 *
 * 提取方式说明：直接对 data.js 源码求值，而不是用正则抠数组字面量。正则抠法在换行、
 * 尾逗号、注释、引号风格变化时都会悄悄产出错误结果；求值拿到的就是 Vela 运行期
 * 真正看到的那份数据。
 */
'use strict'

const fs = require('fs')
const path = require('path')
const vm = require('vm')

const ROOT = path.resolve(__dirname, '..')
const SRC = path.join(ROOT, 'miband10pro-trainer', 'src', 'common', 'data.js')
const OUT = path.join(ROOT, 'phone-app', 'app', 'src', 'main', 'java', 'com', 'hypergym', 'data', 'BandExercises.kt')
const OUT_REL = path.relative(ROOT, OUT).replace(/\\/g, '/')
const SOURCE_LABEL = 'miband10pro-trainer/src/common/data.js'

const argv = process.argv.slice(2)
const CHECK = argv.includes('--check')
const QUIET = argv.includes('--quiet') || CHECK

function fail(msg) {
  console.error('[sync-band-exercises] ' + msg)
  process.exit(1)
}
function log(msg) {
  if (!QUIET) console.log(msg)
}

// ---------------- 1. 从 data.js 求值提取 ----------------

function extract() {
  if (!fs.existsSync(SRC)) fail('找不到手环动作表源文件：' + SRC)
  const source = fs.readFileSync(SRC, 'utf8')

  // 砍掉 ESM 的 `export default {...}`（里面引用了那些函数，Kotlin 侧用不到）
  const cut = source.indexOf('export default')
  if (cut < 0) fail('data.js 里找不到 `export default`，结构可能已变化，请人工确认。')
  const body = source.slice(0, cut)

  const sandbox = { module: { exports: {} } }
  try {
    vm.runInNewContext(
      body +
        '\nmodule.exports = { GROUP_ORDER: GROUP_ORDER, GROUP_COLORS: GROUP_COLORS, DEFAULT_EXERCISES: DEFAULT_EXERCISES };',
      sandbox,
      { filename: SRC, timeout: 5000 }
    )
  } catch (e) {
    fail('求值 data.js 失败：' + e.message)
  }

  const { GROUP_ORDER, GROUP_COLORS, DEFAULT_EXERCISES } = sandbox.module.exports

  // ---------------- 2. 校验（宁可构建失败，也不要让错数据进 APK） ----------------
  if (!Array.isArray(GROUP_ORDER) || GROUP_ORDER.length === 0) fail('GROUP_ORDER 不是非空数组。')
  if (!GROUP_COLORS || typeof GROUP_COLORS !== 'object') fail('GROUP_COLORS 不是对象。')
  if (!Array.isArray(DEFAULT_EXERCISES) || DEFAULT_EXERCISES.length === 0) fail('DEFAULT_EXERCISES 不是非空数组。')

  const seen = new Map()
  const exercises = DEFAULT_EXERCISES.map((e, i) => {
    if (!e || typeof e.name !== 'string' || !e.name.trim()) fail(`第 ${i} 个动作缺少合法 name。`)
    const name = e.name.trim()
    if (typeof e.group !== 'string' || !e.group.trim()) fail(`动作「${name}」缺少 group，无法分类。`)
    const group = e.group.trim()
    if (group !== '其他' && !GROUP_ORDER.includes(group)) {
      fail(`动作「${name}」的 group「${group}」不在 GROUP_ORDER 里，手环端分组会漏掉它。`)
    }
    if (seen.has(name)) fail(`动作名「${name}」重复出现（第 ${seen.get(name)} 与第 ${i} 个），分类会产生歧义。`)
    seen.set(name, i)
    return { name, group }
  })

  // 只保留当前动作表真实存在的分组（与手环 getGroups() 的语义一致），
  // 颜色取 GROUP_COLORS；手环缺色时用 null 占位，由 Kotlin 侧回退到默认色板
  const usedGroups = new Map()
  for (const e of exercises) if (!usedGroups.has(e.group)) usedGroups.set(e.group, GROUP_COLORS[e.group] || null)
  const groups = GROUP_ORDER.filter(g => usedGroups.has(g))
  const colors = groups.map(g => [g, usedGroups.get(g)])
  if (GROUP_COLORS['其他']) colors.push(['其他', GROUP_COLORS['其他']])

  return { groups, colors, exercises }
}

// ---------------- 3. 生成 Kotlin ----------------

/** Kotlin 字符串字面量转义（$ 必须转义，否则会被当成模板起始符） */
function kstr(s) {
  return '"' + String(s).replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\$/g, '\\$') + '"'
}

/** "#FA734F" → "0xFFFA734FL"；非法值返回 null（由 Kotlin 侧回退默认色板） */
function kcolor(hex) {
  if (typeof hex !== 'string') return null
  const m = /^#?([0-9a-fA-F]{6})$/.exec(hex.trim())
  if (!m) return null
  return '0xFF' + m[1].toUpperCase() + 'L'
}

function generate(data) {
  const L = []
  L.push('package com.hypergym.data')
  L.push('')
  L.push('/**')
  L.push(' * ⚠️ 本文件由 `tools/sync-band-exercises.js` 自动生成，**请勿手工编辑**（改动会在下次构建时被覆盖）。')
  L.push(' *')
  L.push(' * 源头：`' + SOURCE_LABEL + '`（手环端动作表 DEFAULT_EXERCISES / GROUP_ORDER / GROUP_COLORS）')
  L.push(' * 重新生成：`node tools/sync-band-exercises.js`（build-debug.ps1 每次构建前会自动执行）')
  L.push(' *')
  L.push(' * 手环端动作表是动作分类的唯一权威来源；手机端只做「按名字精确查表 + 少量历史兜底」，')
  L.push(' * 详见 [MuscleMap]。')
  L.push(' *')
  L.push(' * 本次生成：' + data.exercises.length + ' 个动作，分组 ' + data.groups.join('/'))
  L.push(' */')
  L.push('internal object BandExercises {')
  L.push('')
  L.push('    /** 手环 GROUP_ORDER：分组展示顺序（只含当前动作表真实存在的分组） */')
  L.push('    val groupOrder: List<String> = listOf(')
  for (let i = 0; i < data.groups.length; i += 6) {
    L.push('        ' + data.groups.slice(i, i + 6).map(kstr).join(', ') + ',')
  }
  L.push('    )')
  L.push('')
  L.push('    /** 手环 GROUP_COLORS：部位 → 颜色（0xAARRGGBB）。色值无效时为 null，Kotlin 侧回退默认色板 */')
  L.push('    val groupColors: Map<String, Long?> = mapOf(')
  for (const [g, hex] of data.colors) {
    const c = kcolor(hex)
    if (!c) log(`[sync] 注意：分组「${g}」在 data.js 里没有合法颜色（${JSON.stringify(hex)}），手机端将用默认色。`)
    L.push('        ' + kstr(g) + ' to ' + (c || 'null') + ',')
  }
  L.push('    )')
  L.push('')
  L.push('    /** 手环 DEFAULT_EXERCISES：动作名 → 部位。手机端分类的权威依据 */')
  L.push('    val groupByName: Map<String, String> = mapOf(')
  for (const e of data.exercises) {
    L.push('        ' + kstr(e.name) + ' to ' + kstr(e.group) + ',')
  }
  L.push('    )')
  L.push('')
  L.push('    /** 当前手环动作表的动作数 */')
  L.push('    val count: Int get() = groupByName.size')
  L.push('}')
  L.push('')
  return L.join('\n')
}

// ---------------- 4. 与现有产物比对 ----------------

const data = extract()
const text = generate(data)
const existing = fs.existsSync(OUT) ? fs.readFileSync(OUT, 'utf8') : null

// 差异 = 新表 vs 旧生成结果（整体重写，比较动作集合）
function diff(oldText) {
  if (!oldText) return { lines: ['  （首次生成）'], removed: [] }
  const oldMap = new Map()
  const re = /^\s*("(?:[^"\\]|\\.)*") to ("(?:[^"\\]|\\.)*"),\s*$/gm
  let m
  while ((m = re.exec(oldText)) !== null) {
    const name = JSON.parse(m[1])
    const group = JSON.parse(m[2])
    if (!/^\d|^0x/.test(group) && group !== 'null') oldMap.set(name, group)
  }
  const newMap = new Map(data.exercises.map(e => [e.name, e.group]))
  const lines = []
  const removed = []
  for (const [name, group] of newMap) {
    if (!oldMap.has(name)) lines.push(`  + 新增动作  ${name} → ${group}`)
    else if (oldMap.get(name) !== group) lines.push(`  ~ 分类变更  ${name}: ${oldMap.get(name)} → ${group}`)
  }
  for (const [name, group] of oldMap) {
    if (!newMap.has(name) && group !== 'null') {
      lines.push(`  - 删除动作  ${name}（原 ${group}）`)
      removed.push([name, group])
    }
  }
  return { lines, removed }
}

const { lines: changes, removed } = diff(existing)

/** 被删掉的动作如果还留在历史记录里，就会掉进「其他」。提醒去 MuscleMap 补历史别名 */
function warnRemoved(removedList) {
  const kotlin = path.join(ROOT, 'phone-app', 'app', 'src', 'main', 'java', 'com', 'hypergym', 'data', 'MuscleMap.kt')
  if (!removedList.length || !fs.existsSync(kotlin)) return
  const src = fs.readFileSync(kotlin, 'utf8')
  const missing = removedList.filter(([name]) => !src.includes('"' + name + '"'))
  if (!missing.length) return
  console.warn('')
  console.warn('[sync] ⚠️  以下动作已从手环动作表移除：')
  for (const [name, group] of missing) console.warn(`         ${name}（原属 ${group}）`)
  console.warn('      若历史记录里还有它们，会被归入「其他」。请到 MuscleMap.kt 的 LEGACY_ALIASES')
  console.warn('      里补一条映射（例如  "' + missing[0][0] + '" to "' + missing[0][1] + '", ），否则肌群占比/PR 分组会失真。')
  console.warn('')
}

/** 动作表里已有的名字不该再出现在 LEGACY_ALIASES 里，否则同一动作有两份定义、容易改漏一边 */
function warnStaleAliases(exercises) {
  const kotlin = path.join(ROOT, 'phone-app', 'app', 'src', 'main', 'java', 'com', 'hypergym', 'data', 'MuscleMap.kt')
  if (!fs.existsSync(kotlin)) return
  const src = fs.readFileSync(kotlin, 'utf8')
  const block = /LEGACY_ALIASES\s*=\s*mapOf\(([\s\S]*?)\n\s*\)/.exec(src)
  if (!block) return
  const inTable = new Set(exercises.map(e => e.name))
  const stale = []
  for (const m of block[1].matchAll(/"((?:[^"\\]|\\.)*)"\s+to\s+"((?:[^"\\]|\\.)*)"/g)) {
    const name = JSON.parse('"' + m[1] + '"')
    if (inTable.has(name)) stale.push([name, JSON.parse('"' + m[2] + '"')])
  }
  if (!stale.length) return
  console.warn('')
  console.warn('[sync] ⚠️  以下动作已经在手环动作表里，MuscleMap.LEGACY_ALIASES 里的同名条目已多余：')
  for (const [name, group] of stale) console.warn(`         ${name} to "${group}"`)
  console.warn('      精确查表优先级更高，这几条不会生效；建议删掉，避免以后改动作表时改漏一边。')
  console.warn('')
}

// ---------------- 5. 落盘 / 校验 ----------------

if (existing === text) {
  log(`[sync] 手环动作表已同步：${data.exercises.length} 个动作，分组 ${data.groups.join('/')}`)
  warnStaleAliases(data.exercises)
  warnRemoved(removed)
  process.exit(0)
}

if (CHECK) {
  console.error('[sync] 手机端动作表与 data.js 不同步！')
  changes.forEach(l => console.error(l))
  console.error('      请运行：node tools/sync-band-exercises.js')
  process.exit(1)
}

fs.mkdirSync(path.dirname(OUT), { recursive: true })
fs.writeFileSync(OUT, text, 'utf8')

console.log(`[sync] 已同步手环动作表 → ${OUT_REL}`)
console.log(`       ${data.exercises.length} 个动作，分组 ${data.groups.join('/')}`)
if (changes.length) {
  console.log('       本次变化：')
  changes.forEach(l => console.log(l))
} else {
  console.log('       （动作集合未变，仅颜色/顺序等元数据更新）')
}
warnStaleAliases(data.exercises)
warnRemoved(removed)
