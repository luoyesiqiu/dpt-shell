## 背景

当前 `dpt` 解包 APK 并取得 `applicationId` 后，将壳包名解析为 `{applicationId}.shell`，再从与 `dpt.jar` 相邻的单一 `shell-files` 目录读取 `classes.dex`、各 ABI 的 SO 和 `build-key`。`shell` 构建也会覆盖同一个输出目录，普通 `clean` 会删除构建标识并在后续构建中重新生成 SO 名和 `build-key`。

运行时存在两条必须保持兼容的启动链：Android 5～8 主要由代理 Application 在 `attachBaseContext` 中解压并加载 SO、恢复业务 DEX 和原 Application；Android 9+ 还依赖代理 AppComponentFactory 在 `instantiateClassLoader`、`instantiateApplication` 等回调中完成相同职责。打包器还会向业务 DEX 的静态初始化方法注入对壳 JNI 方法的反射调用，因此打包器、Java 壳、Native 壳和构建配置之间存在不可拆分的名称、签名及顺序契约。

本变更以降低不同 `applicationId` 壳产物的公开静态重复度为目标。它可以保证 Profile 独立、已知标识消失、可变层结构相似度低于约定门槛以及 APK 行为通过回归，但不能消除签名证书、业务代码、第三方库、网络和运行行为形成的全部关联信号。

## 目标与非目标

**目标：**

- 不同 `applicationId` 显式绑定不同 Profile，拥有独立的生成源码、稳定标识、构建目录、Revision 和发布 Bundle。
- APK 加固只读取预先创建且已激活的 Profile Revision；不存在、未激活、损坏或不兼容时严格失败，不自动创建也不回退共享壳。
- 同一 `applicationId` 稳定复用一个 Profile，并通过不可变 Revision 显式升级和回滚；失败升级不得影响当前活动版本。
- 使用经过整体测试的 Recipe 和受约束的手工 Overlay 改变 Java/Native 代码组织，同时把加固协议及生命周期顺序固定在不可变核心中。
- 由同一 Profile 契约驱动打包器注入、Java native 声明、Native 注册表、Manifest 入口、资源路径和构建参数，避免跨层失配。
- 首版以 `arm64-v8a` 为必需 ABI，保证 APK 加固结果可安装、可启动，并保持原 Application、AppComponentFactory 和应用组件行为。
- 以静态标识、结构相似度、包体、性能、误报和行为矩阵作为 Revision 激活硬门槛。

**非目标：**

- 不实现或验收 AAB Profile；不主动修改 AAB 代码路径，也不对本变更后的 AAB 兼容性作承诺。
- 不修改 zipalign、签名、验签或渠道包流程，也不让 Profile 与渠道配置绑定。
- 不修改 `/Users/admin/AndroidStudioProjects/lanling_couduis/tools` 等消费端脚本；消费端适配使用独立变更。
- 不允许每个应用长期维护完整壳工程分支，也不允许 Overlay 覆盖固定协议核心。
- 不改变 DEX/CodeItem 数据格式、加密算法、密钥派生、ART Hook 行为或 Android 版本适配核心。
- 不引入无约束随机垃圾代码，不承诺两个 APK 完全不可关联，也不以规避安全产品检测为目标。

## 设计决策

### 1. Profile 管理与 APK 加固使用分离入口

Profile 的创建和构建依赖壳源码、Gradle 与 CMake，因此由 `dpt-shell` 根工程提供独立 Gradle 入口：

```text
profileCreate    显式创建 applicationId 绑定和稳定身份
profileBuild     从 Recipe、Overlay 和源码版本构建候选 Revision
profileShow      查看绑定、活动 Revision、历史 Revision 和门禁报告
profilePublish   全部硬门槛通过后原子激活候选 Revision
profileRollback  激活一个曾经完整验证通过的历史 Revision
```

`profileCreate` 对同一 `applicationId` 幂等：已有绑定时只报告当前 Profile，不修改任何身份字段；如果请求把同一应用绑定到第二个 Profile，或把同一 Profile 用于第二个应用，则严格失败。

`dpt.jar -f <apk>` 保持为 APK 加固入口。它只能在解包并读取 Manifest 后查询 Profile，不能调用创建、构建或发布逻辑。这样可以保证生产加固不会因一次缺失配置而隐式生成未经审核的壳。

