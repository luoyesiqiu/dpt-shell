# dpt-profile 详细说明（方案 B）

## 1. 设计目标和硬边界

`dpt-profile` 把壳代码的身份、编译产物和消费入口从共享目录中拆开：每个 `applicationId` 有一个稳定的 `profileId`，每次壳代码变更生成一个新的 `revisionId`。最终交付的是一个可复制的单文件 `*.dptprofile`。

本变更只改变 Profile 的封装和选择方式，不改变 DPT 既有的 DEX/CodeItem、配置解密、密钥派生、JNI 语义、ClassLoader 或 Android 启动协议。

硬边界：

- 只保证 APK；AAB 不进入本变更的生成、消费和验收链路。
- 首版只保证 `arm64-v8a`。APK 含业务 Native 库时必须带 `arm64-v8a`，没有该 ABI 会在写入 APK 前失败。
- 渠道包不属于 Profile 生命周期；Profile 先完成加固，渠道包再由既有 Packer-NG 流程处理。
- Profile 不包含签名私钥、签名密码、渠道配置、扫描样本或远程下载逻辑。
- Profile 必须预先创建；加固时不会自动创建、切换或重建 Profile。

## 2. 产物和目录

### 2.1 源状态（只在生成/审计环境使用）

```text
profiles/
  index.json                         # applicationId -> profileId
  source/<profileId>/
    profile.yaml                     # Profile 源配置
    identity.lock.json               # 稳定身份、类名、资产名、build-key
    overlay/                         # 可选、受限 Overlay
  revisions/<profileId>/<revisionId>/
    revision.json                    # Revision 状态和版本
    reports/                         # 目录型发布报告
  candidates/<profileId>/<revisionId>/bundle/
    profile.json
    revision.json
    artifact-sha256.json
    shell-files/
      build-key
      dex/classes.dex
      libs/arm64/<profile-so>.so     # 源 Bundle 的历史路径
```

源目录用于生成和门禁，不需要随正式 APK 加固一起复制到服务器。

### 2.2 可移植 Profile 文件

`.dptprofile` 是固定条目顺序、固定时间戳和 STORED ZIP 条目的单文件归档：

```text
manifest.json
profile.json
revision.json
artifact-sha256.json
checksums.sha256
shell-files/build-key
shell-files/dex/classes.dex
shell-files/libs/arm64-v8a/<profile-so>.so
reports/gate-report.json
reports/scan-baseline.json
reports/scan-candidate.json
```

候选文件名必须为 `*.candidate.dptprofile`，清单状态为 `candidate`；正式文件名必须为 `*.dptprofile`，清单和 Revision 状态均为 `verified`。正式文件由 promote 产生并嵌入三份报告。

归档 Reader 会在解包前检查路径、重复条目、大小和 SHA-256，再复用现有 Profile 契约、DEX、ELF/JNI 校验。任何失败都不会把文件写到 APK 工作目录之外。

## 3. 推荐工作流

```text
目标 APK applicationId
        │
        ▼
profileCreate（稳定身份，一次）
        │
        ▼
profileBuild + Native 构建（候选 Revision）
        │
        ▼
profilePack（*.candidate.dptprofile）
        │       + gate-report / scan-baseline / scan-candidate
        ▼
profilePromote（*.dptprofile，verified）
        │
        ▼
dpt -f input.apk --profile app.dptprofile
        │
        ▼
zipalign / 签名 / 验签 / 可选渠道包
```

### 3.1 创建稳定 Profile

```bash
bash ./gradlew --no-daemon profileCreate \
  -PprofileApplicationId=com.example.app
```

首次执行生成 `profileId`、身份锁和源配置；同一应用再次执行返回原绑定且不轮换身份。不同 `applicationId` 必须创建不同 Profile。

### 3.2 构建候选 Revision

```bash
bash ./gradlew --no-daemon profileBuild \
  -PprofileApplicationId=com.example.app \
  -PprofileRecipe=recipe-a \
  -PprofileBuildNative=true
```

