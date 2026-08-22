<p align="center">
  <img src="logo/1.png" width="120" alt="HyperGym Logo">
</p>

<h1 align="center">HyperGym</h1>

<p align="center">
  <strong>小米手环 10 Pro × 小米手机的力量训练追踪系统</strong><br>
  手环上记录每一组，手机上看到每一天
</p>

<p align="center">
  <img src="https://img.shields.io/badge/手环-Vela%20OS%20快应用-brightgreen" alt="Band">
  <img src="https://img.shields.io/badge/手机-Android%20Kotlin%20Compose-blue" alt="Phone">
  <img src="https://img.shields.io/badge/数据传输-XMS%20Wearable%20SDK-orange" alt="Transport">
  <img src="https://img.shields.io/badge/动作库-1324+个动作-red" alt="Exercises">
</p>

---

## 界面预览

<table>
<tr>
  <td><img src="screenshots/data.png" width="220"></td>
  <td><img src="screenshots/muscle.png" width="220"></td>
  <td><img src="screenshots/muscle_pie.png" width="220"></td>
</tr>
<tr>
  <td align="center">数据总览</td>
  <td align="center">肌群分析 · 动作汇总 + 数据透视</td>
  <td align="center">肌群分析 · 透视卡片 + 肌群占比</td>
</tr>
<tr>
  <td><img src="screenshots/diary.png" width="220"></td>
  <td><img src="screenshots/exercise.png" width="220"></td>
  <td><img src="screenshots/exercise_detail.png" width="220"></td>
</tr>
<tr>
  <td align="center">训练日记</td>
  <td align="center">动作库（1324 个动作）</td>
  <td align="center">动作详情（教学 + 视频）</td>
</tr>
<tr>
  <td><img src="screenshots/transfer.png" width="220"></td>
  <td colspan="2" align="center">传输页（手环联调 · 数据流日志）</td>
</tr>
</table>

> 截图来自 Android 模拟器（内置示例训练数据），UI 为「暖橙 + 白卡 + 柔和阴影」风格。

---

## 这是什么？

HyperGym 是一套**手环 + 手机**协同的力量训练记录系统：

- **手环端**（小米手环 10 Pro / Vela OS）：在手环上选择动作、记录每组重量和次数，长按历史记录一键发送到手机。
- **手机端**（Android / HyperOS）：接收数据后，以暖橙白卡风格的现代 UI 展示训练统计、肌群分布、训练日记和 1324+ 个标准动作的教学库。

当前版本：**v3.1**（versionCode 7），底部 5 个页签：**数据 / 肌群 / 日记 / 动作 / 传输**。

---

## 功能特性

### 📊 数据总览（数据页）

![数据页](screenshots/data.png)

- **训练热力图**：周历 + 「展开整月」月历网格，颜色深浅代表当天容量，一眼看出哪天练了。
- **当天训练卡片**：展示所选日期的动作明细 + 当日总容量。
- **范围切换**：周 / 月 / 全部（pills），切换上方英雄卡与统计。
- **英雄卡**：本周/本月总容量 + 环比趋势（↑/↓ vs 上期）。
- **统计卡片**：训练次数、总组数等关键指标。

### 💪 肌群分析（肌群页）

![肌群页](screenshots/muscle.png)

- **动作数据汇总**：按天分组的**细柱状图**，同一天不同动作不同色；**点击某一天的柱状图**，卡片底部按当天数据显示图例（可换行，两行内完整展示），再点可收起。
- **数据透视卡片**（页面第 2 张卡，可自定义）：像数据透视表一样组合维度与指标——
  - **X轴维度**：日期 / 周 / 肌群 / 动作
  - **Y轴指标**：总容量 / 总组数 / 总次数 / 平均重量 / 平均次数
  - **聚合方式**：总和 / 平均
  - **单项 / 多项**：单项显示一条汇总序列；多项按「明细维度」（动作 / 肌群）拆成多条彩色序列
  - **图表类型**：柱状 / 折线，**柱从底部生长、折线从左向右展开**的微动画
  - 所有选项都在卡片底部以上拉/分段控件选择