备选方案是在首次 APK 加固时自动创建 Profile。该方案操作简单，但会把随机身份生成、源码构建和发布校验混入生产加固，无法满足预创建和严格失败要求，因此不采用。

### 2. Profile 身份稳定，发布产物使用不可变 Revision

Profile 生命周期如下：

```text
未注册
  -> profileCreate
已注册，无活动 Revision
  -> profileBuild
候选 Revision
  -> 本地门禁失败 -> REJECTED
  -> profilePublish 全部门禁通过 -> VERIFIED + ACTIVE

ACTIVE
  -> profileBuild -> 新候选 Revision
  -> 新候选通过 -> 原子切换 ACTIVE
  -> 新候选失败 -> 旧 ACTIVE 保持不变
  -> profileRollback -> 切换到曾 VERIFIED 的旧 Revision
```

Profile ID、稳定随机源、Profile 专属包名、代理入口名、JNI 语义映射、SO/资产/目录名和 `build-key` 属于 Profile 身份，写入 `identity.lock.json`。普通构建、`clean`、Recipe 池扩充和 Revision 升级不得隐式轮换这些字段。

Recipe ID、Overlay 摘要、生成器版本、不可变核心版本、工具链版本、生成源码摘要和产物哈希属于 Revision。每次显式升级生成单调递增的 `revisionId`，发布后不可修改。

首版不提供 Profile 身份主动轮换。需要更换稳定身份时应创建新的 Profile 迁移变更，避免把升级与身份重置混为一体。

### 3. 使用源配置、身份锁、Revision 和活动指针四层数据

```text
profiles/
  index.json
  source/<profile-id>/
    profile.yaml
    identity.lock.json
    overlay/
  revisions/<profile-id>/
    r000001/
      revision.json
      reports/

executable/
  dpt.jar
  profiles/<profile-id>/
    active.json
    revisions/
      r000001/
        profile.json
        artifact-sha256.json
        shell-files/
          build-key
          dex/classes.dex
          libs/arm64/<profile-so-name>.so
```

- `index.json`：保存 `applicationId -> profileId` 精确映射，通过进程锁和临时文件原子替换更新。
- `profile.yaml`：声明目标 `applicationId`、Recipe、`arm64-v8a` 和 Overlay 位置，不作为 APK 内运行时数据。
- `identity.lock.json`：保存不可逆随机身份、稳定标识、JNI 映射和 `build-key`；不位于 Gradle `build/` 下。
- `revision.json`：保存 Revision 输入摘要、Recipe、Overlay SHA-256、生成器/核心/工具链版本、门禁状态和创建时间。
- `profile.json`：打包器使用的规范化契约，声明目标应用、Profile/Revision/协议版本、Manifest 入口、JNI 映射、SO/资产/目录名和 ABI。
- `artifact-sha256.json`：保存清单、壳 DEX、arm64 SO、`build-key` 及必要报告的哈希。
- `active.json`：只保存当前活动 `revisionId` 和清单哈希；在全部门槛通过后原子替换。

普通 `clean` 只能删除隔离生成源码、CMake/Gradle 中间目录和未发布候选，不得删除注册表、源配置、身份锁、已发布 Revision 或活动指针。

### 4. 使用完整受测 Recipe，不开放策略笛卡尔积

壳工程拆分为不可变协议核心、完整 Recipe、Profile 生成器和可选 Overlay：

```text
不可变协议核心 ─┐
受测 Recipe ─────┼─> Profile 生成器 ─> 隔离源码 ─> DEX/SO
受限 Overlay ────┤
身份锁/Revision ─┘
```

一个 Recipe 是已经作为整体完成契约和 APK 启动验证的 Java/Native 组合，不允许在 Profile 创建时把若干策略槽任意排列。首版至少提供两个完整 Recipe，覆盖以下可变区域：

- Java 启动协调的类拆分、委托层次和阶段对象。
- Java SO 定位、解压、CRC 校验、权限设置和加载的辅助组织。
- Native 启动协调的内部函数拆分和编译单元组织。
- Native 路径构建、配置适配和内部字段映射。

创建 Profile 时可以显式指定 Recipe；未指定时，生成器从已验证 Recipe 中选择与其他应用历史 Revision 结构相似度最低且能通过硬门槛的候选，并把选择结果写入 Revision。没有候选满足门槛时构建失败，开发者需要补充 Overlay 或新增经过完整验证的 Recipe。