`profileBuild` 必须使用已验证 Recipe，并将生成源码、CMake、R8 规则和候选 Bundle 写入隔离目录。`profileBuildNative=true` 会编译 arm64 壳 SO；不打开时只会保留延迟提示，不能拿来生成正式 Profile。

需要受限 Overlay 时：

```bash
-PprofileOverlayDir=/绝对路径/overlay
```

Overlay 只允许启动协调、SO 定位/加载、路径/配置适配和 Profile 私有辅助代码；固定代理入口、JNI 注册/签名、密钥和启动协议不可覆盖。

### 3.3 打包候选文件

候选 Bundle 通常位于：

```text
<profileStateDir>/candidates/<profileId>/<revisionId>/bundle
```

```bash
bash ./gradlew --no-daemon profilePack \
  -PprofileBundleDir=/绝对路径/.../bundle \
  -PprofileArchiveOut=/绝对路径/com.example.app.candidate.dptprofile
```

`profilePack` 不需要注册表参数，不修改源 Bundle、Revision、活动指针或已发布文件；相同输入重复打包应得到相同 SHA-256。输出已存在时严格失败。

服务器没有 Gradle 环境时可以直接运行：

```bash
java -cp <dpt-runtime-classpath> \
  com.luoye.dpt.profile.cli.ProfileCommandMain pack \
  --bundle /绝对路径/bundle \
  --out /绝对路径/com.example.app.candidate.dptprofile
```

### 3.4 门禁和晋级

报告目录至少包含：

```text
gate-report.json
scan-baseline.json
scan-candidate.json
```

三份报告必须绑定同一个 `profileId`、`revisionId` 和候选 APK SHA-256；两份扫描报告的 `applicationId`、扫描器集合和时间窗口必须一致。全部硬门槛通过后：

```bash
bash ./gradlew --no-daemon profilePromote \
  -PprofileCandidate=/绝对路径/com.example.app.candidate.dptprofile \
  -PprofileReportDir=/绝对路径/reports \
  -PprofileArchiveOut=/绝对路径/com.example.app.dptprofile
```

`profilePromote` 会重新读取并验证 candidate，执行 `ProfileScanGate` 和 `ProfileUnifiedGate`，通过后原子生成正式文件。失败不会生成/替换正式文件，也不会修改注册表、活动指针或 candidate。

### 3.5 验证正式文件

```bash
bash ./gradlew --no-daemon profileValidate \
  -PprofileArchive=/绝对路径/com.example.app.dptprofile

# 可选绑定一个 APK，核对 applicationId 和业务 ABI
bash ./gradlew --no-daemon profileValidate \
  -PprofileArchive=/绝对路径/com.example.app.dptprofile \
  -PprofileApk=/绝对路径/input.apk
```

也可直接调用：

```bash
java -cp <dpt-runtime-classpath> \
  com.luoye.dpt.profile.cli.ProfileCommandMain validate \
  --profile /绝对路径/com.example.app.dptprofile \
  --apk /绝对路径/input.apk
```

`validate` 不读取 Profile 注册表；candidate 文件会被拒绝为不可消费文件。

## 4. APK 显式消费

```bash
java -jar /绝对路径/dpt.jar \
  -f /绝对路径/input.apk \
  -o /绝对路径/input_protected.apk \
  --profile /绝对路径/com.example.app.dptprofile
```

`--profile-file` 是同义别名。显式模式的调用链为：

```text
Dpt.parseOptions
    -> Apk.Builder.profileArchive
    -> Apk.resolveProfileInputs
    -> ProfileArchiveReader.readVerified
    -> ApkProfileInputValidator（Manifest / 业务 ABI）
    -> AndroidPackage 既有 DEX/SO/配置/交付流程
```

关键行为：

1. APK 解包后先读取 Manifest `applicationId` 和 `lib/<abi>`；再读取 Profile 文件。
2. `applicationId`、Profile/Revision、协议、生成器/核心版本、artifact/checksums、DEX/JNI、arm64 ELF 和业务 ABI 全部通过后，才允许写 Manifest、DEX、SO 或配置。
3. 壳文件解包到当前 APK 的随机工作目录；`copyNativeLibs` 只从该归档目录复制，不读取全局 `shell-files`。
4. 任务结束清理随机目录，正式 Profile 文件保持只读且 SHA-256 不变。
5. 未指定 `--profile` 时，旧程序化入口继续使用注册表 + 活动 Revision 的目录 Bundle，便于迁移和回归。

