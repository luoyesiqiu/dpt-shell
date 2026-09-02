# dpt-profile 使用与验收指南

本文档说明如何为一个 APK 预先创建独立壳 Profile，构建并发布一个不可变 Revision，再使用该 Revision 完成 APK 加固。Profile 只负责壳选择和壳产物隔离；签名、`zipalign`、验签和渠道包仍由既有交付流程负责。

## 1. 范围与结论

当前变更的目标是：不同 `applicationId` 使用不同的 Profile、生成源码和壳产物；同一 `applicationId` 复用同一个 Profile，并通过 Revision 做显式升级和回滚。

首版有以下硬边界：

- 只保证 `arm64-v8a`。输入 APK 含业务 Native 库但没有 `arm64-v8a` 时，加固会在写入 APK 内容前严格失败。
- 只支持 APK Profile 链路，不支持或验收 AAB Profile。
- Profile 不依赖渠道输入。没有渠道配置时，Profile 创建、构建和 APK 加固仍可执行。
- 不修改消费端 `/Users/admin/AndroidStudioProjects/lanling_couduis/tools`，也不会自动上传 APK 或扫描样本。
- 受限 Java/C++ Overlay 只允许实现启动协调、SO 处理、路径/配置适配和 Profile 私有辅助代码，不能覆盖固定协议核心。
- 本变更降低壳层公开静态重复度，不承诺消除证书、业务代码、第三方库、网络和运行行为造成的全部关联，也不以规避安全产品检测为目标。

## 2. 环境与目录

在 `dpt-shell` 根目录执行命令。建议使用 JDK 17、Android SDK、CMake 和项目自带 Gradle Wrapper。Wrapper 没有执行权限时统一通过 `bash` 调用：

```bash
ANDROID_HOME=/Users/admin/Library/Android/sdk \
env PATH=/opt/homebrew/bin:$PATH \
bash ./gradlew --no-daemon <task>
```

默认目录如下：

```text
dpt-shell/
  profiles/                         # Profile 源状态、身份锁、候选和已发布源 Revision
    index.json                      # applicationId -> profileId
    source/<profile-id>/            # profile.yaml、identity.lock.json、overlay/
    revisions/<profile-id>/         # 源侧 Revision 清单和报告
  executable/
    dpt.jar
    profiles/<profile-id>/           # dpt.jar 读取的活动 Bundle
      active.json
      revisions/<revision-id>/
        profile.json
        revision.json
        artifact-sha256.json
        shell-files/
          build-key
          dex/classes.dex
          libs/arm64/<profile-so>.so
```

根工程可以用 `-PprofileStateDir=<目录>` 和 `-PprofileActiveDir=<目录>` 覆盖前两个目录。APK 加固时，如果 `dpt.jar` 位于 `executable/`，默认从 `dpt-shell/profiles` 读取源状态，从 `executable/profiles` 读取活动 Bundle。身份锁、注册表、已发布 Revision 和活动指针不在 Gradle `build/` 下；普通 `clean` 不应删除它们。建议定期备份整个 `profiles/` 和 `executable/profiles/`，并保留目录内的 SHA-256 清单。

## 3. 五个 Profile 入口

### 3.1 `profileCreate`

显式创建 `applicationId` 到不透明 `profileId` 的唯一绑定。该入口只创建或返回绑定，不构建、不发布，也不会修改已有稳定身份。

```bash
ANDROID_HOME=/Users/admin/Library/Android/sdk \
env PATH=/opt/homebrew/bin:$PATH \
bash ./gradlew --no-daemon profileCreate \
  -PprofileApplicationId=com.example.myapp
```

首次执行会生成 `profile.yaml`、`identity.lock.json` 和唯一注册表项；再次执行同一 `applicationId` 应返回 `created=false`。同一个 `applicationId` 绑定第二个 Profile，或同一个 Profile 绑定第二个 `applicationId`，都会严格失败。

### 3.2 `profileShow`

只读查看绑定、身份锁摘要、活动 Revision、历史 Revision、ABI 和门禁状态。可以按应用或 Profile 查询：