![肌群占比](screenshots/muscle_pie.png)

- **本周肌群占比**：甜甜圈图 + 各肌群占比清单（容量 + 百分比），与上述卡片共用一套配色。

### 📖 训练日记（日记页）

![日记页](screenshots/diary.png)

- 按日期倒序展示每天训练详情，每张卡片**完整显示 3 个动作**，超出部分**卡片内上下滚动**。
- **PR（个人纪录）徽章**自动识别并标记。
- **长按卡片晃动 + 右上角红色 ✕**：可删除错误记录；再长按一次退出删除状态。删除后**数据 / 肌群 / 日记页联动刷新**，对应日期的 JSON 行也从 `records-NNNN.jsonl` 数据文件中同步移除。

### 🏋️ 动作库（动作页）

![动作库](screenshots/exercise.png)

- 内置 **1324+ 个标准健身动作**（**纯中文**名称 + 分步教学），与手环动作列表**完全解耦**。
- 支持按肌群分类筛选（全部 / 胸 / 肩 / 背 / 腿 / 臂 / 核心 / 有氧 / 颈）+ 关键词搜索。
- 点击动作进入详情：**训练信息**（部位 / 目标肌群 / 主肌群 / 次要肌群 / 器材）+ **分步教学** + **ExoPlayer 无缝循环视频**演示。

![动作详情](screenshots/exercise_detail.png)

### 📡 数据传输（传输页，原「诊断」）

![传输页](screenshots/transfer.png)

- 蓝牙连接状态实时显示。
- **P/S 调试码日志**，方便排查（P01-P17 / S01-S08 等）。
- **SAF 安全目录存储**（卸载重装数据不丢）为主，内部存储兜底。
- 操作按钮：重连 / 测试发送 / 清除 / 数据目录 / 重扫目录。

---

## 手环端界面（Vela 快应用）

手环端为 `miband10pro-trainer`（Vela OS 快应用，`com.hypergym`，versionName **1.0.61**）。主界面是一个横向滑动容器，**左右滑动切换 5 屏**，记录完成后进入「训练完成」汇总页。

<table>
<tr>
  <td><img src="screenshots/band-0-plan.png" width="200"></td>
  <td><img src="screenshots/band-1-records.png" width="200"></td>
  <td><img src="screenshots/band-2-today.png" width="200"></td>
</tr>
<tr>
  <td align="center">屏0 · 计划</td>
  <td align="center">屏1 · 记录列表</td>
  <td align="center">屏2 · 今日数据</td>
</tr>
<tr>
  <td><img src="screenshots/band-3-history.png" width="200"></td>
  <td><img src="screenshots/band-4-debug.png" width="200"></td>
  <td><img src="screenshots/band-5-summary.png" width="200"></td>
</tr>
<tr>
  <td align="center">屏3 · 历史</td>
  <td align="center">屏4 · 联调诊断</td>
  <td align="center">训练完成 · 汇总</td>
</tr>
</table>

> 界面图按 `.ux` 源码精确还原（黑底 + 绿色主操作色），数据为示例。

- **屏0 · 计划**：上下滑动切换 9 个训练动作，左右加减重量（按各动作步进），「增加」加入计划。
- **屏1 · 记录列表**：显示已加入计划的项目与已完成组数；点击项目开始计次（`-` / 次数 / `+` / 确认 / 取消）；长按项目编辑重量（`- / +` / 确定 / 删除）；「结束」保存并进入汇总。
- **屏2 · 今日数据**：今日各动作的训练明细 + 当日总容量。
- **屏3 · 历史**：按日期列出历史训练（每条含动作与容量），底部「本月 X 天 · 总 Xkg」；**长按日期即发送当天数据到手机**。
- **屏4 · 联调诊断**：B00/B01… 调试码日志 +「诊断 / 发Ping / 重连」按钮，用于排查互联互通。
- **训练完成 · 汇总**：记录结束后展示本次「动作 / 总组数 / 总容量」+ 每个动作的逐组明细，「返回主页」。

