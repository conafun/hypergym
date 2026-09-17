# 待办 / 待确认的需求变更

> 这里攒的是**口径已确认、但等攒齐一起改**的改动，以及**待你确认**的事项。
> 攒齐后一次性改代码 + 递增版本 + 出包 + 更新 README，再统一 push。
>
> 相关文档：[`SIGNING.md`](SIGNING.md)（签名硬约束，**禁止修改**）｜ [`REVIEW.md`](REVIEW.md)（代码问题清单）｜ [`README.md`](README.md) 更新记录

---

## ① 待改：PR 的「首次判定」

**状态**：口径已确认，**先改代码前的最后一步是确认"首次是否算 PR"**（2026-09-11 记）

### 你确认的 PR 口径（按你的原话整理）

1. PR = 某个动作**实际做出的最大重量**——必须是真实举起的，**不是推算**（不用 e1RM）
2. **不看组数**，只看那个重量数字
3. **自重（0kg）不出 PR**：例如「罗马椅」用自重时没有 PR
4. 但「罗马椅」若选了 **10kg 负重**做出的重量，**那一次就是这个动作的 PR**
5. **某个动作第一次做，它的重量就是 PR**；之后有更重的成绩就刷新 PR

### 现状（`StatsEngine.kt:241-256` 的 `prFlags`）

| 你的口径 | 现状 | 一致？ |
|---|---|:--:|
| 只看实际最大重量、不推算、不看组数 | 只看 `ex.weight` | ✅ |
| 自重（0kg）不出 PR | `0 > 历史值` 恒不成立，不会标 | ✅ |
| **第一次做就是 PR** | **首次不标** | ❌ |
| 更重则刷新 | 严格大于才标并刷新基线 | ✅ |

代码与注释都是刻意写的「首次记录不算 PR」：

```kotlin
if (bw != null && ex.weight > bw) prs.add(PrFlag(ex.exercise, ex.weight))
//     ^^^^^^^^^^^^ bw 为 null（= 第一次见这个动作）时不标
```

**实际影响**：第一次做「牧师椅 60kg」**不会**出现 🏆 徽章，要等第二次做得更重才有第一个徽章。

### 要改成

```kotlin
// weight <= 0 的记录整个跳过：既不标 PR，也不建立基线
if (ex.weight <= 0.0) continue
val bw = bestWeight[ex.exercise]
if (bw == null || ex.weight > bw) {
    prs.add(PrFlag(ex.exercise, ex.weight))   // 首次 或 更重 → PR
    bestWeight[ex.exercise] = ex.weight
}
```

**涉及文件**：`phone-app/app/src/main/java/com/hypergym/data/StatsEngine.kt`（`prFlags`）

**显示位置**：只影响**日记页**的 🏆 徽章（`DiaryScreen.kt:62,74,194`）；数据页与肌群页不使用 PR。

### ⚠️ 改之前必须先定的一件事

按新口径，**第一次训练那天、所有有配重的动作都会挂 PR 徽章**（比如第一次练 7 个动作 → 7 个 🏆），
因为它们各自都"从无到有刷新了纪录"。

| 选项 | 结果 |
|---|---|
| A. 首次也标 PR | 完全符合你说的口径；代价是第一天满屏徽章 |
| B. 首次不标、但把首次的值当基线（= 现状） | 第一天干净；代价是第一次做某动作不会显示 PR |

**这条需要你最终拍板**，我再动代码。

### 改完的收尾步骤（固定流程）

递增手机端版本 → `.\build-debug.ps1 -Only phone -Bump` → 更新 README 的「更新记录」 → 你确认后 commit + push

---

## ② 其他待确认 / 待办

### 性能优化（批次进度，2026-09-12 记）

已按性价比排成 4 批。**第 1 批已完成（`v3.1.13`）**，其余待办：

| 批次 | 内容 | 现象 | 预期收益 | 状态 |
|:--:|---|---|---|---|
| 1 | 数据透视算法：缓存分桶键 + 一次遍历建索引 + 去掉日期往返 | 切「周」档明显一顿 | 200 天数据 450 ms → 0.37 ms | ✅ `v3.1.13` |
| 2 | 非相邻 tab 改用 `scrollToPage`（现在 `animateScrollToPage` 会穿过中间所有页） | 点底部 tab 切页顿挫 | 切页顿挫基本消失 | ⏳ 待办 |
| 3 | 肌群页「动作数据汇总」改 Canvas/`LazyRow`；`Canvas` 里的 `textMeasurer.measure()` 改预测量 | 上下滚动卡 | 滚动顺滑 | ⏳ 待办 |
| 4 | 数据快照去重 + 派生值 `derivedStateOf` + 日志列表加 `key` | 收到手环数据时整页重算 | 减少重算 | ⏳ 待办 |

