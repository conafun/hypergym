# HyperGym 项目审查报告

> 审查范围：`F:\DeepseekHarness\miband` 全仓库
> 审查方式：源码逐文件通读（手机端 ~2100 行 Kotlin + 手环端 795 行 ux/JS）+ 构建配置、Git 历史、资产实测
> 结论一律给出 `文件:行号` 证据；文档与实现不一致处已逐条核对

---

## 一、项目概览

**HyperGym** —— 小米手环 10 Pro 记录力量训练、同步到 Android 手机做备份与可视化的双端系统。原创度较高，不是教程复刻。

| 维度 | 实测数据 |
|---|---|
| 手机端 | Kotlin 2.1.21 + Compose (BOM 2025.05.01)，AGP 8.2.2，minSdk 21 / targetSdk 30 / compileSdk 36 |
| 手环端 | Vela OS 快应用，`com.hypergym` v1.0.64，`src/pages/home/index.ux` 单文件 36 KB / 795 行 |
| 资产 | `exercises.json` 941,877 字节，**1324 个动作**，image/video 字段 100% 齐全，图片 1324 + 视频 1324 = 2649 个文件 |
| 动作分类实测 | 上臂 292、大腿 227、背 203、核心 169、胸 163、肩 143、小腿 59、前臂 37、有氧 29、颈 2 |
| Git | 15 个提交（2026-08-19 初次提交 → 2026-09-05），工作区有 8 个文件未提交；`.git` 经本次清理后 **28.5 MB**（原 785.6 MB） |
| 仓库治理 | **无测试**（`app/src` 下只有 `main`）、**无 CI**、无 lint 配置 |

**架构设计是这份代码最大的加分项**：数据层拆分干净，职责边界清晰。

```
手环 index.ux ──@system.interconnect──▶ MainActivity（全部联通逻辑）
                                          │
                     ┌────────────────────┴────────────────────┐
                     ▼                                        ▼
              RecordStore（单线程串行）                  UiState（Compose 订阅）
                     │
        ┌────────────┴────────────┐
        ▼                         ▼
   SafBackend（SAF 主）    InternalBackend（兜底）
   records-NNNN.jsonl · 30 行/片 · date 主键去重 · 超 1200 行压缩
```

值得肯定的设计决策：

- **`RecordStore` 用单线程 executor 串行化所有磁盘操作**（`RecordStore.kt:60-62`）——彻底绕开了并发写文件的复杂度，是正确且克制的选择。
- **存储后端抽象 + 双实现**（`DataBackend.kt:52-70`）：SAF 为主、内部存储兜底，"卸载重装数据不丢"是真的能成立。
- **分片 + append-only + fsync**：`appendLine` 写完即 `fd.sync()`（`DataBackend.kt:121,196`），断电不丢已落盘数据。
- **`listDataFiles` 按 `(number, name)` 排序**（`DataBackend.kt:90,153`），旧版 `records.jsonl`（number=0）排在分片之前，配合"后行覆盖前行"的索引策略，保证新数据胜出——顺序语义是对的。
- **`UiState` 单向绑定**：composable 只依赖状态对象，不依赖 Activity（`HyperGymApp.kt:30`）。
- **UI 层无 TODO、无注释掉的死代码**，中文注释写的是"为什么"而不是"做了什么"（`MuscleMap.kt:3-17`、`PivotCard.kt:304`）。
- 手环端诊断页（B 码日志）对真机联调非常实用，是踩过坑才有的设计。

---

## 二、必须优先处理

### 🔴 1. 签名密钥已提交到公开仓库（最高危）

仓库 `https://github.com/conafun/hypergym` **未登录即可访问（HTTP 200，公开）**，而 README 第 379 行还写着"暂不开源"。已确认在版本控制内的敏感文件：

