# HyperGym 手机互通功能技术方案

> 版本：v1 初稿（2026-08-04）
> 适用：小米手环 10 Pro（Vela JS 快应用）+ 小米手机（HyperOS / Android App）
> 目标：手环端收集训练数据，通过 interconnect 同步到手机端，手机端图表化展示

---

## 一、命名规范（统一）

| 项目 | 名称 |
| :--- | :--- |
| 应用名（显示名） | **HyperGym** |
| 快应用包名（manifest.json `package`） | **com.hypergym** |
| 手机 App 安卓包名 | **com.hypergym**（必须与快应用一致） |

> 包名 + 签名一致是 interconnect 通信的**硬性前提**，否则连接被拒绝。

---

## 二、技术选型

| 方案 | 结论 |
| :--- | :--- |
| **system.interconnect（官方）** | ✅ 唯一推荐：官方稳定、双向通信、自动建连、有官方 demo |
| 蓝牙自研 | ❌ 需处理底层协议，开发复杂，不采用 |
| fetch / WebSocket | ❌ 手环 10 Pro 不支持网络请求，不可用 |

---

## 三、interconnect API 速查（手环端）

### 3.1 声明与导入

```json
// manifest.json features 中追加
{ "name": "system.interconnect" }
```

```js
import interconnect from '@system.interconnect'
const connect = interconnect.instance()  // 单例连接对象
```

### 3.2 方法

| API | 说明 |
| :--- | :--- |
| `connect.getReadyState({success, fail})` | 状态：`status` 1=已连接，2=断开；错误码 1006=连接断开 |
| `connect.diagnosis({timeout, success, fail})` | 诊断：0=OK，204=超时，1001=对端应用未安装，1000=其他错误 |
| `connect.send({data, success, fail})` | 发数据，`data` 支持对象/字符串/ArrayBuffer；错误码 204=超时，1006=断开 |

### 3.3 事件（**注意：属性赋值，不是方法注册**）

| 事件 | 回调参数 | 说明 |
| :--- | :--- | :--- |
| `connect.onopen = fn` | `{isReconnected}` | 连接打开（自动建连，无需手动创建） |
| `connect.onclose = fn` | `{code, data}` | 连接关闭 |
| `connect.onerror = fn` | `{code, data}` | 出错（1000 未知 / 1001 对端未装 / 1006 断开） |
| `connect.onmessage = fn` | `{data}` | 收到手机端数据 |

### 3.4 示例

```js
connect.onopen = (d) => { connect.send({ data: { cmd: 'hello', app: 'hypergym' } }) }
connect.onmessage = (d) => { /* d.data 为手机发来的内容 */ }
```

---

## 四、签名与打包（做互通时启用）

### 4.1 前提
- 手环 rpk **必须使用手机 App 的安卓签名（.jks）打包**，通信前会校验签名，不一致直接拒绝。
- 未做互通之前，可以继续用工具默认自签名。

### 4.2 jks 转 pem 流程

```
# 1) jks → p12
keytool -importkeystore -srckeystore keystore.jks -destkeystore keystore.p12 -srcstoretype jks -deststoretype pkcs12

# 2) p12 → pem
openssl pkcs12 -nodes -in keystore.p12 -out keystore.pem

# 3) 拆分：
#    私钥  (-----BEGIN PRIVATE KEY----- ...)      → sign/debug/private.pem
#    证书  (-----BEGIN CERTIFICATE----- ...)      → sign/debug/certificate.pem
#    release 目录同理，放 sign/release/
```

### 4.3 在线工具（免装 openssl）
官方在线签名生成工具：https://cdn.hybrid.xiaomi.com/aiot-ide/signature-generate-tool/v2/index.html
（上传 p12 + 密码 → 生成签名 → 下载 pem）

### 4.4 签名注意事项
- 手机 App 与 rpk **必须用同一份证书**，否则无法通信。
- 妥善保管证书，**每次用相同证书**打 release 包；证书改变可能无法上架。

---

## 五、关键注意事项（踩坑清单）

