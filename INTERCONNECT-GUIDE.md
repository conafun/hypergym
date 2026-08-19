# HyperGym 手环(10 Pro) ↔ 手机 联调要点总结

> 状态：✅ 双向已打通（2026-08-15 验证）
> 环境：小米手环 10 Pro（Vela 快应用）+ 小米 HyperOS 手机（小米运动健康 com.mi.health）

---

## 1. 通信架构（三层）

```
手环快应用 system.interconnect
      │  (蓝牙, 由手环框架自动建立)
      ▼
手机「小米运动健康」com.mi.health 的 XMS_WEARABLE_SERVICE 服务（中继+校验）
      │  (AIDL binder)
      ▼
手机第三方 App（xms-wearable-lib_1.4_release.aar SDK）
```

- 手环端 API：`@system.interconnect`
- 手机端 SDK：`com.xiaomi.xms.wearable.*`（MessageApi / NodeApi / AuthApi / ServiceApi）
- 手机上必须装有「小米运动健康」（com.mi.health）且手环已在其内配对连接

---

## 2. 包名与签名（第一优先级，错一项就静默丢消息）

| 项 | 手环 RPK | 手机 APK |
|---|---|---|
| 包名 | manifest.json 的 `package` | applicationId / namespace |
| 签名 | sign/release/{certificate.pem, private.pem} | keystore.jks 的证书 |

- 两者包名 **必须完全一致**（本项目：`com.hypergym`）
- 两者签名 **必须出自同一证书**。RPK 签名证书要从 APK keystore 导出：
  `keytool -importkeystore → p12 → openssl pkcs12 → pem → 拆分 certificate.pem / private.pem`
- 本项目使用官方测试证书，SHA256 指纹：
  `EA:B2:34:44:C0:31:B8:14:AC:DF:37:2E:D0:4C:BC:13:0F:E7:16:C1:31:B1:E7:75:7B:BE:76:32:0C:9A:24:2F`（CN=wearable）
- **验证方法**：RPK 内签名块里提取 DER 证书算 SHA256，与 `keytool -printcert -jarfile xxx.apk` 对比，必须一致

### ⚠️ 最重要的一条教训：换签名必须先卸载

手机穿戴服务会**缓存手环 App 的签名指纹**。旧签名 RPK 装过手环后，即使覆盖安装新签名 RPK，
服务仍拿旧指纹核对手环发来的消息 → 不匹配 → **静默丢弃**（手环显示发送成功、诊断=0，手机却收不到，
且没有任何报错 —— 本次排查花了一整天的根因）。

**换签名/换包名的正确顺序：**
1. 手环上**彻底卸载**旧 App（不是覆盖安装）
2. 手环 + 手机**都重启一次**
3. 装新 RPK / APK
4. 手机 App 先开（前台、屏幕常亮）→ 再开手环 App → 再测试

---

### 版本升级/重装注意事项（两端通用）

**手机 APK：**
- 签名不变（同一个 keystore、包名不变）→ **直接覆盖安装，不用卸载**，versionCode 递增即为正常升级，App 本地数据保留
- 签名变了（换/丢 keystore）→ 必须先卸载旧版，否则系统报"签名冲突"装不上，数据也保不住
- 铁律：**keystore 和密码永久备份**，丢了就永远失去覆盖升级能力

**手环 RPK：**
- 即使签名、包名完全没变，也**不建议直接覆盖安装** —— 手机运动健康服务缓存手环 App 签名指纹，覆盖安装不一定刷新缓存，可能"装好了但消息被静默丢弃"
- 每次升级都按稳妥流程走：卸载旧 App（⚠️ 卸载前先用长按发送把训练记录同步到手机）→ 手环 + 手机都重启 → 装新 RPK → 手机 App 先开 → 再开手环 App → 跑诊断验证
- 懒人降级方案（不想双端重启）：只卸载旧包 → 装新包 → 手机 App 先开 → 手环 App 后开 → 看诊断码；若 B 码全绿但手机无 P12，必须补做双端重启

**两端通用清单：**
1. **版本号递增**：RPK 的 `versionCode`/`versionName`、APK 的 `versionCode` 都要比旧版大，否则装不上/不算升级
2. **包名、签名永不改**：改了就是系统层面两个不同的 App，与升级无关，旧缓存必清
3. **minPlatformVersion 别乱动**：保持 1200（10 Pro 平台版本），改大装不上、改小无意义
4. **小米运动健康保持配对**：升级前后确认手环仍正常连着 com.mi.health，断连会导致服务转发中断
5. **装完必验证**：不要假设"装上了就是好的"，跑一遍 B/P 诊断码（B06=1、B07=0、手机 P12 出现）确认链路活着再收工

