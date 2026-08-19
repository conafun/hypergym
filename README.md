<p align="center">
  <img src="logo/1.png" width="120" alt="HyperGym Logo">
</p>

<h1 align="center">HyperGym</h1>

<p align="center">
  <strong>小米手环 10 Pro × 手机的力量训练追踪系统</strong><br>
  手环上记录每一组，手机上看到每一天
</p>

<p align="center">
  <img src="https://img.shields.io/badge/手环-Vela%20OS%20快应用-brightgreen" alt="Band">
  <img src="https://img.shields.io/badge/手机-Android%20Kotlin%20Compose-blue" alt="Phone">
  <img src="https://img.shields.io/badge/数据传输-XMS%20Wearable%20SDK-orange" alt="Transport">
  <img src="https://img.shields.io/badge/动作库-1324+个动作-red" alt="Exercises">
</p>

---

## 这是什么？

HyperGym 是一套**手环 + 手机**协同的力量训练记录系统：

- **手环端**（小米手环 10 Pro / Vela OS）：在手环上选择动作、记录每组重量和次数，长按历史记录一键发送到手机。
- **手机端**（Android / HyperOS）：接收数据后，以暖橙白卡风格的现代 UI 展示训练统计、肌群分布、训练日记和 1324+ 个标准动作的教学库。

---

## 功能特性

### 📊 数据总览（数据页）

- 热力图周历 + 月历网格，一目了然哪天练了
- 英雄卡（Hero Card）展示选定周期的总容量和环比趋势
- 容量趋势图（柱状图）+ 按月查看历史

### 💪 肌群分析（肌群页）

- 分组柱状图：按天/周展示各肌群训练容量
- 甜甜圈图：肌群训练占比
- 训练建议：根据数据自动提示薄弱肌群

### 📖 训练日记（日记页）

- 按日期倒序展示每天的训练详情
- PR（个人纪录）徽章自动识别并标记
- **长按卡片晃动 + 红色 ✕ 删除**：可删除错误记录，删除后数据/肌群页联动刷新

### 🏋️ 动作库（动作页）

- 内置 **1324+ 个标准健身动作**（中文名称 + 分步教学）
- 支持按肌群分类筛选（胸/肩/背/腿/臂/核心/有氧/颈）
- 每个动作配有 **ExoPlayer 无缝循环视频** 演示

### 📡 数据传输（传输页）

- 蓝牙连接状态实时显示
- P/S 调试码日志，方便开发排查
- SAF 安全目录存储：卸载重装数据不丢

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
│  数据页 · 肌群页 · 日记页    │
│  动作库 · 传输页             │
└──────────────────────────────┘
```

---

## 技术栈

| 组件 | 技术 | 版本 |
|---|---|---|
| **手机端 UI** | Jetpack Compose + Material 3 | BOM 2025.05.01 |
| **图表** | Vico（Canvas 自绘柱状图/甜甜圈图） | 2.1.4 |
| **视频播放** | Media3 ExoPlayer（硬解循环播放） | 1.4.1 |
| **数据传输** | xms-wearable-lib（XMS Wearable SDK） | 1.4 |
| **数据存储** | SAF 目录 + JSONL 分片文件（每片 30 行） | — |
| **手环端** | Vela OS 快应用（aiot-toolkit） | 2.0.5 |
| **开发语言** | Kotlin 2.1 / JavaScript (快应用) | — |
| **构建工具** | Gradle + AGP | 8.2 / 8.2.2 |
| **最低 Android** | minSdk 21 (Android 5.0) | — |

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
│   │       │   │   ├── RecordStore.kt    # 存储引擎（分片/去重/压缩）
│   │       │   │   ├── DataBackend.kt    # 存储后端抽象（SAF / 内部）
│   │       │   │   ├── StatsEngine.kt    # 统计引擎（纯函数）
│   │       │   │   ├── MuscleMap.kt      # 动作 → 肌群映射
│   │       │   │   └── ExerciseLibrary.kt # 动作库加载 + 中文翻译
│   │       │   └── ui/
│   │       │       ├── HyperGymApp.kt    # 根界面（5 Tab + Pager）
│   │       │       ├── Theme.kt          # 暖橙主题色 + 装饰背景
│   │       │       ├── DashboardScreen.kt # 数据页（热力图/趋势）
│   │       │       ├── MuscleScreen.kt   # 肌群页（柱状图/甜甜圈）
│   │       │       ├── DiaryScreen.kt    # 日记页（长按删除）
│   │       │       ├── ExerciseScreen.kt # 动作库（搜索/视频/教学）
│   │       │       ├── Charts.kt         # Canvas 图表组件
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

- **去重**：以 `date` 为主键，同日期后到覆盖先到
- **分片**：每 30 条记录一个 `records-NNNN.jsonl` 文件，写满自动开新片
- **压缩**：超过 1200 行自动重写（去重后重新分片）
- **持久化**：SAF 目录（用户授权的公共文件夹），卸载重装数据不丢

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

**暖橙 + 白卡 + 柔和阴影**

| 元素 | 色值 | 用途 |
|---|---|---|
| Primary | `#E06040` | 按钮、图表高亮、容量数字 |
| PrimaryDeep | `#C84E32` | 渐变尾部 |
| PrimaryLight | `#F2A58D` | 渐变中间 |
| Blue | `#7090E0` | 辅助图表 |
| Green | `#3E9E7B` | 正向/达标 |
| Background | `#F4F5F7` | 页面底色 |
| Card | `#FFFFFF` | 白色卡片（纯白） |

---

## 许可证

本项目为个人学习/研究用途，暂不开源。如需引用或合作，请联系作者。
