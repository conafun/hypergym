# 签名与通信约定（硬约束 · 禁止修改）

> **这是一条定死的规则：手环 rpk 的签名方式不再改动。**
> 任何版本修改、重构、优化都不许碰它。
> 违反它的后果**不是"装不上"，而是手环与手机之间彻底无法通信**。

---

## 一、规则本身

小米的 `system.interconnect`（XMS Wearable）要求：

> **手环快应用 `.rpk` 必须与手机 APK 使用同一张证书签名。**
> 两者被视为「同一个应用的两个部分」，包名与签名必须完全一致，否则连接失败。

依据：`interconnect.md` §2.1、§5。

---

## 二、绝对不要修改的文件与设置

| 文件 / 设置 | 作用 |
|---|---|
| `miband10pro-trainer/sign/release/private.pem` | 手环 rpk 的签名**私钥** |
| `miband10pro-trainer/sign/release/certificate.pem` | 手环 rpk 的签名**证书** |
| `phone-app/keystore/keystore.jks` | 手机 APK 的签名库 |
| `phone-app/keystore.properties` | 指向上面那个签名库 |
| `phone-app/app/build.gradle` 中 `signingConfigs` / `buildTypes` | 决定 APK 用哪张证书 |
| `miband10pro-trainer/src/manifest.json` 的 `package` 字段 | 必须等于手机 applicationId（`com.hypergym`） |

**不要**编辑、替换、重新生成、复制、移动以上任何文件。
也不要为了"顺手统一"而新建 `sign/debug/` 之类的东西 —— 那会改变 `aiot build` 选到的证书。

---

## 三、唯一的判据：三处指纹必须完全一致

```
EA:B2:34:44:C0:31:B8:14:AC:DF:37:2E:D0:4C:BC:13:0F:E7:16:C1:31:B1:E7:75:7B:BE:76:32:0C:9A:24:2F
```

| 对象 | 读取方式 |
|---|---|
| 手机 APK | `keytool -printcert -jarfile <apk>` |
| 手机签名库 | `keytool -list -v -keystore phone-app/keystore/keystore.jks -alias xmswearable` |
| 手环 release 证书 | `keytool -printcert -file miband10pro-trainer/sign/release/certificate.pem` |

（`keytool` 用 `.research/downloads/jdk21/jdk-21.0.4+7/bin/keytool.exe`；
`keystore.properties` 里 release 那组的 store password / alias 是 `xmswearable`。）

---

## 四、打包手环端只能用 release 模式

`aiot` 选择签名证书的逻辑在
`miband10pro-trainer/node_modules/@aiot-toolkit/aiotpack/lib/compiler/javascript/vela/utils/signature/SignUtil.js:38-68`：

```
build   模式：sign/debug/  →  sign/  →  toolkit 自带的调试证书   ← 会静默回退！
release 模式：sign/release/  →  sign/
```

本项目**只有 `sign/release/`**，所以：

| 命令 | 实际用的证书 | 结果 |
|---|---|---|
| `npm run release` | `sign/release/` = `EA:B2:34:44:…` | ✅ 与手机一致，可通信 |
| `npm run build` | 回退到 toolkit 内置 = `4E:8E:1E:E2:…` | ❌ 签名不匹配，**断连** |

**`build-debug.ps1` 已经固定用 `npm run release`，不要改回 `npm run build`。**

---

## 五、已经装好的两道防线

`build-debug.ps1` 在构建手环端时会：

1. **构建前**（`Assert-SigningMatches`）：读取手环 release 证书与手机 `keystore.jks` 的指纹，
   与上面第三节的约定值逐一比对。任何一处不符 → **直接报错中止，不开始构建**。
2. **构建后**：扫描 aiot 日志，若发现签名路径落在 `node_modules` 内（= 用了 toolkit 内置证书）
   → 报错中止。

也就是说：即使有人（包括未来的我）改错了证书或换错了命令，**构建会失败而不是产出坏包**。

---

## 六、事故记录（2026-09-10）

- **起因**：`build-debug.ps1` 最初用 `npm run build` 打包手环端，产出的 rpk 被 toolkit 静默地用
  内置调试证书签名。
- **现象**：手环与手机完全无法通信。**表象完全看不出是签名问题**，而且**源码 diff 也查不出来** ——
  因为证书根本不在源码里，`manifest.json` 只动了版本号两行，协议代码一个字没改。
- **定位**：对比三处证书指纹，发现手环 rpk 是 `4E:8E:1E:E2:…`，手机 APK 是 `EA:B2:34:44:…`。
- **佐证**：坏包的 `pages/home/index.js` 是 205,670 字节（development 不压缩），
  正确包是 41,556 字节、`META-INF/CERT` 2,622 字节 —— 与原来能通的包（41,471 / 2,623）几乎一致。
- **修复**：改用 `npm run release`，手环版本升到 1.0.66 重装即恢复通信。

**教训**：这类问题不留任何源码痕迹，**只能靠机制拦住，不能靠人记得**。