---

## 3. 手环端（Vela 快应用）要点

### manifest.json
```json
{
  "package": "com.hypergym",
  "minPlatformVersion": 1200,          // ← 10 Pro 平台版本是 1200，必须用 1200（1000 会出问题）
  "features": [ { "name": "system.interconnect" } ]
}
```

### API 使用（易错点全部踩过）
```js
import interconnect from '@system.interconnect'
var conn = interconnect.instance()      // 单例

conn.onopen    = (res) => {}            // 事件是【属性赋值】，不是方法调用
conn.onclose   = (data) => {}           // data.code / data.data
conn.onerror   = (data) => {}           // code 1000未知 1001手机APP未安装 1006连接断开
conn.onmessage = (data) => {}           // 收手机数据: data.data 是字符串

conn.send({
  data: { /* 必须是对象！字符串会报 202 invalid args */ },
  success: () => {},
  fail: (data, code) => {}              // ← fail 是两个参数 (data, code)，不是 res.code！
})

conn.getReadyState({ success: (res) => {} })   // res.status: 1=已连接 2=断开
conn.diagnosis({ success: (res) => {} })       // 【不能传 timeout 参数】，10 Pro 会报 202
// diagnosis status: 0=OK 204=连接超时 1001=对端APP未安装 1000=其他
```

---

## 4. 手机端（Android）要点

### 正确初始化顺序（顺序不能乱）
```
1. Wearable.getNodeApi(ctx) / getAuthApi / getMessageApi / getServiceApi
2. getConnectedNodes()            → 取 nodes[0].id（nodeId）
3. authApi.checkPermission / requestPermission(nodeId, Permission.DEVICE_MANAGER, Permission.NOTIFY)
4. messageApi.addListener(nodeId, listener)     ← 权限通过后注册
5. onMessageReceived(nodeId, bytes)             ← 收到手环数据
```

### 关键 API 行为
- `addListener(nodeId, listener)`：绑定**特定 nodeId**；同一进程重复注册抛 `IllegalStateException("you have registered")`
- `sendMessage(nodeId, byte[])`：手机→手环；手环端 `conn.onmessage` 收到
- 收到的手环数据是 **JSON 字符串的 UTF-8 字节**：`new String(bytes)` 直接是 JSON
- 服务连接：SDK 自动绑定 `com.mi.health` 的 `XMS_WEARABLE_SERVICE`（绑定失败才轮询 com.xiaomi.wearable）
- `nodeApi.isWearAppInstalled(nodeId)`：查询服务是否认识手环上的 App（排查用，P17）
- `serviceApi.getServiceApiLevel()`：查询服务 API 级别（排查用，P02b）
- `serviceApi.registerServiceConnectionListener()`：服务连接/断开回调

### 环境注意点
- **手机 App 必须前台运行**，HyperOS 会杀后台进程导致监听失效（开源项目血泪教训）
- 建议 `FLAG_KEEP_SCREEN_ON` 保持屏幕常亮（测试期）
- 手机上要有蓝牙/定位权限（Android 12+ 需 BLUETOOTH_CONNECT/SCAN 运行时授权）
- 手环端测试时**不能息屏**

### 测试顺序（官方+开源项目共同要求）
1. 运动健康先连上手环
2. 手机 App 先开、保持前台
3. 再开手环 App
4. 手环不熄屏操作

---

## 5. 调试代码体系（两端内置）

### 手环端 B 码（屏4「联调诊断」页）
| 代码 | 含义 |
|---|---|
| B00 | 诊断启动/重连 |
| B01/B02 | 连接对象创建 成功/失败 |
| B03 | 通道已打开 onopen |
| B04/B05 | 通道关闭/错误（B05 带 code，1001=手机APP未安装） |
| B06 | getReadyState：1=已连接 2=断开 |
| B07 | diagnosis：0=OK 204=超时 1001=对端未安装 |
| B08 | Ping 发送成功/失败 |
| B09 | 收到手机消息 |
| B10 | 收到手机回包 = 双向打通 |

### 手机端 P 码（日志区）
| 代码 | 含义 |
|---|---|
| P01 | SDK 初始化 |
| P02/P02b | 服务监听注册 / 服务API级别 |
| P03/P04 | 服务已连接 / 断开 |
| P05 | Android 权限 |
| P06/P07 | 找到设备 / 未找到 |
| P08/P09 | SDK 权限授予 / 失败 |
| P10/P11 | 消息监听注册成功 / 失败 |
| P12 | 收到手环消息（字节数+内容） |
| P13/P14 | 自动回包成功 / 失败 |
| P15/P16 | 测试发送成功 / 失败 |
| P17 | 手环端应用是否已注册（FALSE=服务不认识手环App） |