错误码示例：

| 场景 | 错误码 |
| --- | --- |
| Profile 目标 applicationId 不匹配 | `DPT-PROFILE-2002` |
| 协议/生成器/核心版本不兼容 | `DPT-PROFILE-2003` |
| 归档或契约哈希失败 | `DPT-PROFILE-2004` |
| 业务 Native 缺少 arm64-v8a | `DPT-PROFILE-2005` |
| candidate 用于正式加固 | `DPT-PROFILE-3004` |

## 5. 旧入口和新入口的职责

旧入口继续保留：

- `profileCreate`：创建/读取稳定身份。
- `profileShow`：只读查看绑定、活动 Revision、ABI 和门禁状态。
- `profileBuild`：生成候选 Revision 和目录 Bundle。
- `profilePublish`：迁移期目录型发布和活动指针管理。
- `profileRollback`：在同一 Profile 中切换历史已验证 Revision。

新增入口用于交付：

- `profilePack` / `pack`：目录 Bundle -> candidate 文件。
- `profileValidate` / `validate`：验证正式文件，可选核对 APK。
- `profilePromote` / `promote`：candidate + 报告 -> verified 文件。

服务器打包建议只依赖 `dpt.jar`、正式 `.dptprofile`、输入 APK、签名工具和既有交付脚本；不要复制 `profiles/index.json` 或活动指针作为运行时依赖。

## 6. 回滚和升级

同一应用换壳时：

1. 保留原 `profileId` 和不可变身份锁。
2. 重新执行 `profileBuild` 生成新的 `revisionId`。
3. 重新 pack、门禁、扫描和 promote，得到新的 `.dptprofile`。
4. 发现问题时直接选择上一份 verified 文件，不修改文件内容。

不同应用不能共用一个 Profile 文件；若确实要更换稳定身份，应创建新的 Profile 并重新验收。

## 7. 验收和设备策略

### 7.1 自动化静态验收

```bash
bash ./gradlew --no-daemon :dpt:test
git diff --check
```

至少检查：candidate/verified 隔离、重复打包确定性、篡改和路径穿越拒绝、报告门禁、无注册表验证、旧目录解析回归。

### 7.2 APK 交付验收

单次安装优先真实 `arm64-v8a` 真机：

```bash
sha256sum input.apk input_protected.apk
adb install -r input_protected.apk
adb shell monkey -p com.example.app 1
```

记录设备型号、Android 版本、ABI、输入/输出 SHA-256、安装和启动结果、主要业务路径、再次启动、崩溃/ANR 与卸载结果。检查 `logcat` 中不存在 `Fatal signal`、`UnsatisfiedLinkError`、`dlopen failed`、JNI 注册失败或 DEX 恢复异常。

只有重复安装、跨版本或矩阵测试才使用临时通用 AVD；同一轮复用一个实例，结束后停止模拟器、删除本轮 AVD/快照/临时目录并执行 `df -h`。真机和 AVD 证据分开记录。

## 8. 常见问题

| 问题 | 根因/处理 |
| --- | --- |
| `PROFILE_ERROR ... 3004` | 传入了 candidate；先执行 promote。 |
| `2002` | APK Manifest 包名和 Profile 绑定不同；不能跨 applicationId 复用。 |
| `2005` | APK 有业务 SO 但没有 `lib/arm64-v8a`；首版没有其他 ABI 兜底。 |
| `3001` | 归档结构、版本、契约、DEX、ELF 或哈希无效；重新生成，不手改 JSON。 |
| `3002` | 输出文件已存在；更换输出路径，工具不会覆盖。 |
| promote 缺报告 | 补齐三份真实报告并确保候选 APK SHA-256 一致。 |
| 加固仍读取共享壳 | 未传 `--profile` 或使用旧 JAR；确认运行的 `dpt.jar` 包含 Profile 类并显式传参。 |