```bash
bash ./gradlew --no-daemon profileShow \
  -PprofileApplicationId=com.example.myapp

bash ./gradlew --no-daemon profileShow \
  -PprofileId=pf_xxxxxxxxxxxxxxxx
```

该入口不改写任何 Profile 文件。生产加固前应先用它确认存在活动 Revision，并确认 ABI 为 `arm64-v8a`。

### 3.3 `profileBuild`

从已注册 Profile 的源配置生成候选 Revision。候选不会自动激活；要生成可用于 APK 加固的 arm64 Bundle，必须执行 Native 构建：

```bash
bash ./gradlew --no-daemon profileBuild \
  -PprofileApplicationId=com.example.myapp \
  -PprofileRecipe=recipe-a \
  -PprofileBuildNative=true
```

可以通过 `-PprofileOverlayDir=/绝对路径/overlay` 指定受限 Overlay，通过 `-PprofileToolchainVersion=<版本描述>` 固定工具链描述。没有显式指定 Recipe 时，系统只从已经整体验证过的 Recipe 中选择满足结构门槛的候选；没有候选满足门槛时严格失败，不会静默换用未经验证的组合。

构建结果会输出 `PROFILE_BUILD_RESULT` 和候选 Revision ID。构建只写入隔离的生成源码、缓存和 `profiles/candidates/`，不会更新 `executable/profiles/<profile-id>/active.json`。

### 3.4 `profilePublish`

在候选 Revision 的所有硬门槛通过后发布不可变 Bundle，并原子更新活动指针。生产路径必须提供门禁报告目录；手工多引擎扫描报告还必须通过 `--scan-baseline` 和 `--scan-candidate`（Gradle 属性分别为 `profileScanBaseline`、`profileScanCandidate`）传入：

```bash
bash ./gradlew --no-daemon profilePublish \
  -PprofileApplicationId=com.example.myapp \
  -PprofileRevisionId=r000001 \
  -PprofileReportDir=/绝对路径/reports \
  -PprofileScanBaseline=/绝对路径/reports/scan-baseline.json \
  -PprofileScanCandidate=/绝对路径/reports/scan-candidate.json
```

报告必须绑定候选 APK 的 SHA-256，且与旧 DPT 基线使用相同的扫描器集合和时间窗口。报告缺失、过期、不可比或新增指定高风险家族标签时发布失败，旧活动 Revision 保持不变。测试中使用的占位报告只用于验证状态机，不能作为生产放行证据。

### 3.5 `profileRollback`

只允许回滚到同一 Profile 中曾经通过全部门槛、且完整性校验仍通过的历史 Revision。不重新构建、不改写历史产物：

```bash
bash ./gradlew --no-daemon profileRollback \
  -PprofileApplicationId=com.example.myapp \
  -PprofileRevisionId=r000001
```

失败、未验证、缺失或哈希损坏的 Revision 会被拒绝，当前活动指针不变。

## 4. 给某个 APK 增加单独的壳

对一个真实 APK，完整顺序必须是「创建 Profile → 构建候选 → 完成门禁并发布 → 加固 APK」。不能跳过预创建，也不能让 `dpt.jar` 在加固时自动创建 Profile。

```bash
# 1. 为目标 applicationId 创建唯一 Profile
bash ./gradlew --no-daemon profileCreate \
  -PprofileApplicationId=com.example.myapp

# 2. 构建该 Profile 的 arm64 候选 Revision
bash ./gradlew --no-daemon profileBuild \
  -PprofileApplicationId=com.example.myapp \
  -PprofileRecipe=recipe-a \
  -PprofileBuildNative=true

# 3. 使用真实门禁报告发布候选（revisionId 以构建输出为准）
bash ./gradlew --no-daemon profilePublish \
  -PprofileApplicationId=com.example.myapp \
  -PprofileRevisionId=<revisionId> \
  -PprofileReportDir=/绝对路径/reports \
  -PprofileScanBaseline=/绝对路径/reports/scan-baseline.json \
  -PprofileScanCandidate=/绝对路径/reports/scan-candidate.json

# 4. 只在 profilePublish 成功后执行 APK 加固
java -jar executable/dpt.jar \
  -f /绝对路径/input.apk \
  -o /绝对路径/input_protected.apk \
  -S
```

