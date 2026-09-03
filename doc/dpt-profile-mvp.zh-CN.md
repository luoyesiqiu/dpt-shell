# dpt-profile MVP（方案 B）

目标：为每个 `applicationId` 生成一套独立壳代码，并把可消费的壳封装成一个可复制的 `.dptprofile` 文件。同一应用复用同一个 `profileId`，需要换壳时只生成新的 Revision。

## 最短流程

```text
profileCreate -> profileBuild -> profilePack
                              -> 门禁/扫描报告
                              -> profilePromote -> verified.dptprofile
                              -> dpt --profile -> APK
```

Profile 只包含壳输入，不包含签名私钥、密码、渠道配置或 APK。首版只保证 `arm64-v8a` 和 APK，不处理 AAB；渠道包仍是加固后的独立步骤。

## 1. 生成 Profile

在 `dpt-shell` 根目录执行。以下命令中的 `com.example.app` 换成目标 APK 的真实 `applicationId`。

```bash
# 每个 applicationId 只需成功执行一次；同一应用重复执行会复用原 profileId
bash ./gradlew --no-daemon profileCreate \
  -PprofileApplicationId=com.example.app

# 生成候选壳；必须打开 Native 构建，首版产物为 arm64-v8a
bash ./gradlew --no-daemon profileBuild \
  -PprofileApplicationId=com.example.app \
  -PprofileRecipe=recipe-a \
  -PprofileBuildNative=true
```

`profileBuild` 输出的 `revisionId` 对应一个候选 Revision，不会自动激活。若使用受限 Overlay，再增加 `-PprofileOverlayDir=/绝对路径/overlay`；Overlay 不能改固定 DEX、JNI 签名、密钥或启动协议。

## 2. 生成可移植文件

先找到候选 Bundle 目录，通常为：

```text
<profileStateDir>/candidates/<profileId>/<revisionId>/bundle
```

执行 pack（不需要 `--profiles-root`，源目录不会被修改）：

```bash
bash ./gradlew --no-daemon profilePack \
  -PprofileBundleDir=/绝对路径/.../bundle \
  -PprofileArchiveOut=/绝对路径/com.example.app.candidate.dptprofile
```

也可以直接调用 CLI：

```bash
java -cp dpt/build/classes/java/main:... \
  com.luoye.dpt.profile.cli.ProfileCommandMain pack \
  --bundle /绝对路径/bundle \
  --out /绝对路径/com.example.app.candidate.dptprofile
```

日常优先使用 Gradle 入口；服务器只需要复制候选文件和报告目录，不需要复制 Profile 注册表。

## 3. 门禁通过后晋级

报告目录必须包含以下三个文件，并且报告中的 `profileId`、`revisionId`、候选 APK SHA-256、`applicationId` 和扫描窗口彼此匹配：

```text
reports/
  gate-report.json
  scan-baseline.json
  scan-candidate.json
```

```bash
bash ./gradlew --no-daemon profilePromote \
  -PprofileCandidate=/绝对路径/com.example.app.candidate.dptprofile \
  -PprofileReportDir=/绝对路径/reports \
  -PprofileArchiveOut=/绝对路径/com.example.app.dptprofile
```

`profilePromote` 只接受 candidate 文件，严格失败且不覆盖已有正式文件。只有输出为 `*.dptprofile` 且状态为 `verified` 的文件才能用于正式加固。

晋级前可单独验证正式文件：

```bash
bash ./gradlew --no-daemon profileValidate \
  -PprofileArchive=/绝对路径/com.example.app.dptprofile

# 可选：同时核对 APK 的 applicationId 和业务 Native ABI
bash ./gradlew --no-daemon profileValidate \
  -PprofileArchive=/绝对路径/com.example.app.dptprofile \
  -PprofileApk=/绝对路径/input.apk
```

## 4. 使用 Profile 加固 APK

服务器或本地只需把 `dpt.jar` 和正式 Profile 文件放在一起，显式传入 `--profile`：

```bash
java -jar /绝对路径/dpt.jar \
  -f /绝对路径/input.apk \
  -o /绝对路径/input_protected.apk \
  --profile /绝对路径/com.example.app.dptprofile
```

指定 `--profile` 后不会查询注册表、活动指针或全局 `shell-files`，壳会解包到本次任务的随机目录；任务结束后临时目录清理。APK Manifest、DEX、SO 或配置写入前会检查：

- Profile 状态必须为 `verified`，且文件完整性校验通过。
- Profile 的 `applicationId` 必须等于 APK Manifest 的包名。
- 协议、生成器、核心版本和 `arm64-v8a` ABI 必须匹配。
- APK 含业务 Native 库时必须存在 `arm64-v8a`。

`--profile-file` 是 `--profile` 的同义参数。AAB 不在本变更范围内。

## 5. MVP 验收

首次安装和启动优先使用真实 `arm64-v8a` 真机：

```bash
sha256sum /绝对路径/input.apk /绝对路径/input_protected.apk
adb install -r /绝对路径/input_protected.apk
adb shell monkey -p com.example.app 1
```

确认安装、冷启动、主要页面和再次启动正常，并检查 `logcat` 没有 `Fatal signal`、`ANR`、`UnsatisfiedLinkError`、`dlopen failed` 或 JNI 注册失败。需要多次安装或矩阵测试时才使用临时通用 AVD，测试结束停止并删除本轮 AVD/缓存，再检查磁盘空间。

## 6. 常见失败

| 现象 | 处理 |
| --- | --- |
| candidate 不能用于加固 | 先用真实报告执行 `profilePromote`，不要改清单状态。 |
| applicationId 不匹配 | 为该应用创建/选择正确的 Profile，不能跨应用复用。 |
| arm64 不兼容 | 检查 APK 和 Profile 是否都包含 `arm64-v8a`。 |
| 输出已存在 | 改用新的输出文件名；工具不会覆盖旧文件。 |
| 报告缺失或不可比 | 补齐三份报告并重新生成，不能跳过硬门槛。 |