### 判定速查
- 手环 B05 code=1001 → 手机 App 没装/包名对不上
- 手环 B07=1001 → 对端 App 未安装（包名或安装状态问题）
- 手环 B06=1、B07=0、B08 成功 但手机无 P12 → **签名缓存问题**（卸载重装+重启）或手机 App 被系统杀后台
- 手机 P17=FALSE → 服务不认识手环 App，卸载重装手环端
- 双向都收不到但诊断=0 → 检查 addListener 是否注册成功（P10）

---

## 6. 当前数据协议（v1.0.61）

### 手环 → 手机（业务数据）
```json
{ "type": "training-records", "date": "2026-08-15", "records": [ ... ] }
```
- records 结构：`{ exercise, weight, sets: [{ set, reps, volume }] }`

### 手环 → 手机（诊断 Ping）
```json
{ "type": "ping", "t": 1755000000000, "src": "band-dbg" }
```

### 手机 → 手环（自动回包 / 测试）
```json
{ "type": "ping-reply", "t": 1755000000001, "src": "phone" }
```

### 手机端存储策略（增量分片落盘，v2.2 起；v1.1 单文件已兼容）
- **目标：文件在数据就在** —— 卸载重装手机 App 后训练记录不丢；**展示页直接读文件夹，无需手环重传**
- **主存储**：SAF 目录（首次启动自动弹目录选择，如 `Documents/HyperGym`，授权持久化）
- **兜底**：App 内部存储（未选目录/授权失效时用，卸载即丢，界面黄字警告）
- **分片文件**：目录内多个 `records-NNNN.jsonl`（如 records-0001.jsonl），**每个文件最多 30 行 = 30 天记录**，写满自动开下一个分片；不是一条数据一个文件
- **格式**：JSONL（每行一条 JSON）—— 真 JSON 数组无法增量追加，每行一个对象才能 append-only；每收一条追加一行 + fsync，绝不整文件重写（除压缩）
- **读取 = 扫描目录全部 .jsonl**（含旧版单文件 `records.jsonl`，甚至残留重名文件），按行解析后以 `date` 去重合并 → 启动/回前台自动重扫，全部数据显示
- **去重**：以 `date` 为主键，同日期后到覆盖先到（内存 `date→记录` 索引）；总行数超过 1200 自动压缩重写（去重后按 30 行/片重新分片）
- **线程**：单线程队列串行化所有磁盘操作；解析在后台线程，UI 只拿快照
- **切目录迁移**：内部/旧目录数据与新目录既有数据合并（当前数据同日期覆盖），统一重写为新分片
- **S 码日志**：S01 后端 / S02 载入天数+文件数 / S02r 目录重扫 / S03 警告 / S04 目录选择 / S05 落盘成功（含分片名） / S06 落盘失败 / S07 目录切换结果

---

## 7. 踩坑记录（血泪史）

| # | 现象 | 原因 | 解法 |
|---|---|---|---|
| 1 | 手机收不到，手环一切正常 | 穿戴服务缓存旧签名 | 手环卸载重装+双端重启 |
| 2 | send 报 202 invalid args | data 传了字符串 | data 必须是对象 |
| 3 | diagnosis 报 202 args type error | 传了 timeout 参数 | 10 Pro 不传 timeout |
| 4 | send failed undefined undefined | fail 回调签名写错 | fail(data, code) 两参 |
| 5 | addListener 报 you have registered | 同进程重复注册 | 检查注册状态，重启进程 |
| 6 | 诊断 RPK 全绿但业务包不通 | 诊断 RPK 用了 demo 包名，证据不能迁移 | 必须用自己的包名实测 |
| 7 | 换新包后按钮样式"没变" | 18px 圆角不明显 / 装了旧包 | 改 24px + 卸载重装 |
| 8 | APK 构建找不到 Build Tools | buildToolsVersion 未指定 | 显式 36.1.0 |
| 9 | themes.xml 编译失败 | Material 主题残留 | 用 Theme.AppCompat.Light.DarkActionBar |
| 10 | Compose 依赖报 core-ktx 1.16.0 需 AGP 8.6+ | Vico 2.1.4 传递依赖新 core | resolutionStrategy 强制 core 1.13.1 |
| 11 | Kotlin 编译 daemon 连不上（AccessDeniedException） | daemon 目录被沙箱拦截 | gradle.properties 设 kotlin.compiler.execution.strategy=in-process |

---

## 8. 构建命令速查

