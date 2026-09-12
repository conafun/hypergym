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

- （暂无。有新要求就往这里加，攒齐了一起改。）

---

## ③ 已知的、暂不修的问题

完整清单见 [`REVIEW.md`](REVIEW.md)（按「严重级别 × 手机端 / 手环端」整理，共 34 项，其中 28 项未修）。
其中三条最要紧的已在 `v3.1.10 / v1.0.67` 修掉：

- 同一天多次传输被覆盖（已修）
- 肌群页「周/月」是滚动窗口而非自然周期（已修）
- 手环读文件失败被静默当空库 → 整份覆盖（已修）

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
| 本机只有 Windows PowerShell 5.1，`.ps1` 不带 UTF-8 BOM 会按 GBK 解码 → 中文乱码 + 语法错误 | 用 `edit` 工具改完 `.ps1` 后**必须补回 BOM**（`edit` 会丢掉它） |
| PowerShell 5.1 下 `$ErrorActionPreference='Stop'` 遇到原生命令写 stderr 会中止脚本 | 临时的原生命令调用要降级为 `Continue` + 判 `$LASTEXITCODE` |
| PowerShell 函数名撞内置别名会静默失效（例：函数 `RP` 被别名 `rp` = `Remove-ItemProperty` 抢走，绘图一格没画） | 函数命名避开别名（用 `New-*` / `Get-*` 之类） |
| `New-Object System.Drawing.Font('名字', 15*$x/1.35, …)` 会报 `op_Division` 失败 | 先把字号算进普通变量，再传给构造函数 |