### 5. 手工 Overlay 只扩展安全槽位

开发者可以在 Profile 的 `overlay/` 中提供可信 Java/C++ 源码。Overlay 参与 Revision 摘要和结构相似度计算，但不是不受信任代码沙箱。

允许的 Java Overlay：

- 启动阶段内部实现和委托组织。
- SO 定位、解压、校验与加载辅助实现。
- ABI、路径和配置适配器。
- Profile 私有辅助类。

允许的 Native Overlay：

- 启动辅助函数和内部编译单元组织。
- 路径、配置和字段适配器。
- Profile 私有类型、namespace 和辅助函数。

Overlay 不得：

- 定义或改变 `JNI_OnLoad` 必要顺序、JNI 语义 ID、JVM 签名或注册数量。
- 改变 DEX 组合、CodeItem、密钥派生、配置加密、ART Hook 或组件恢复协议。
- 修改 Manifest 权限、导出组件或应用业务组件。
- 引入网络权限、网络依赖、远程下载或远程动态代码加载。
- 覆盖生成的代理入口、JNI Bridge 声明和跨层契约文件。

构建期同时执行接口白名单、禁止 API/符号、依赖变化和最终 Manifest/产物扫描。任一检查失败只拒绝候选 Revision，不影响当前活动版本。

备选方案是为每个应用复制完整壳源码。它能形成较大差异，但核心安全修复和 Android 适配会在多个分支间漂移，因此不采用。

### 6. 使用单一规范化契约同步打包器、Java 和 Native

Profile 生成阶段产生一份规范化契约，再派生：

- 打包器读取的 `profile.json`。
- Manifest 中代理 Application 和代理 AppComponentFactory 的类名。
- Java JNI Bridge 类及 native 方法声明。
- Native `JNINativeMethod` 注册表和固定 JVM 签名。
- 业务 DEX 静态初始化注入使用的 JNI 类名与方法名。
- SO 名、壳资产名、运行目录名、CMake 宏、Java 构建常量和 R8 keep 规则。

JNI 使用“稳定语义 ID -> Profile 专属方法名 -> 固定 JVM 签名”的映射。发布前校验器逐项核对 Java 声明、Native 注册表、打包器注入引用和 Manifest 入口；任何缺失、重复或签名不一致均拒绝发布。

### 7. 固定不可变运行协议和生命周期顺序

以下契约不参与 Recipe 或 Overlay 变化：

- `shellDex + compressedDexZip + 4 字节长度` 的组合格式。
- MultiDex CodeItem 格式、提取、加密和恢复流程。
- APK 级随机密钥以及 `applicationId + "_" + buildKey` 的配置密钥派生。
- `.bitcode` 加密、RC4 key slot 写入和现有风险检查语义。
- Android 5～8 与 Android 9+ 两条启动链的必要先后关系。
- 原 Application、AppComponentFactory、Activity、Service、Receiver、Provider 和 ClassLoader 的恢复行为。

`JNI_OnLoad` 必须保持“读取并解密配置 -> 执行既有风险检查 -> 注册 JNI”的顺序。生成器可以改变 Profile 专属方法名和内部组织，但不能改变依赖关系和失败语义。

### 8. Profile 构建、候选发布和 APK 加固使用隔离目录

每个 Profile Revision 使用独立的生成源码、Gradle、CMake、缓存和候选输出目录。缓存键至少包括身份锁摘要、Recipe ID、Overlay 摘要、生成器版本、核心源码版本和工具链版本。

候选 Bundle 先写入临时目录，完成本地门禁后进入候选区；只有 `profilePublish` 验证全部本地与外部证据后，才把不可变 Bundle 移入 Revision 目录并原子替换 `active.json`。并发创建、构建和发布使用 Profile 级文件锁，不能覆盖其他 Profile 或活动 Revision。

单次 APK 加固把活动 Bundle 视为只读输入。重命名后的壳 DEX、垃圾 DEX、写入 APK 随机密钥后的 SO 和其他中间产物全部写入该 APK 的随机工作目录，禁止修改 Bundle。

### 9. 首版只以 arm64 作为 Profile ABI

首版 `profile.yaml` 和 Bundle 必须声明 `arm64-v8a`，发布目录使用现有 `arm64` 映射。Profile 构建只把 arm64 壳 SO 作为硬门槛，`armeabi-v7a`、`x86` 和 `x86_64` 不进入本次任务和测试矩阵。

