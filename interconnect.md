markdown
# 小米手机（HyperOS）与小米手环 10 Pro（Vela OS）数据传输完整方案

> 适用设备：小米手机（HyperOS） + 小米手环 10 Pro（Vela OS）  
> 整理日期：2026-08-04

---

## 一、方案概览与选型建议

| 方案 | 推荐度 | 优点 | 缺点 / 限制 |
| :--- | :--- | :--- | :--- |
| **system.interconnect（官方）** | ⭐⭐⭐⭐⭐ | 官方稳定，双向通信，有示例 | 需保持手机 App 与快应用包名、签名一致 |
| **蓝牙（Bluetooth）** | ⭐⭐⭐ | 灵活，不依赖网络 | 开发复杂，需处理底层协议 |
| **网络请求（Fetch）** | ⭐⭐ | 实现简单 | 手环 10 Pro 可能不支持网络请求 |
| **WebSocket** | ⭐⭐ | 实时全双工 | 依赖网络，设备支持情况不明 |

**结论**：强烈建议优先采用官方 `system.interconnect` API。

---

## 二、system.interconnect API 详解

### 2.1 核心前提（必须满足）

- **包名一致**：手机 App 的包名（Android 应用 ID）与手环快应用 `manifest.json` 中的 `package` 字段必须完全一致。
- **签名一致**：手环快应用的 `.rpk` 安装包**必须使用手机 App 的安卓签名文件进行签名**。

> 即：手机 App 与手环快应用被视为**同一个应用的两个部分**，否则连接失败或闪退。

---

### 2.2 API 使用步骤

#### 1）声明模块

在快应用 `manifest.json` 中添加：

```json
{
  "name": "system.interconnect"
}
2）导入模块
javascript
import interconnect from '@system.interconnect';
// 或
const interconnect = require('@system.interconnect');
3）获取连接实例
javascript
const connect = interconnect.instance();
4）主要方法
方法	说明
connect.getReadyState(OBJECT)	获取连接状态，status 为 1 表示已连接，2 表示断开
connect.onOpen(CALLBACK)	注册连接成功回调
connect.onClose(CALLBACK)	注册连接断开回调
connect.onError(CALLBACK)	注册错误回调
connect.send(OBJECT)	向手机 App 发送数据（字符串或 ArrayBuffer）
connect.onMessage(CALLBACK)	注册接收手机 App 消息的回调
2.3 手环端代码示例
javascript
import interconnect from '@system.interconnect';

const connect = interconnect.instance();

// 监听连接成功
connect.onOpen(() => {
  console.log('与手机App连接成功');
  connect.send({ data: 'Hello from Band!' });
});

// 监听连接断开
connect.onClose(() => {
  console.log('与手机App连接断开');
});

// 监听错误
connect.onError((error) => {
  console.error('连接错误:', error);
});

// 监听手机发来的消息
connect.onMessage((data) => {
  console.log('收到手机消息:', data);
  // 处理数据
});

// 查询连接状态
connect.getReadyState({
  success: (res) => {
    console.log('状态:', res.status === 1 ? '已连接' : '已断开');
  },
  fail: (err) => {
    console.error('获取状态失败:', err);
  }
});
2.4 手机端注意事项
手机 App 需要实现对应的服务/模块，以响应手环的连接请求并处理收发数据。

手机 App 的包名和签名必须与手环快应用保持一致。

双向通信：手机端也可主动向手环发送数据。

三、官方示例与资源
跨设备图片传输示例：官方提供了完整 demo，演示了手环请求图片、手机以 Base64 分片传输、手环重组渲染的全过程。

官方文档地址：

Vela 快应用开发：https://iot.mi.com/vela/quickapp/

小米澎湃 OS 开发者平台：https://dev.mi.com/xiaomihyperos/quickapp-develop

四、开发与调试工具
IDE：使用官方定制的 AIoT-IDE（基于 VSCode）。

真机调试：官方“真机调试”可能仅对合作方开放；个人开发者可通过 AIoT-IDE 打包 .rpk，再使用第三方工具（如 AstroBox）侧载安装到手环上测试。

五、总结与建议
首选 system.interconnect，它是官方为跨设备通信设计的专用方案。

务必保证手机 App 与快应用的包名和签名完全一致，否则无法通信。

按照“声明模块 → 获取实例 → 注册监听 → 发送/接收数据”的流程编码。

参考官方图片传输示例，可快速理解完整通信架构。

若网络请求方案不可用，可考虑蓝牙作为备选，但开发复杂度更高。

六、注意事项
手环 10 Pro 可能不支持 fetch 网络请求，请勿依赖该方式。

所有开发工作请以最新官方文档为准。

测试时注意手环与手机蓝牙连接正常，且手机 App 处于运行状态。