| 文件 | 内容 | 状态 |
|---|---|---|
| `phone-app/keystore.properties` | **明文签名口令** `xmswearable`（debug + release 同一套） | 已跟踪 |
| `phone-app/keystore/keystore.p12` | 签名密钥库 | 已跟踪 |
| `phone-app/keystore/keystore.pem` | 签名证书 | 已跟踪 |
| `miband10pro-trainer/sign/release/private.pem` | **rpk 签名私钥** | 已跟踪 |
| `diag-band/sign/release/private.pem` | rpk 签名私钥 | 已跟踪 |
| `interconnect-demo-build/sign/{debug,release}/private.pem` | rpk 签名私钥 | 已跟踪 |

后果：任何人可下载密钥、用你的身份签发 APK/rpk 分发（供应链投毒），能覆盖安装你的应用。口令 `xmswearable` 同时是 debug/release 共用（`keystore.properties:3-5,9-11`），且与密钥别名相同，字典强度极低。

**修复（顺序不能颠倒）：**

1. **立即轮换**：生成全新的 release keystore（`keytool`），旧密钥视为已泄露并作废。
2. **从 Git 历史彻底清除**：`git filter-repo --path phone-app/keystore.properties --path phone-app/keystore --path-glob '*/private.pem' --invert-paths`，然后 force-push。注意：只要历史里存在过，单纯 `git rm` 无效——**必须重写历史 + 轮换密钥**。
3. **补 `.gitignore`**（当前 `*.jks` 挡住了 `keystore.jks`，却放过了 `.p12`/`.pem`/`keystore.properties`）：
   ```gitignore
   keystore.properties
   keystore/
   sign/
   *.p12
   *.pem
   ```
4. 改为从环境变量 / `~/.gradle/gradle.properties` 读口令，仓库内只留 `keystore.properties.example`。

### 🔴 2. `keystore.jks` 被忽略但构建依赖它 → 新克隆无法构建

`app/build.gradle:56` 引用 `release.store.file=../keystore/keystore.jks`，但：

```
phone-app/keystore/keystore.jks   tracked=False  exists=True   ← .gitignore:13 `*.jks` 命中
phone-app/keystore/keystore.p12   tracked=True   exists=True
```

**别人 clone 下来跑 `assembleRelease` 会直接失败**。这是个尴尬的组合：该忽略的（口令、密钥）提交了，不该忽略的（构建必需的 jks）忽略了。修复方式见上一条（把整套 keystore 移出仓库 + 提供示例文件 + 文档说明生成步骤）。

### ✅ 3. `.git` 曾有 757 MB 垃圾（已清理）

实测包内对象分类：

| 类别 | 数量 | 体积 |
|---|---|---|
| 可达 blob | 2751 | **25.5 MB** |
| 不可达 blob（垃圾） | 2086 | 756.8 MB |
| `.git` 总计（清理前） | — | 785.6 MB |

有 2086 个 blob 不再被任何 ref 引用（本地多次改写提交遗留），最大单个对象 **199 MB**，且单独占了一整个 `pack-0a7c6b15….pack`。

**已于本次审查中清理：**

```
git gc --prune=now      # 785.6 MB → 28.5 MB，回收 757.2 MB
```

清理前先做过安全检查：`git fsck` 显示 **0 个 unreachable/dangling commit**（全部是 blob 与 1 个 tree），无损坏、无 stash、无额外分支或标签，因此不存在历史丢失风险。清理后 `git fsck` 无错误，HEAD 仍为 `04defcf`，15 个提交、5439 个追踪文件、工作区状态均与清理前完全一致。

**一处更正：** 我此前写"这些大对象会随 clone/push 一起传输"是**错的**。Git 只传输可达对象，所以远端从未被污染——GitHub API 实测该仓库 `size` = 28478 KB ≈ **27.8 MB**，与本地清理后的可达数据（27.81 MiB）几乎一致。即这 757 MB 纯粹是**本地磁盘**问题，远端无需 force-push 来缩容。

**因此：** 重写历史现在**只剩"清除密钥"这一个理由**了，不再需要为体积而做。

### 🔴 4. 手环端存在真实的训练数据丢失路径

这是"会真正弄丢用户数据"的一类缺陷，四处相互叠加：