1. **模拟器测不了互联**：需要外接蓝牙适配器，配置复杂，**必须真机调试**。
2. **连接状态要轮询**：进页面直接获取往往拿到 DISCONNECTED，需定时 `getReadyState` 轮询。
3. **连接自动建立**：不用管创建/销毁，只需注册回调。
4. **手机端排查**：`adb logcat` 看手机端收数据情况；手环端先检查 send 数据结构 + send 回调执行情况。
5. **真机装新包前先卸载旧包**（Debug 页需输入完整包名，否则卸载不彻底，旧图标残留）。
6. **包名变更影响**：数据路径为 `/data/quickapp/files/<包名>/`，改包名后旧数据不跟随，历史记录需用 adb 迁移或重新录入。
7. **rpk 真机安装方式**：小米运动健康 → 我的 → 关于 → Debug → 第三方应用 → 输入包名 → Install third app。

---

## 六、数据协议草案（手环 → 手机）

### 6.1 单条记录格式（基于现有训练数据结构）

```json
{
  "cmd": "sync_records",
  "version": 1,
  "date": "2026-08-04",
  "records": [
    {
      "exercise": "卧推",
      "weight": 60,
      "sets": 4,
      "reps": 10,
      "vol": 2400,
      "ts": 1722740000000
    }
  ]
}
```

### 6.2 传输策略
- **全量同步**（首版）：训练记录量级小（每天几十条，总量几百 KB），每次全量发送最简单可靠。
- **分片传输**（后续可选）：参照官方跨设备图片传输 demo 的 header→data→end 三阶段协议，大文件（如图片/完整备份）分片收发、带进度。

### 6.3 增量同步（后续优化）
- 手机端按日期去重：手环发"最新数据日期索引"，手机端拉缺失日期；或手环端维护 sync 时间戳。

### 6.4 手机 → 手环
- 下发训练计划模板、查询命令、请求指定日期数据。

---

## 七、手机 App 规划（数据展示 + 图表化）

### 7.1 技术栈建议
- Kotlin + Jetpack Compose（或传统 View）
- 图表库：MPAndroidChart（成熟稳定）或 Vico（较新）

### 7.2 页面规划
| 页面 | 内容 |
| :--- | :--- |
| 总览页 | 最近训练汇总：训练次数、总组数、总容量（kg） |
| 趋势页 | 周/月容量折线图、各动作训练频率柱状图 |
| 详情页 | 单次训练明细；单动作进步曲线（重量/组数/次数变化） |
| 设置页 | 同步状态、连接状态、数据管理（导出/清空） |

### 7.3 手机端通信实现
- 安卓端实现与手环 rpk **同包名、同签名**的接收服务，对接小米穿戴第三方 APP 能力开放接口。
- 官方文档：《小米穿戴第三方APP能力开放接口文档》（见第九节链接）。

---

## 八、集成路线图

| 阶段 | 内容 |
| :--- | :--- |
| 阶段 0（现在） | 保持手环端 APP 稳定；**统一包名 com.hypergym**；迁移旧数据 |
| 阶段 1 | 手环端 interconnect 骨架：声明模块、实例化、事件监听、状态显示、发送测试数据 |
| 阶段 2 | 手机 App 最小工程（同包名+签名），真机打通双向收发 |
| 阶段 3 | 数据协议完善：全量同步训练记录 → 分片/增量/错误重试 |
| 阶段 4 | 手机端图表化展示：总览、趋势、详情页 |
| 阶段 5 | 双向增强：手机下发计划模板、远程指令等 |

---

## 九、参考资源

- 官方 interconnect 文档：https://iot.mi.com/vela/quickapp/zh/features/network/interconnect.html
- 跨设备图片传输示例：https://iot.mi.com/vela/quickapp/zh/samples/
- interconnect 开发测试 demo：https://cdn.cnbj3-fusion.fds.api.mi-img.com/quickapp-vela/interconnect_dev_test_demo.zip
- 小米穿戴第三方 APP 能力开放接口文档：https://vela-docs.cnbj1.mi-fds.com/vela-docs/files/小米穿戴第三方APP能力开放接口文档_1.4.pdf
- 在线签名生成工具：https://cdn.hybrid.xiaomi.com/aiot-ide/signature-generate-tool/v2/index.html
- 开发 FAQ（签名/通信排查）：https://iot.mi.com/vela/quickapp/zh/guide/other/faq.html