```powershell
# 手环 RPK（release，用 sign/release 证书）
cd F:\DeepseekHarness\miband\miband10pro-trainer
node node_modules\aiot-toolkit\lib\bin.js release
# 产物: dist\com.hypergym.release.<版本>.rpk → 复制为 demo-package\HyperGym-release.rpk

# 手机 APK（release，用 keystore.jks）
$env:JAVA_HOME="F:\DeepseekHarness\miband\.research\downloads\jdk21\jdk-21.0.4+7"
$env:GRADLE_USER_HOME="F:\DeepseekHarness\miband\.research\gradle-home"
$env:ANDROID_HOME="C:\Users\admin\AppData\Local\Android\Sdk"
F:\DeepseekHarness\miband\phone-app\gradlew.bat -p F:\DeepseekHarness\miband\phone-app assembleRelease
# 产物: app\build\outputs\apk\release\app-release.apk → 复制为 demo-package\HyperGym-Phone.apk
```

## 9. 后续开发方向备忘

### 🔒 冻结约定（v2.2 起，全版本保持，不再改动）
> 传输链路已走通，用户拍板封板。以后所有版本 APK 只允许动 UI/UX 层，以下一律不动：
- **传输协议**：手环 `system.interconnect`（training-records / ping / ping-reply，v1.0.61）→ 手机 xms-wearable SDK；调试码 P01–P17 / B00–B10 含义不变
- **存储设定**：SAF 目录为主 + 内部兜底；`records-NNNN.jsonl` 分片、30 行/文件自动轮换；JSONL append-only；date 去重；启动/回前台全目录重扫；诊断页「重扫目录」按钮保留
- **包名/签名**：`com.hypergym` + 同一证书；升级规则不变（APK 覆盖安装，RPK 卸载重装）
- **统计引擎**：`StatsEngine.kt` / `MuscleMap.kt` 的纯函数接口（UI 只消费，不改算法签名）
- 版本组合锁定不变：Kotlin 2.1.21 + Compose BOM 2025.05.01 + Vico 2.1.4 + AGP 8.2.2 + Gradle 8.2

### UI/UX 优化方向（下一阶段主线，数据展示要"完美"）
- 动作详情页（上次延期）：点动作 → e1RM 力量曲线 + 历史重量表
- 每周报告卡：一句话总结（练几次/环比/新PR）
- 动效：数字滚动、图表过渡动画（Vico 支持插值）、热力图淡入
- 配色/深色模式、空状态引导
- 肌群分布可视化升级：人形图/环形图
- 用户自己有想法优先

- 手环端：改动已收敛（历史长按发送即可）；新增数据类型只需扩展 `_sendDayRecords` 的 payload
- 手机端：✅ 增量分片落盘（RecordStore，SAF+JSONL 30行/文件+date去重+启动/回前台全目录重扫）；✅ v2.0 起 UI 迁移 Jetpack Compose（浅色简洁风）+ Vico 图表；✅ v2.1 数据展示升级：渐变英雄卡 + 周/月/全部切换 + 训练热力图（近5周）+ 容量趋势渐变面积图（含环比徽章）+ 肌群分布条形图（MuscleMap 动作→肌群关键词映射）+ 训练日记升级（星期/PR徽章/每组次数芯片/PR=e1RM或最大重量破纪录）；✅ v2.2 存储分片化（records-NNNN.jsonl，30行/文件自动轮换）+ 展示页默认读文件夹全部文件 + 诊断页「重扫目录」按钮；✅ **v3.0 四屏 UI 定稿（与 ui-prototype 一致）**：底部 4 文字 tab（数据/肌群/日记/诊断）+ 暖橙 #E06040 主题 + 白卡圆角区块 + Canvas 自绘图表（数据页=可展开热力图+当天内容+周/月/全部总量柱状图，肌群页=周/月动作容量分组柱状图+肌群占比饼图+均衡建议，日记页=PR徽章+橙色容量汇总，诊断页=P/S 码日志+5 按钮）+ logo/1.png 作为启动图标
- **纯函数统计引擎**：`data/StatsEngine.kt`（周期统计/环比/周容量/热力图/连续周/PR检测，日期全部用 ISO 字符串比较，不用 java.time 避免 API 26 限制）；`data/MuscleMap.kt`（中文动作名关键词→肌群）
- **版本组合锁定（不要乱升）**：Kotlin 2.1.21 + Compose BOM 2025.05.01 + Vico 2.1.4 + AGP 8.2.2 + Gradle 8.2；Vico 更新版本要求更新的 Kotlin/AGP，升级需整套一起评估
- 参考开源范例：Searchstars/Hyperbilibili（band9 分支）+ HyperbiliInterconnect（手机端）