1. **读取失败即当空库，下次保存抹掉全部历史**
   `file-io.js:10-12` 的 `catch (e) {}` 把 JSON 解析失败静默降级为 `{}`，`fail` 回调同样返回 `{}`（`file-io.js:14`）；`index.ux:397-401` 直接 `self.int_records = records || {}`；而保存是全量覆盖写（`file-io.js:18-25`）。→ 一次解析失败，历史记录被清空。
2. **写盘失败后内存已污染，且会重复追加**
   `index.ux:606-633` 先改内存再写盘；失败仅 toast（`634-644`），`planItems` 未清空 → 用户再点"结束"会把同一批 set 二次 `concat`（`index.ux:628`）产生重复组。`endTraining`（`592-600`）无重入锁，快速连点两次都会执行。
3. **无原子写**：无"临时文件 → 校验 → rename"，无备份，`version` 字段只写不校验（`file-io.js:19` vs `10`）。
4. **重量档位回绕无钳位**：`index.ux:505-519` 用取模，卧推 90 kg 再按下一档直接跳回 40 kg，静默写入 50 kg 偏差。

**修复：** `readRecords` 区分"文件不存在"与"解析失败"（后者禁止保存并提示）；`saveRecords` 改临时文件 + rename + 保留上一份备份；`endTraining` 加重入锁与失败回滚；重量加减改钳位不回绕。

### 🔴 5. SAF 写入失败被当作成功上报

手机端 `SafBackend.appendLine`（`DataBackend.kt:190-198`）在 `findFile` 或 `openFileDescriptor` 返回 null 时**直接 `return`，不抛异常也不返回失败**。而 `RecordStore.ingest` 调用后无条件认为成功：

```kotlin
b.appendLine(target, day.rawJson)      // RecordStore.kt:328  ← 可能什么都没写
shardCounts[target] = (shardCounts[target] ?: 0) + 1
totalLines++
...
onResult(IngestResult(true, day.date, reason, ...))  // 上报 ok=true
```

后果：授权失效、provider 报错、磁盘满时，界面显示"已落盘 S05"，内存索引里也有这条数据，**但文件里没有**——直到下次 `reload` 数据凭空消失。这是"看起来成功的数据丢失"，比直接报错更危险。

**修复：** `DataBackend.appendLine` 改为返回 `Boolean` 或抛异常，`ingest` 据此上报失败并回滚内存索引（`RecordStore.kt:307-310` 的写入）。

---

## 三、值得修复的正确性问题

### 手机端