如果输入 APK 包含业务 Native 库但没有 `arm64-v8a`，加固必须在 Manifest、DEX 和 SO 修改前报告 ABI 不兼容。没有业务 Native 库的 APK 可以使用 arm64 Profile，但输出只保证在 arm64 设备上安装运行。

### 10. 将差异化和已知标识设置为本地硬门槛

发布壳 DEX/SO 的已知旧标识命中数必须为零。首版清单至少包括：

```text
com.luoye
com.luoyesiqiu
MainActivity
ProxyApplication
ProxyComponentFactory
JniBridge
DPT_UNKNOWN_DATA
d_shell_data_001
i11111i111.zip
OoooooOooo
vwwwwwvwww
dpt-libs
code_cache
com/luoye/dpt/junkcode/JunkClass
```

扫描范围限定为 Profile 壳 DEX、壳 SO 和注入资产，避免业务 APK 自身合法的同名内容造成误判。`JNI_OnLoad` 是平台入口，不属于禁用标识。

不同 Profile 的壳 DEX、arm64 壳 SO、全部 Profile 专属标识和 `build-key` 必须不同。SHA-256 只用于证明产物没有复用，不能代替结构相似度。

结构相似度只比较 Profile 可变层：

- Java：提取方法和控制流特征，去除包/类/方法名、字符串值和调试信息后计算 Jaccard，相似度必须 `<= 0.80`。
- Native：对可变层目标文件提取函数、基本块和指令特征，去除符号名、地址、重定位和调试信息后计算 Jaccard，相似度必须 `<= 0.85`。
- 比较对象：所有其他 `applicationId` 曾经激活过的 Revision，而不只是当前活动版本；同一 Profile 的历史 Revision 不作为跨应用阻断对象。
- 不可变协议核心和第三方库不计入阻断分数，但完整 DEX/SO 相似度继续记录在报告中。

任一候选超过门槛时不得激活。该门槛保证壳可变层重复度受控，不得表述为完全不可关联证明。

### 11. 将包体、性能、行为和误报设置为发布硬门槛

所有对比使用同一输入 APK、同一签名方式、`arm64-v8a`、`-S` 和固定工具链。基线来自切换 Profile 前的旧 DPT 产物，并以输入 APK SHA-256 和环境信息固化在报告中。

硬门槛如下：

- 最终 APK 同时满足：不超过旧 DPT 加固产物 `+2 MiB`；相对原 APK 的体积增幅不超过旧基线 `+1` 个百分点。
- arm64 壳 SO 不超过对应旧壳 SO 的 `110%`。
- 同设备至少执行 30 次冷启动；P50 不超过旧加固基线 `+5%`，P95 不超过 `+10%`，崩溃和 ANR 必须为零。
- 已有 Profile 的 APK 加固执行 10 次；P50 不超过旧 DPT 基线 `+10%`，P95 不超过 `+15%`。
- 首次 Profile arm64 构建在固定 CI 上不超过旧 arm64 壳构建基线 `+25%`。
- 加固 APK 不得相对旧 DPT 产物新增权限、导出组件、网络能力或远程动态代码行为。
- 同一扫描器集合和时间窗口内，多引擎检测数不得高于旧 DPT 基线；新增明确的 Trojan、Banker、Spyware 或 RAT 家族标签时直接失败。

已测样本中，原 APK 为 `158,287,943` 字节，旧加固 APK 为 `180,671,900` 字节，旧增幅约 `14.1413%`。按上述双门槛，该样本候选 APK 上限为 `182,254,779` 字节。当前 arm64 壳 SO 为 `719,152` 字节，对应上限为 `791,067` 字节。这些数值用于校准规则，通用实现仍按每个基线报告计算。

外部多引擎扫描不会由工具自动上传 APK。`profilePublish` 读取开发者手工提供、与候选 APK SHA-256 精确绑定的扫描报告；报告缺失、过期、哈希不匹配或扫描器集合不完整时严格失败。

### 12. APK 加固严格选择活动 Revision

APK 数据流如下：

