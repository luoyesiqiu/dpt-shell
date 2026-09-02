# dpt-profile MVP 打包指南

这是一份日常使用的简易版说明。目标是：不同 `applicationId` 使用不同壳，同一应用复用同一个 Profile，最后得到一个已经加固、对齐、签名并可安装的 APK。

## 一句话流程

```text
确认 Profile-aware dpt.jar
  -> profileCreate（新应用仅执行一次）
  -> profileBuild（生成候选壳）
  -> profilePublish（通过硬门槛后激活）
  -> tools/protect_apk.sh（加固、对齐、签名）
  -> 真机安装验收
```

Profile 与渠道包无关；不传渠道参数时不会生成渠道包。

## 1. 前置检查

在 `dpt-shell` 根目录执行 Profile 命令：

```bash
cd /Users/admin/AndroidStudioProjects/dpt-shell

# 确认运行时包含 Profile 命令
jar tf executable/dpt.jar | rg 'com/luoye/dpt/profile'
```

如果没有任何匹配项，说明 `dpt.jar` 还是旧版，先同步 Profile-aware 的 `dpt.jar`、活动 Bundle 和 Profile 状态，不能直接开始 Profile 加固。

还需要准备：

- JDK 17、Android SDK、CMake。
- 输入 APK 包含 `arm64-v8a`；MVP 首版只保证 `arm64-v8a`。
- 项目签名配置可被 `tools/protect_apk.sh` 读取，或可以在脚本提示时手工输入。
- 暂不支持 AAB，只处理 APK。

## 2. 新应用生成独立壳

把下面的 `com.example.myapp` 换成目标 APK 的真实 `applicationId`。

### 2.1 创建 Profile（每个应用只做一次）

```bash
bash ./gradlew --no-daemon \
  -PprofileApplicationId=com.example.myapp \
  profileCreate
```

命令会创建唯一的 `profileId`。同一个 `applicationId` 再执行不会创建第二套身份；不同 `applicationId` 必须重新执行。

### 2.2 构建候选壳

```bash
bash ./gradlew --no-daemon \
  -PprofileApplicationId=com.example.myapp \
  -PprofileRecipe=recipe-a \
  -PprofileBuildNative=true \
  profileBuild
```

`-PprofileBuildNative=true` 不能省略，否则不会生成可用的 `arm64-v8a` 壳 SO。命令输出中的 `revisionId` 要记下来。候选 Revision 不会自动成为活动 Revision。

如需加入该应用专属的受限 Overlay：

```bash
bash ./gradlew --no-daemon \
  -PprofileApplicationId=com.example.myapp \
  -PprofileRecipe=recipe-a \
  -PprofileOverlayDir=/绝对路径/overlay \
  -PprofileBuildNative=true \
  profileBuild
```

Overlay 只能放启动协调、SO 加载、路径/配置适配和 Profile 私有辅助代码，不能改固定 DEX、JNI、密钥和启动协议。

### 2.3 发布并激活 Revision

发布是硬门槛。必须提供真实的门禁报告和两份可比扫描报告，不能用空报告或手工改 `active.json`：

```bash
bash ./gradlew --no-daemon \
  -PprofileApplicationId=com.example.myapp \
  -PprofileRevisionId=<上一步输出的 revisionId> \
  -PprofileReportDir=/绝对路径/reports \
  -PprofileScanBaseline=/绝对路径/reports/scan-baseline.json \
  -PprofileScanCandidate=/绝对路径/reports/scan-candidate.json \
  profilePublish
```

发布成功后，活动指针才会更新。失败时旧活动 Revision 保持不变。

## 3. 给 APK 加固

### 3.1 查看或确认当前 Profile

```bash
cd /Users/admin/AndroidStudioProjects/dpt-shell
bash ./gradlew --no-daemon \
  -PprofileApplicationId=com.example.myapp \
  profileShow
```

确认存在活动 Revision，且 Bundle 的 ABI 为 `arm64-v8a`。

### 3.2 执行加固、对齐和签名

```bash
cd /Users/admin/AndroidStudioProjects/lanling_couduis
./tools/protect_apk.sh "/绝对路径/input.apk"
```

脚本会按以下顺序处理：

```text
DPT 加固（-x -S）
  -> zipalign
  -> 读取 app/and_res_config.xml 签名
  -> apksigner 签名与验签
```

输出文件默认在输入 APK 同目录，命名为：

```text
input_protected.apk
input_protected_1.apk
input_protected_2.apk
```

已有文件不会被覆盖。若签名配置缺失或字段不完整，脚本会提示手工输入签名文件、别名和密码。

### 3.3 可选生成渠道包

只有传入 `--channels` 才会执行 Packer-NG：

```bash
./tools/protect_apk.sh "/绝对路径/input.apk" \
  --channels=oppo,vivo,xiaomi
```

渠道包输出到最终 APK 同目录下的 `<最终 APK 文件名>_channels/`。不需要渠道包时不要传该参数。

## 4. 最小验收

单次人工验收优先使用真实的 `arm64-v8a` Android 真机：

```bash
APK="/绝对路径/input_protected.apk"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BT="$SDK/build-tools/33.0.1"

"$BT/aapt" dump badging "$APK" | rg "package: name"
"$BT/zipalign" -c -P 16 -v 4 "$APK"
"$BT/apksigner" verify --verbose "$APK"
adb install -r "$APK"
adb shell monkey -p <applicationId> 1
```

验收至少确认：安装成功、冷启动成功、主要业务路径正常、退出后再次启动正常，并检查 `adb logcat` 没有 `Fatal signal`、`ANR`、`UnsatisfiedLinkError`、`dlopen failed` 或 JNI 注册失败。

需要重复安装或跑矩阵时，才使用临时通用 AVD；测试结束必须停止并删除本轮 AVD、快照和临时目录，再检查磁盘空间。真机结果和 AVD 结果分开记录。

## 5. 已有 Profile 的日常命令

已有应用通常只需要：

```bash
cd /Users/admin/AndroidStudioProjects/dpt-shell
bash ./gradlew --no-daemon \
  -PprofileApplicationId=com.example.myapp \
  profileShow

cd /Users/admin/AndroidStudioProjects/lanling_couduis
./tools/protect_apk.sh "/绝对路径/input.apk"
```

只有要更换壳代码或生成新 Revision 时，才重新执行 `profileBuild` 和完整的 `profilePublish`。

## 6. 常见失败处理

| 现象 | 处理 |
| --- | --- |
| 找不到 Profile 命令 | 同步 Profile-aware `executable/dpt.jar`，不要使用旧 JAR。 |
| 没有活动 Revision | 先完成 `profileBuild` 和 `profilePublish`。 |
| `arm64-v8a` 不兼容 | 检查 APK 和 Bundle 是否都包含 `arm64-v8a`。 |
| 输出文件已存在 | 脚本会自动追加 `_1`、`_2`，无需删除旧包。 |
| 签名信息缺失 | 检查 `app/and_res_config.xml`，或按提示手工输入。 |
| 发布报告不完整 | 补齐真实 `gate-report.json`、`scan-baseline.json` 和 `scan-candidate.json`，不要绕过门禁。 |

## 7. 当前项目 Profile A 示例

`com.tangyu.momogirl` 已有独立 Profile：

```text
profileId：pf_c5dfbe76167029ff
活动 Revision：r000001
壳 ABI：arm64-v8a
```

使用前仍建议先执行 `profileShow`，再运行 `tools/protect_apk.sh`。业务项目 `tools/executable/dpt.jar` 如果检查不到 `com/luoye/dpt/profile` 类，必须先同步新版运行时。
