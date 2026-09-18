# dpt-profile 使用说明

给每个 `applicationId` 生成一套**独立壳**，打包成单文件 `*.dptprofile`；加固 APK 时用 `--profile` 显式指定。

## 1. 整体流程

```text
【生成机】需要源码 + Android 工具链
  profileCreate                     建立稳定身份（一个 applicationId 一次）
      -> profileBuild               编译 arm64 壳（候选 Revision）
      -> profilePack -PprofileProduction=true
                                    直出正式 app.dptprofile
      ------------------------------------------------ 只需复制 ↓
【消费机】只需 JDK + dpt.jar + Build Tools + 签名文件
  validate -> DPT 加固 -> zipalign -> 签名 -> 验签 -> 可选渠道包
```

消费机**不需要** Profile 注册表、壳源码、Gradle、NDK。指定 `--profile` 后 DPT 不查注册表，
只从传入的归档解包壳文件。

## 2. 术语与边界

| 术语 | 含义 | 能交给消费机吗 |
| --- | --- | --- |
| Profile | 一个应用的稳定壳身份，绑定 `applicationId` + `profileId` | 否，交付用归档文件 |
| Revision | 同一 Profile 的一次壳代码/工具链版本 | 通过正式归档交付 |
| Bundle | 生成机上的目录型候选壳（DEX、SO、契约文件） | 否，不是对外接口 |
| candidate | `*.candidate.dptprofile`，未完成晋级 | 否，正式加固会拒绝 |
| verified | `*.dptprofile`，可被加固流程消费 | **是** |

边界：

- 只保证 APK，不支持 AAB。
- 只保证 `arm64-v8a`；目标 APK 含业务 Native 库时必须带 `lib/arm64-v8a`，没有其他 ABI 兜底。
- Profile 不含签名私钥、签名密码、渠道配置、扫描样本。
- 渠道包不属于 Profile 生命周期，永远在签名之后单独生成。
- `verified` 只表示**技术上可被加固流程消费**，不表示通过第三方扫描或应用商店审核。

## 3. 生成正式 Profile（生成机）

默认状态写入 `build/dpt-profile/state`，活动指针写入 `build/dpt-profile/active`（可随构建清理）。
需要跨构建保留身份时，追加 `-PprofileStateDir=<绝对路径>/state -PprofileActiveDir=<绝对路径>/active`。

### 3.1 创建稳定身份

```bash
bash ./gradlew --no-daemon profileCreate \
  -PprofileApplicationId=com.example.app
```

首次生成 `profileId`、身份锁和源配置；同一应用重复执行会复用原绑定，不会轮换身份。

### 3.2 编译壳

```bash
bash ./gradlew --no-daemon profileBuild \
  -PprofileApplicationId=com.example.app \
  -PprofileRecipe=recipe-a \
  -PprofileBuildNative=true
```

`-PprofileBuildNative=true` 必须打开，否则不会编译 arm64 壳 SO，无法生成正式 Profile。
产物 Bundle 位于 `<profileStateDir>/candidates/<profileId>/<revisionId>/bundle`。

### 3.3 直出正式归档

```bash
bash ./gradlew --no-daemon profilePack \
  -PprofileBundleDir=/绝对路径/candidates/<profileId>/<revisionId>/bundle \
  -PprofileArchiveOut=/绝对路径/com.example.app-r000001.dptprofile \
  -PprofileProduction=true
```

- 输出 `status=verified`、`reportsSha256=null`，不含 `reports/` 条目。
- 漏掉 `-PprofileProduction=true` 会输出 candidate，正式加固会拒绝。
- 输出文件已存在时严格失败，**不覆盖**。
- 归档完整性、`applicationId`、`arm64-v8a`、DEX/SO 契约、构建密钥和 SHA-256 仍然强制校验。

没有 Gradle 时用等价 CLI：

```bash
java -cp /绝对路径/dpt.jar \
  com.luoye.dpt.profile.cli.ProfileCommandMain pack \
  --bundle /绝对路径/bundle \
  --out /绝对路径/app.dptprofile \
  --production true
```

### 3.4 校验归档

```bash
bash ./gradlew --no-daemon profileValidate \
  -PprofileArchive=/绝对路径/com.example.app-r000001.dptprofile

# 顺带核对目标 APK 的 applicationId 和业务 ABI
bash ./gradlew --no-daemon profileValidate \
  -PprofileArchive=/绝对路径/com.example.app-r000001.dptprofile \
  -PprofileApk=/绝对路径/input.apk
```

CLI 等价：`validate --profile <归档> [--apk <APK>]`。

## 4. 加固 APK（消费机）

### 4.1 DPT 加固

```bash
WORK=/绝对路径/work/$(date +%Y%m%d%H%M%S)   # 每次任务一个独立空目录
mkdir -p "$WORK"

java -jar /绝对路径/dpt.jar \
  -f /绝对路径/input.apk \
  -o "$WORK" \
  -x -S -e arm,x86,x86_64 \
  --profile /绝对路径/com.example.app-r000001.dptprofile
```

| 参数 | 作用 |
| --- | --- |
| `-x` (`--no-sign`) | DPT 不签名，签名交给后面的 `apksigner` |
| `-S` (`--smaller`) | 用少量启动开销换更小体积 |
| `-e` (`--exclude-abi`) | 排除 armeabi-v7a / x86 / x86_64，只保留 arm64 |
| `--profile` | 指定正式 `.dptprofile`；`--profile-file` 是同义别名 |
| `-o` | **输出目录**，不是文件名 |