---

## 系统架构

```
┌──────────────────────────────┐
│     小米手环 10 Pro          │
│     (Vela OS 快应用)         │
│                              │
│  选择动作 → 输入重量/组数    │
│  → 本地保存 → 长按发送      │
└──────────┬───────────────────┘
           │  @system.interconnect
           │  (XMS Wearable SDK)
           ▼
┌──────────────────────────────┐
│     Android 手机 App          │
│     (Kotlin + Compose)       │
│                              │
│  接收 JSON → 按日期去重      │
│  → JSONL 分片存储 (30条/片)  │
│  → Compose UI 实时渲染       │
│                              │
│  数据 · 肌群 · 日记          │
│  动作 · 传输                 │
└──────────────────────────────┘
```

---

## 技术栈

| 组件 | 技术 | 版本 |
|---|---|---|
| **手机端 UI** | Jetpack Compose + Material 3 | BOM 2025.05.01 |
| **图表** | Canvas 自绘柱状图 / 甜甜圈图 / 折线图（Charts.kt / PivotCard.kt） | — |
| **视频播放** | Media3 ExoPlayer（硬解无缝循环） | 1.4.1 |
| **数据传输** | xms-wearable-lib（XMS Wearable SDK） | 1.4 |
| **数据存储** | SAF 目录 + JSONL 分片文件（每片 30 行） | — |
| **手环端** | Vela OS 快应用（aiot-toolkit） | 2.0.5 |
| **开发语言** | Kotlin 2.1 / JavaScript (快应用) | — |
| **构建工具** | Gradle + AGP | 8.2 / 8.2.2 |
| **最低 Android** | minSdk 21 (Android 5.0) | — |
| **应用版本** | versionName 3.1 / versionCode 7 | — |

---

## 项目结构

```
hypergym/
├── phone-app/                      # 手机端 Android 项目
│   ├── app/
│   │   ├── build.gradle            # 依赖配置
│   │   └── src/main/
│   │       ├── java/com/hypergym/
│   │       │   ├── MainActivity.kt      # 蓝牙连接 + 数据接收
│   │       │   ├── data/
│   │       │   │   ├── TrainingData.kt   # 数据模型 + JSON 解析
│   │       │   │   ├── RecordStore.kt    # 存储引擎（分片/去重/压缩/删除）
│   │       │   │   ├── DataBackend.kt    # 存储后端抽象（SAF / 内部）
│   │       │   │   ├── StatsEngine.kt    # 统计引擎（纯函数）
│   │       │   │   ├── MuscleMap.kt      # 动作 → 肌群映射
│   │       │   │   └── ExerciseLibrary.kt # 动作库加载 + 中文翻译
│   │       │   └── ui/
│   │       │       ├── HyperGymApp.kt    # 根界面（5 Tab + Pager）
│   │       │       ├── Theme.kt          # 暖橙主题色 + 装饰背景 + 统一调色板
│   │       │       ├── DashboardScreen.kt # 数据页（热力图/英雄卡/趋势）
│   │       │       ├── MuscleScreen.kt   # 肌群页（动作汇总/肌群占比）
│   │       │       ├── PivotCard.kt      # 数据透视卡片（自定义 X/Y）
│   │       │       ├── DiaryScreen.kt    # 日记页（长按删除 + 3 动作卡片）
│   │       │       ├── ExerciseScreen.kt # 动作库（搜索/视频/教学）
│   │       │       ├── Charts.kt         # Canvas 自绘图表组件
│   │       │       ├── Components.kt     # 通用 UI 组件
│   │       │       └── DateUtils.kt      # 日期工具
│   │       └── assets/
│   │           └── exercises/            # 动作库资源
│   │               ├── exercises.json    # 1324 个动作数据
│   │               ├── videos/          # 动作演示视频
│   │               └── images/          # 动作缩略图
│   └── libs/                             # xms-wearable-lib.aar
│
├── miband10pro-trainer/            # 手环端 Vela 快应用
│   ├── src/
│   │   ├── manifest.json           # 快应用清单（package: com.hypergym）
│   │   ├── common/data.js          # 9 个训练动作定义
│   │   └── pages/home/             # 首页 UI
│   └── package.json
│
├── 健身动作库/                      # 原始动作库（1324 个动作 + 视频 + 图片）
├── ui-prototype/                    # Web UI 原型（暖橙白卡风格）
├── ui-refs/                         # 参考设计图
├── screenshots/                     # 本 README 用到的界面截图
├── logo/                            # App 图标
├── .gitignore                       # Git 忽略规则
└── README.md                        # 本文件
```