> 出处：`PivotCard.kt`（第 1 批，已改）｜`HyperGymApp.kt:50,74`（第 2 批）｜
> `Charts.kt:77,88,104-161`、`PivotCard.kt:223,230`、`MuscleScreen.kt:78-87`（第 3 批）｜
> `UiState.kt:24`、`MainActivity.kt:79`、`DebugScreen.kt:56`（第 4 批）。
> 复现脚本（`.research/`，不入库）：`bench-pivot.js`（复杂度压测）、
> `verify-pivot-equivalence.js`（新旧输出等价性 + 提速对比）、
> `verify-monday-date.js`（纯整数日期算法逐日比对）、`measure-parse.js`。

### 其他可选

- **（可选）把动作表同步也接进 Gradle**：目前同步由 `build-debug.ps1` 在手机端构建前执行，
  所以**走本脚本构建一定是最新的**；但若直接用 Android Studio 编译（不走脚本），
  `BandExercises.kt` 会停留在上次生成的状态。若你以后习惯用 Studio，可加一个
  `preBuild` 依赖去调 `tools/sync-band-exercises.js`。（2026-09-12 记）
- **（可选）动作表移除动作时的历史兜底**：`tools/sync-band-exercises.js` 检测到动作被从
  `data.js` 删掉时会打印提醒，让你去 `MuscleMap.kt` 的 `LEGACY_ALIASES` 补一条映射；
  这一步是**人工的**，不做的话旧记录会归到「其他」。

---

## ③ 已知的、暂不修的问题

完整清单见 [`REVIEW.md`](REVIEW.md)（按「严重级别 × 手机端 / 手环端」整理，共 35 项，其中 28 项未修）。
其中九条已在 `v3.1.10 ~ v3.1.16` 修掉：

- 同一天多次传输被覆盖（已修）
- 肌群页「周/月」是滚动窗口而非自然周期（已修）
- 手环读文件失败被静默当空库 → 整份覆盖（已修）
- 动作分类与手环不一致（26 个动作错 6 个）+ 分类有页面顺序依赖（已修，`v3.1.12`）
- 数据透视 `O(桶×序列×记录)` 且「周」档夹带 `SimpleDateFormat` 往返（已修，`v3.1.13`）
- 数据页「容量趋势」在「月」档日期倒序（新日期在左），且不像另外两档那样自动聚焦最新（已修，`v3.1.14`）
- 图标哑铃水平偏右 33.5px（Chrome 窗口宽度钳制导致渲染偏移，已修，`v3.1.15`）
- 组数 >6 组时容量汇总被 chips 挤压成竖排（数据页 + 日记页共用 `SetsLine`，已修，`v3.1.16`）
- PR 卡片动作横排过乱、无换算（改为肌群可折叠 + 每行一个动作 + 百分比换算，已改，`v3.1.16`）

---

## ④ 本机环境备忘（换会话 / 换机器时会撞上）

### push 必须走 Clash 代理，否则 TLS 握手被重置

**现象**：`git push` / `git ls-remote` 报
`fatal: unable to access '…': Recv failure: Connection was reset`

**原因**：git **没有配代理**（`http.proxy` / `https.proxy` 都是空），走直连时
TCP 能连上 `github.com:443`，但 HTTPS 握手阶段被重置。
本机 Clash 在 `127.0.0.1:7897` 监听（Gradle 的依赖下载已经在用它，见 `phone-app/gradle.properties`）。

**做法**：单次带代理推送，**不要改全局 git 配置**：

```powershell
git -c http.proxy=http://127.0.0.1:7897 -c https.proxy=http://127.0.0.1:7897 push origin main
```

### 其他已知坑

| 坑 | 处理 |
|---|---|
| 沙箱受限模式禁止命名管道 → git 读凭据失败（Win32 error 5） | push 需要提权（`danger-full-access`）才能读 Windows 凭据管理器 |
| 本机只有 Windows PowerShell 5.1，`.ps1` 不带 UTF-8 BOM 会按 GBK 解码 → 中文乱码 + 语法错误 | 用 `edit` 工具改完 `.ps1` 后**必须补回 BOM**（`edit` 会丢掉它）。已做成一键工具：`powershell -File tools\add-ps1-bom.ps1`（会补 BOM 并顺带做语法解析检查）。**2026-09-12 又踩了一次**：改完 `build-debug.ps1` 直接构建，报了一堆假的 `Unexpected token` |
| PowerShell 5.1 下 `$ErrorActionPreference='Stop'` 遇到原生命令写 stderr 会中止脚本 | 临时的原生命令调用要降级为 `Continue` + 判 `$LASTEXITCODE` |
| PowerShell 函数名撞内置别名会静默失效（例：函数 `RP` 被别名 `rp` = `Remove-ItemProperty` 抢走，绘图一格没画） | 函数命名避开别名（用 `New-*` / `Get-*` 之类） |
| `New-Object System.Drawing.Font('名字', 15*$x/1.35, …)` 会报 `op_Division` 失败 | 先把字号算进普通变量，再传给构造函数 |