| 问题 | 证据 | 说明 |
|---|---|---|
| **`SimpleDateFormat` 非线程安全** | `StatsEngine.kt:13` | 单例共享 `fmt`，`todayString()`/`formatDate()` 可能在 UI 线程与后台线程并发调用 → 结果错乱或抛异常。改用 `java.time` 或每次新建/`ThreadLocal` |
| **"清除"按钮点了没反应** ✅已实测确认 | `DebugScreen.kt:74` → `MainActivity.kt:118` | 按钮清的是 `ui.receivedLines`，但页面只渲染 `state.logLines`（`DebugScreen.kt:56`），`receivedLines` 全局无人消费 → 日志一条不少 |
| **体积为 0 时除零产生 NaN** ✅已实测确认代码路径 | `DashboardScreen.kt:93,352`；`Charts.kt:53,64,67,113,136` | `maxOfOrNull{} ?: 1.0` 只兜空列表；当**所有天**的 `totalVolume()` 均为 0 时 `maxVol=0` → `NaN`，`NaN.dp` 会传给 Compose 尺寸。注意触发条件比"常见"窄（需全部记录容量为 0，如 sets 为空或 weight=0），故定为中等而非严重。修：`safeMax(v)=if(v<=0)1.0 else v` |
| **Pager 页面状态全部丢失** | `HyperGymApp.kt:74`；各页 `remember` | `HorizontalPager` 默认销毁离屏页，而全模块 `rememberSaveable` 出现 0 次 → 滑走再回来，筛选/展开/搜索/编辑态全部重置 |
| **选中日期被覆盖** | `DashboardScreen.kt:95` | `remember(sorted){ sorted.lastOrNull()?.date }` 把 remember key 当初始化用 → 手环每回传一次数据，用户选中的日期就被重置为最新 |
| **畸形 date 可致崩溃** | `TrainingData.kt:50` 只判空；`DateUtils.kt:25,28`、`DashboardScreen.kt:79,101` 定长切串 | 一条非法日期字符串即 `StringIndexOutOfBounds`/`NumberFormatException`。建议 `parse` 时用正则校验 `\d{4}-\d{2}-\d{2}` |
| **ExoPlayer 未随生命周期暂停** | `ExerciseScreen.kt:280-294` | 只有 `onDispose`，退后台视频继续播放；未设 `audioAttributes`（无音频焦点）；`AndroidView` 缺 `update` 块 |
| **主线程解析 941 KB JSON** | `ExerciseScreen.kt:70`、`MuscleScreen.kt:45` | 在 composition 内同步 `readText` + 解析 1324 条 → 首帧阻塞 |
| **Pivot 计算 O(n²)** | `PivotCard.kt:318-326` | `valueOf` 内 `recs.filter{}` 在 `sortedByDescending` 比较器里被反复调用 |
| **`MuscleMap.init` 隐式且有顺序依赖** | `MuscleScreen.kt:46` 是唯一调用点 | 未初始化时 `libraryBest` 返回 null（`MuscleMap.kt:112`），静默降级为关键词匹配 → 分类结果依赖"是否进过肌群页"。当前因 `PivotCard` 在 `MuscleScreen` 内部而侥幸正确，属脆弱设计 |
| **`groupCache` 无同步** | `MuscleMap.kt:43,84-86` | 普通 `HashMap` 可能被多线程写入 → 极端情况下 HashMap 结构损坏 |
| **Vico 是无用的重依赖** | `build.gradle:103` 声明，`app/src` 全代码 **0 处引用** | 图表全是 Canvas 自绘（`Charts.kt`/`PivotCard.kt`）。但它迫使 `configurations.all { force core-ktx 1.13.1 }`（`build.gradle:112-117`）——白背一个降级 hack。建议直接删除 |
| **release 无混淆/压缩** | `build.gradle:65` `minifyEnabled false` | 37 MB 的 APK 与此有关（`demo-package/HyperGym-Phone.apk` = 36,733,784 字节，主要是 2649 个媒体资产） |
| **版本号在配置期被改写** | `build.gradle:22-32` | 只要 Gradle 配置了 release 任务就 `versionPatch+1` 并写回 `version.properties` → IDE sync 也可能触发版本漂移 |

### 手环端

| 问题 | 证据 |
|---|---|
| **保存的 summary 页根本不可达** | `manifest.json:21-26` 只注册 `pages/home`；全项目无 `router.push('/pages/summary')`；`index.ux:641` 只是切到屏 2。`pages/summary/index.ux` 是死代码，而 README:158 声称"进入训练完成汇总页" |
| **长按重量数字直接退出应用** | `index.ux:15` `onlongpress="exitApp"` → `index.ux:727-729` `router.back()`。计划页误触即退出且不存档，且与"长按=编辑/发送"的既有语义冲突 |
| **屏号错位：划到历史页会触发诊断** | `index.ux:648-657` 判 `e.index === 3`，但 index 3 是历史屏，诊断屏是 index 4 → 每次进历史都发 `getReadyState` + 延迟 1.5 s 的 `diagnosis`，诊断屏反而不触发 |
| **定时器/监听泄漏** | `index.ux:358,387` 的 `setTimeout` 未登记，`onDestroy`（`308-313`）只清 4 个具名定时器；`dbgReconnect`（`386`）重复 `interconnect-client.js:18` 的 `instance()`，旧 conn 无销毁、闭包捕获页面 `self`（`335`）无法释放 |
| **跨零点归属错误** | `index.ux:604-605` 用**保存时刻**生成 `dateStr`，`405-407` 今日视图同源 → 23:50 开始、00:10 结束的训练整场归到次日；跨 0 点后原记录从"今日"消失 |
| **计数无上限** | `index.ux:570-571` `decCount` 有下限 1，`incCount` 无上限；`confirmCount`（`576`）`volume = wt*reps` 无校验 |
| **手势冲突 + 编辑态错位** | `index.ux:462-476` 按 X<168 判半屏、竖直位移 >60 px 即切换，与 swiper 横滑在对角手势下互抢；`addToPlan` 的 `unshift`（`523`）未重置 `editingIdx` → 编辑态错位到相邻项 |
| **已记录组无法修正** | `_enterEdit`（`675`）要求 `setsDone === 0`，而"删除"只在编辑态可见 → 记完组既不能改重量也不能删组 |
| **小屏文字溢出** | `index.ux:159/188/215/228` 无 `max-lines`/`text-overflow`，52 px 动作名超 6 字即溢出 336 px 容器 |
| **聚合逻辑三份拷贝** | `_buildLog`（`764-788`）对同一天先算 `dayVol` 再重算 `tr2/tv2`；`_buildToday`（`412-422`）是第三份 |