`-S` 是既有的体积优化选项，会在体积和部分运行时性能之间做权衡；是否使用应与旧 DPT 基线保持一致。签名配置仍由既有 DPT/交付流程处理，Profile 不读取或生成签名密钥。未注册应用、没有活动 Revision、目标 `applicationId` 不匹配、协议/生成器版本不兼容、arm64 缺失或 Bundle 哈希不一致时，工具必须在第一次 Manifest、DEX、SO 或配置写入前失败，并输出 `DPT-PROFILE-*` 错误码。

APK Profile 链路不会读取全局共享 `shell-files` 作为兜底。每次加固的随机密钥、临时工作目录、重写 DEX 和写入 APK 后的 SO 都是单次产物；活动 Bundle 在任务前后应保持只读。

## 5. Recipe 与 Overlay 边界

Recipe 是已经整体通过契约和 APK 启动矩阵的 Java/Native 组合，不允许把未经整体验证的策略槽任意排列。首版至少提供两个完整 Recipe。Recipe 可以改变启动协调类的拆分、SO 辅助组织、Native 编译单元和路径适配，但必须保持以下不可变协议：

- `shellDex + compressedDexZip + 4 字节长度` 和 MultiDex CodeItem 格式。
- 配置解密、密钥派生、RC4 key slot、ART Hook、风险检查和组件恢复语义。
- Android 5～8 的代理 Application 链与 Android 9+ 的 AppComponentFactory 链的必要顺序。
- 原 Application、AppComponentFactory、Activity、Service、Receiver、Provider 和 ClassLoader 的恢复行为。
- `JNI_OnLoad` 的「读取配置 → 风险检查 → 注册 JNI」顺序、语义 ID 和固定 JVM 签名。

Overlay 必须位于 Profile 专属 `overlay/` 目录，内容会计算 SHA-256 并写入 Revision。允许的代码只能用于启动协调、SO 定位/解压/校验/加载、ABI/路径/配置适配和 Profile 私有辅助类型。以下操作会在构建期拒绝：覆盖代理入口、JNI Bridge、Native 注册表或契约；改变 JNI 语义/签名/注册数量；改变 DEX/CodeItem 或密钥协议；增加权限、导出组件、网络依赖、远程下载或远程动态代码加载。

## 6. 发布硬门槛

只有以下门槛全部通过，`profilePublish` 才能激活 Revision：

- 壳 DEX/SO/资产中的旧公开标识命中数为零；不同 `applicationId` 的可变层 Java Jaccard 不高于 `0.80`，Native Jaccard 不高于 `0.85`。
- 候选 APK 不超过旧 DPT 产物 `+2 MiB`，相对原 APK 的增幅不超过旧基线 `+1` 个百分点；arm64 壳 SO 不超过旧壳 SO 的 `110%`。
- 固定环境冷启动至少 30 次，P50 不超过旧基线 `+5%`、P95 不超过 `+10%`，崩溃和 ANR 为零；已有 Profile 的 10 次加固和首次 arm64 构建也必须满足各自门槛。
- 相对旧 DPT 不新增权限、导出组件、网络能力或远程动态代码行为。
- 手工多引擎扫描报告与候选 APK SHA-256、扫描器集合和时间窗口精确匹配，检测数不高于旧基线，且不新增 Trojan、Banker、Spyware 或 RAT 家族标签。

工具不会自动上传扫描样本。无法形成可比证据时必须失败关闭，而不是跳过门槛。

## 7. 验收策略与设备规则

### 7.1 单次人工验收：优先真机

明日对某个目标 APK 的首次安装、启动和业务行为验收，优先使用真实的 arm64 真机。需要记录设备型号、Android 版本、ABI、输入/输出 APK SHA-256、安装结果、启动结果、关键业务路径、崩溃/ANR 和卸载结果。验收至少覆盖：