---

## 快速开始

### 手机端（Android App）

**环境要求：**

- JDK 21
- Android SDK（compileSdk 36, buildTools 36.1.0）
- Gradle 8.2

**构建步骤：**

```bash
# 进入手机端项目目录
cd phone-app

# 设置环境变量（Linux/Mac）
export JAVA_HOME=/path/to/jdk21
export ANDROID_HOME=/path/to/android-sdk

# 构建 Release APK
./gradlew assembleRelease

# APK 输出路径
# phone-app/app/build/outputs/apk/release/app-release.apk
```

> Windows 用 `gradlew.bat` 替换 `./gradlew`。

### 手环端（Vela 快应用）

**环境要求：**

- Node.js >= 8.10
- aiot-toolkit >= 2.0.5

**构建步骤：**

```bash
cd miband10pro-trainer
npm install
npm run build    # 构建
npm run release  # 发布 rpk 包
```

---

## 数据流详解

### 传输协议（手环 → 手机）

手环通过 `@system.interconnect` 发送 JSON：

```json
{
  "type": "training-records",
  "date": "2026-08-15",
  "records": [
    {
      "exercise": "杠铃深蹲",
      "weight": 80,
      "sets": [
        { "set": 1, "reps": 8, "volume": 640 },
        { "set": 2, "reps": 8, "volume": 640 },
        { "set": 3, "reps": 6, "volume": 480 }
      ]
    }
  ]
}
```

### 存储机制

- **去重**：以 `date` 为主键，同日期后到覆盖先到。
- **分片**：每 30 条记录一个 `records-NNNN.jsonl` 文件，写满自动开新片。
- **压缩**：超过 1200 行自动重写（去重后重新分片）。
- **持久化**：SAF 目录（用户授权的公共文件夹），卸载重装数据不丢。
- **删除联动**：日记页删除某天记录后，内存索引 + 数据文件同步重写，数据/肌群页图表立即刷新。

---

## 手环内置动作（9 个）

| 动作 | 重量范围 (kg) | 步进 (kg) |
|---|---|---|
| 卧推 | 40 – 90 | 5 |
| 杠铃深蹲 | 40 – 90 | 5 |
| 六角杠铃 | 50 – 90 | 5 |
| 哑铃 | 10 – 50 | 2 |
| 高位下拉 | 20 – 50 | 2 |
| 坐姿划船 | 20 – 50 | 2 |
| 大剪刀 | 30 – 60 | 5 |
| 倒蹬 | 40 – 80 | 10 |
| T杠划船 | 15 – 40 | 5 |

> 手机端动作库（1324 个动作）与手环动作列表**完全解耦**，手机端动作库是一个独立的参考教学页面。

---

## 设计语言

**暖橙 + 白卡 + 柔和阴影**（区块化）

| 元素 | 色值 | 用途 |
|---|---|---|
| Primary | `#E06040` | 按钮、图表高亮、容量数字 |
| PrimaryDeep | `#C84E32` | 渐变尾部 |
| PrimaryLight | `#F2A58D` | 渐变中间 |
| Blue | `#7090E0` | 辅助图表 |
| Green | `#3E9E7B` | 正向/达标 |
| Background | `#F4F5F7` | 页面底色 |
| Card | `#FFFFFF` | 白色卡片（纯白） |

所有图表的分类配色使用同一套 `ChartPalette`，保证同页各卡片风格一致。

---

## 许可证

本项目为个人学习/研究用途，暂不开源。如需引用或合作，请联系作者。