---

## 四、文档与实现不一致（README 需要修）

README 写得很用心（379 行、图文并茂），但手环端章节已明显落后于代码：

| README | 实际 |
|---|---|
| `README.md:341` "手环内置动作（9 个）" | `data.js` 实为 **26 个动作** |
| `README.md:126` versionName **1.0.61** | `manifest.json:4` = **1.0.64** |
| `README.md:345-346` 哑铃 10–50 / 步进 2 | `data.js:15` 10–**60** |
| `README.md:352` 倒蹬 40–80 / 步进 10 | `data.js:30` **30–60 / 步进 5** |
| `README.md:346,353` 列出"六角杠铃""T杠划船" | `data.js` 中**不存在**这两个动作 |
| `README.md:153` "上下滑动切换 9 个训练动作" | 已改为**两级菜单**（左滑选部位 / 右滑选动作，`index.ux:462-467`） |
| `README.md:158` "记录完成后进入训练完成汇总页" | 汇总页未注册，不可达 |
| `README.md:194` 图表技术 "Canvas 自绘" | 正确，但 `build.gradle:103` 仍挂着没用上的 Vico |
| `README.md:379` "暂不开源" | 仓库在 GitHub **公开可访问** |

另有 `docs/interconnect-plan.md` 的设计与实现偏离：文档 §6.1 要求 `cmd:"sync_records"` + `version` + 逐条 `ts`，实现发的是 `{type:'training-records',...}`（`index.ux:745`）；§6.4 手机→手环下发**完全未实现**（`interconnect-client.js:14-16` 把 `onMessage` 置 null，第 46 行永不回调）；§5.2 的连接状态轮询也未实现。

---

## 五、仓库卫生

- **构建产物入库**：`diag-band/build/`、`interconnect-demo-build/build/`、`miband10pro-trainer/build/`、`dist/*.rpk`（二进制，每次发版都产生 diff）、`interconnect-demo-build/.DS_Store`。
- **`健身动作库/index.html` 15 MB** 被跟踪，加上 1324 图片 + 1324 视频，导致 tracked 内容约 100 MB；而 `phone-app` 的 assets 是同一批媒体**再存一份**（2649 文件）——两个目录媒体重复。
- **`.research/` 已正确忽略**，从未进入追踪；那 757 MB 垃圾是本地 blob 残留，非 `.research` 入库。（本机目录 `F:\DeepseekHarness\miband` 与仓库同名易混淆，实际 `.research` 一直是 untracked）
- 未提交改动散落：`demo-package/*.rpk` 被改、`dist/1.0.63.rpk` 被删、`dist/1.0.64.rpk` 未跟踪——**二进制产物不该进 Git**，建议发布走 GitHub Releases 或 LFS。
- `diag-band`、`interconnect-demo-build` 是小米官方 demo 的副本，含 `private.pem`；建议整体移出仓库或明确标注来源与用途。