1. 安装成功，包名和签名符合预期。
2. 冷启动成功，原 Application 或 AppComponentFactory 能完成恢复。
3. 首屏、主要 Activity、Service、Receiver、Provider 和 ClassLoader 行为与未加固版本一致。
4. 退出后再次启动、进程重建和网络/缓存常用路径无异常。
5. `adb logcat` 无壳初始化崩溃、JNI 注册失败、SO 加载失败或 DEX 恢复异常。

### 7.2 多次安装或自动化矩阵：临时通用 AVD

只有在需要多次安装、重复冷启动或自动化矩阵时，才使用临时通用 arm64 AVD。推荐 Pixel 6、API 32 或项目基线指定的同一 API/ABI；每次任务使用独立临时目录，任务结束必须停止并删除 AVD、快照、用户数据和临时 APK，避免占用磁盘。真机验收与 AVD 结果要分开记录，AVD 不能替代明日的人工真机结论。

设备策略固化在 `verification/dpt-profile/device-test-policy.json`，适用于本变更的所有自动化安装/启动任务：默认 `real-device-first`，只有 `repeated-install-or-matrix` 才允许临时 AVD，并要求清理完成后再报告通过。

## 8. 迁移、升级与回滚

切换生产 APK 入口前，为每个待加固 `applicationId` 依次执行 `profileCreate`、`profileBuild`、完整门禁和 `profilePublish`。迁移时不要把旧共享壳目录直接复制成多个 Profile；每个应用必须有自己的身份锁和 Bundle。

同一应用升级时只创建新 Revision，Profile ID、包/类/JNI/SO/资产名和 `build-key` 保持稳定。新 Revision 发布失败不会影响旧活动版本；发现运行问题时使用 `profileRollback` 切回已验证历史 Revision。需要更换稳定身份时创建新的 Profile 迁移，不在升级过程中隐式轮换身份。

## 9. 当前交付证据与未完成项

`verification/dpt-profile/profile-apk-delivery.json` 记录了一次测试 APK 的交付兼容性证据：Profile 创建和 arm64 构建已产生独立 Bundle，加固输出已通过既有 `zipalign`、`apksigner` 的 v1/v2/v3 验证，且未提供渠道输入。该文件中的 `profilePublish=LOCAL_TEST_ONLY` 说明其发布状态只用于链路验证。

该证据不等于生产验收。生产激活仍需要：

- 真实旧 DPT 与候选 APK 的同窗口多引擎扫描报告。
- 真实 arm64 真机安装、启动和业务行为记录。
- 完整性能、包体、权限组件和结构差异报告与候选 APK SHA-256 的绑定。

注意首次正式发布的顺序边界：`profilePublish` 只读取已经生成的 `gate-report.json` 和人工扫描报告，不负责生成候选 APK 或这些报告；而 `dpt.jar` 只接受活动 Revision。当前仓库没有独立的「候选 Revision 暂存加固并回填报告」命令，因此新 Profile 的正式发布需要由发布流程提供受控的候选 APK 生成/报告回填步骤，不能用手工伪造报告或直接改写 `active.json` 绕过门禁。明日的真机功能验收可以使用已受控激活的测试 Revision，但结果必须标记为功能验收，不等同于生产发布门禁通过。

在这些证据补齐前，结论只能是「可以开始验收」，不能写成「已完成正式发布验收」。

## 10. 常见严格失败处理

| 现象 | 处理 |
|---|---|
| `DPT-PROFILE-1001` | 补齐对应入口的必填 Gradle 参数，不要猜测默认 Profile。 |
| `DPT-PROFILE-1002` | 先执行 `profileCreate`，确认 `applicationId` 拼写和注册表。 |
| 无活动 Revision | 执行 `profileBuild`、完整门禁和 `profilePublish`；候选不能直接用于加固。 |
| Bundle 哈希或协议不一致 | 恢复备份或重新发布完整 Revision，不要手工改写 Bundle。 |
| arm64 不兼容 | 检查输入 APK 的 `lib/arm64-v8a` 和 Profile Bundle；首版不使用其他 ABI 兜底。 |
| 扫描/性能报告不可比 | 使用同一输入、签名方式、`-S`、工具链、设备和时间窗口重新采样。 |