```text
输入 APK
  -> 创建单次随机工作目录并解包
  -> 只读解析 Manifest applicationId 与业务 ABI
  -> 注册表精确查询 Profile
  -> 读取 active.json 并定位不可变 Revision
  -> 校验 applicationId、Profile/Revision/协议版本、arm64 和全部哈希
  -> 从 Profile 契约初始化本次加固上下文
  -> 写代理 Manifest、处理业务 DEX、合并壳 DEX、复制并改写临时 SO
  -> 写配置并执行既有 APK 构建
```

以下情况均须在第一次 Manifest、DEX、SO 或配置写入前失败，并输出稳定错误码、`applicationId`、Profile ID（如可用）和损坏字段：

- 应用没有 Profile 绑定。
- Profile 已创建但没有活动 Revision。
- 活动 Revision 目录、清单或文件缺失。
- `applicationId`、协议版本、生成器版本或 arm64 不兼容。
- 任一产物哈希不匹配。

APK Profile 链路不得读取全局 `executable/shell-files` 作为兜底。现有 AAB 分支不接入 Profile 选择，也不纳入本变更验收。

## 风险与权衡

- **严格切换会让未迁移应用无法加固**：新版 APK 入口不再有共享壳兜底。→ 在切换入口前为全部目标 `applicationId` 创建并激活首个 Revision，并提供 `profileShow` 预检。
- **Overlay 表面合法但改变生命周期时序**：可能只在特定 Android 版本崩溃。→ 代理入口和关键顺序留在不可变核心，Overlay 只能实现受限接口，每个 Revision 执行完整启动矩阵。
- **Java、Native 和打包器名称失配**：可能在 `RegisterNatives` 或业务类初始化时失败。→ 所有名称从单一契约派生，并在发布前做跨层签名核对。
- **相似度算法被简单重命名绕过**：会让门槛失去意义。→ 计算前移除名称、常量、地址和调试信息，只比较规范化结构特征。
- **Recipe 数量有限**：新 Profile 可能没有候选能通过跨应用相似度门槛。→ 严格阻止发布，要求补充 Overlay 或新增完整受测 Recipe，不降低门槛兜底。
- **Profile 身份数据丢失**：严格模式下已有应用无法继续构建。→ 身份锁和发布 Revision 位于非构建目录，普通 `clean` 不删除，并要求备份和完整性检查。
- **发布门槛成本较高**：每个 Revision 需要设备矩阵和外部扫描证据。→ 区分候选构建与激活；开发阶段可构建候选，但只有完整证据才能进入生产活动版本。
- **扫描器结果随时间变化**：相同 APK 的检测数可能波动。→ 基线与候选使用同一时间窗口和扫描器集合，报告记录版本与时间；无法形成可比结果时失败而不是放行。
- **arm64 首版限制设备范围**：非 arm64 设备不能运行输出。→ 加固前校验业务 ABI并在文档和错误信息中明确；其他 ABI 使用后续独立变更扩展。
- **Bundle 和 APK 中间文件并发污染**：可能导致错误产物或不可复现崩溃。→ Revision 只读、Profile 级发布锁、APK 随机工作目录和校验后原子激活。

## 迁移计划

1. 记录旧 DPT 的 arm64 壳、参考 APK 包体、加固耗时、冷启动、权限组件和扫描基线，固定测试环境与报告格式。
2. 引入 Profile/Revision 数据模型、注册表和管理入口，但暂不切换现有 APK 加固入口。
3. 将固定壳标识、JNI 映射和构建参数收敛到规范化契约，建立不可变核心、至少两个完整 Recipe 和受限 Overlay 接口。
4. 建立 arm64 隔离构建、候选 Revision、结构相似度及其他本地门禁，再接入设备和手工扫描报告门禁。
5. 为测试 `applicationId` 创建 Profile，完成候选构建、发布、升级失败保护和回滚验证。
6. 为实际需要加固的每个 `applicationId` 显式创建 Profile 并激活首个 Revision。
7. 切换 APK 加固入口为强制精确选择活动 Revision，移除 APK 路径下的共享默认壳回退和 Demo 发布内容。

工具级回滚可以恢复到切换前的 DPT 版本；Profile 注册表、身份锁和所有已发布 Revision 保持不变。Profile 级回滚通过 `profileRollback` 原子切换到曾验证通过的 Revision，不修改已经发布的 APK。

## 待确认项

没有阻塞实施的业务待确认项。显式预创建、严格失败、Revision 升级、受限手工 Overlay、APK-only、arm64、仅修改 `dpt-shell`、结构相似度阈值和全部非功能硬门槛均已确认。