---

## 六、改进优先级

**P0（本周内）**

1. 轮换全部签名密钥 + 用 `git filter-repo` 从历史清除 + force-push；补齐 `.gitignore`（第 2 节第 1、2 条）
   —— 注：此步**只因密钥**而做，与仓库体积无关（见第 2 节第 3 条更正）
2. ✅ ~~`git gc --prune=now` 回收 757 MB~~ —— **已完成**，785.6 MB → 28.5 MB
3. 修手环端持久化：读取失败禁止保存、原子写 + 备份、`endTraining` 重入锁与回滚
4. 修 `appendLine` 静默失败 → 让 `ingest` 能上报真实的落盘失败

**P1（一个月内）**

5. 手机端：`safeMax` 兜住除零、`rememberSaveable` 保住页面状态、`TrainingParser.parse` 校验日期格式、`SimpleDateFormat` 换 `java.time`
6. 手环端：修屏号错位（`index.ux:648`）、移除 `onlongpress="exitApp"`、重量钳位、处理跨零点归属
7. 决定 summary 页的去留：注册进路由，或删除并同步 README
8. 删除未使用的 Vico 依赖，顺带去掉 `force core-ktx` 的降级 hack
9. 修好"清除"按钮（`DebugScreen.kt:74` / `MainActivity.kt:118`）

**P2（技术债）**

10. 同步 README 与 `interconnect-plan.md` 到实现（动作数、版本号、屏结构、协议）
11. 拆分 `index.ux`（795 行）：抽 `common/date-util.js`、`common/plan-store.js`、`pages/home/components/*`；把 `planItems/editingIdx/isCounting/delPending` 收敛为单一模式枚举
12. 拆分 `DashboardScreen.kt`（573 行）与 `PivotCard.kt`（340 行），把纯计算下沉到 data 层并**复用已有的 `StatsEngine`**（`trendSeries`/`heatmapWeeks`/`weeklyStreak` 目前写了却全未使用，UI 各写一份）
13. 引入最小可用的质量门禁：`app/src/test` 下先给 `StatsEngine`、`TrainingParser`、`MuscleMap` 这些纯函数补单测（当前**零测试**），加一个跑 `assembleRelease` 的 GitHub Actions
14. 清理 `build/`、`dist/*.rpk`、`.DS_Store` 等入库产物；媒体资产迁 LFS 或去重

---

## 七、总体评价

**这是一个完成度明显高于同类个人 Demo 的项目。** 通信链路（XMS Wearable SDK）、SAF 持久化、分片存储、双端联调诊断这些最容易烂尾的部分都真正跑通了，1324 个动作的资产也完整可用；数据层抽象和"文件在数据就在"的持久化思路是经过思考的设计，不是堆砌。

主要风险集中在三处，且都不在"算法"上，而在**边界与治理**：

1. **仓库治理失守**——签名密钥泄到公开仓库，`.git` 里躺着 757 MB 垃圾。这跟代码水平无关，但后果最严重，必须最先处理。
2. **持久化可靠性不足**——手环端"解析失败即抹库"、手机端"写失败仍报成功"，这两条构成的数据丢失路径比任何 UI 瑕疵都值得优先投入。
3. **文档滞后于代码**——README 很漂亮但与实现已多处不符（9 vs 26 个动作、1.0.61 vs 1.0.64、汇总页不可达），会持续误导后来者。

代码风格与可读性没有明显问题：无 TODO、无死注释、命名一致、注释解释动机；扣分项主要是两个超大文件（`index.ux` 795 行、`DashboardScreen.kt` 573 行）内部混杂了模板/样式/纯计算/UI，以及零测试带来的重构恐惧。

**如果只做三件事**：轮换密钥并清理 Git 历史 → 修手环端持久化的数据丢失路径 → 补 `StatsEngine`/`TrainingParser` 的单测。这三件做完，项目的"可信度"会有质变。