`-o` 目录里的产物名不固定（常见 `<输入名>_signed.apk`、`<输入名>_unsign.apk`、`unsigned_<输入名>.apk`）；
请用独立空目录接收，自行确认生成的文件，不要让多个任务共用同一个目录。

### 4.2 对齐、签名、验签

```bash
BT=$ANDROID_HOME/build-tools/33.0.1
DPT_APK="$WORK/input_signed.apk"

"$BT/zipalign" -p -f 4 "$DPT_APK" "$WORK/aligned.apk"
"$BT/zipalign" -c -p -v 4 "$WORK/aligned.apk"        # 当场回验

: "${KS_PASS:?请通过 Secret 注入}"                    # 不要把密码写进命令行或 Git
: "${KEY_PASS:?请通过 Secret 注入}"
"$BT/apksigner" sign \
  --ks /绝对路径/release.keystore --ks-key-alias release \
  --ks-pass env:KS_PASS --key-pass env:KEY_PASS \
  --v1-signing-enabled true --v2-signing-enabled true \
  --v3-signing-enabled false --v4-signing-enabled false \
  --out /绝对路径/input_protected.apk "$WORK/aligned.apk"
unset KS_PASS KEY_PASS

"$BT/apksigner" verify --verbose --print-certs /绝对路径/input_protected.apk
```

### 4.3 渠道包（可选，最后一步）

```bash
java -jar /绝对路径/packer-ng-2.0.1.jar generate \
  --channels=xiaomi,huawei,oppo,vivo \
  --output=/绝对路径/channels \
  /绝对路径/input_protected.apk

java -jar /绝对路径/packer-ng-2.0.1.jar verify /绝对路径/channels/input_protected-xiaomi.apk
```

渠道包必须基于**已签名**的基础包生成；生成后不要再对渠道包二次签名，否则渠道信息会失效。

### 4.4 顺序不能变

```text
DPT 未签名产物 -> zipalign -> 签名 -> 验签 -> 渠道包
```

先签名再 `zipalign` 会破坏签名摘要；Profile 校验失败时不要继续写 APK。

## 5. 换壳、回滚、多应用

| 场景 | 做法 |
| --- | --- |
| 同一应用换壳 | 保留 `profileId`，重跑 `profileBuild` 得到新 `revisionId`，再 pack |
| 回滚 | 直接用上一份 verified 归档，不修改文件内容 |
| 更换稳定身份 | 新建 Profile 并重新验收（新 `applicationId`，或清掉旧状态目录） |
| 不同应用 | 不能共用一个 Profile 文件 |

清理默认构建输出（不会删显式持久目录里的身份源）：

```bash
bash ./gradlew --no-daemon profileClean
```

## 6. 需要留存扫描报告时（可选）

默认流程不要求任何报告。如果组织流程要求留存门禁和第三方扫描证据，
把 3.3 换成「打 candidate -> promote」，promote 会把三份报告嵌入正式归档：

```bash
bash ./gradlew --no-daemon profilePack \
  -PprofileBundleDir=/绝对路径/bundle \
  -PprofileArchiveOut=/绝对路径/app.candidate.dptprofile

bash ./gradlew --no-daemon profilePromote \
  -PprofileCandidate=/绝对路径/app.candidate.dptprofile \
  -PprofileReportDir=/绝对路径/reports \
  -PprofileArchiveOut=/绝对路径/app.dptprofile
```

报告目录需包含 `gate-report.json`、`scan-baseline.json`、`scan-candidate.json`，
且三份报告绑定同一个 `profileId`、`revisionId` 和候选 APK SHA-256。
不要把 `.candidate.dptprofile` 改名成 `.dptprofile` 绕过校验，Reader 会同时检查文件名和清单状态。

## 7. 常见失败

| 错误码 / 现象 | 原因与处理 |
| --- | --- |
| `DPT-PROFILE-3004` | 传入了 candidate；用 `-PprofileProduction=true` 重新打包，或先 promote |
| `DPT-PROFILE-2002` | APK 的 `applicationId` 与 Profile 绑定不一致；不能跨应用复用 |
| `DPT-PROFILE-2003` | 协议/生成器/核心版本不兼容；换用同一构建产出的 `dpt.jar` |
| `DPT-PROFILE-2004` | 归档或契约哈希失败；重新生成，不要手改 JSON |
| `DPT-PROFILE-2005` | APK 有业务 SO 但没有 `lib/arm64-v8a` |
| `DPT-PROFILE-3001` | 归档结构/契约/DEX/ELF/哈希无效 |
| `DPT-PROFILE-3002` | 输出文件已存在；换输出路径，工具不会覆盖 |
| 加固仍读共享壳目录 | 没传 `--profile`，或跑的是不含 Profile 类的旧 `dpt.jar` |
| `apksigner` 报 `META-INF/*.version not protected` | 依赖库里版本描述文件的既有现象，不影响 v1/v2 验签与安装 |

## 8. 交付验收

静态检查：

```bash
bash ./gradlew --no-daemon :dpt:test
git diff --check
shasum -a 256 input.apk input_protected.apk
```

真机验收（优先真实 `arm64-v8a` 设备，单次安装即可）：

```bash
adb install -r input_protected.apk
adb shell monkey -p com.example.app 1
```

记录设备型号、Android 版本、ABI、输入/输出 SHA-256、安装与启动结果、主要业务路径、二次启动结果。
检查 `logcat` 中不存在 `Fatal signal`、`UnsatisfiedLinkError`、`dlopen failed`、JNI 注册失败或 DEX 恢复异常。

验收机上不要执行卸载、`pm clear` 或清空应用数据；只有重复安装、跨版本或矩阵测试才使用临时 AVD，
并在结束后清理该 AVD 和临时目录。
