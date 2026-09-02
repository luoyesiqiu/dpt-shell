## ADDED Requirements

### Requirement: Profile 必须预先显式创建
系统必须（MUST）通过独立管理入口显式创建 Profile，并将一个 `applicationId` 精确绑定到一个不透明 Profile ID。APK 加固入口不得创建、补建或重建 Profile，也不得从 `applicationId` 拼接或可逆派生 Profile ID 和壳内标识。

#### Scenario: 显式创建新应用 Profile
- **WHEN** 开发者通过 Profile 管理入口为尚未注册的 `applicationId` 创建 Profile
- **THEN** 系统生成不透明 Profile ID、稳定随机身份、源配置和身份锁，并持久化唯一绑定

#### Scenario: 重复创建同一应用 Profile
- **WHEN** 开发者再次为已经注册的 `applicationId` 执行创建操作
- **THEN** 系统报告并返回原 Profile，且不修改 Profile ID、稳定标识、JNI 映射或 `build-key`

#### Scenario: 拒绝冲突绑定
- **WHEN** 请求把同一 `applicationId` 绑定到第二个 Profile，或把同一 Profile 绑定到第二个 `applicationId`
- **THEN** 系统拒绝操作并报告冲突双方，不改写现有注册表

#### Scenario: 加固未注册应用
- **WHEN** APK 加固入口读取到一个未注册的 `applicationId`
- **THEN** 加固在第一次 Manifest、DEX、SO 或配置写入前失败，并且注册表和 Profile 目录保持不变

### Requirement: Profile 身份必须稳定
系统必须（MUST）把 Profile ID、稳定随机源、Profile 专属包名、代理入口名、JNI 语义映射、SO/资产/目录名和 `build-key` 固化在非构建目录中的身份锁。普通清理、重复构建、Recipe 池扩充和 Revision 升级不得隐式修改这些身份字段。

#### Scenario: 普通清理后重新构建
- **WHEN** 对工程执行普通 `clean` 后重新构建已有 Profile
- **THEN** Profile ID、类名、JNI 方法名、资产名、SO 名、目录名和 `build-key` 与清理前一致

#### Scenario: Recipe 池扩充后构建已有 Profile
- **WHEN** 新 Recipe 加入注册表后构建已有 Profile
- **THEN** 系统不修改该 Profile 的稳定身份字段，Recipe 变化只能通过显式新 Revision 记录

#### Scenario: 已有绑定缺少身份锁
- **WHEN** 注册表存在目标绑定但身份锁缺失、不可解析或哈希不一致
- **THEN** 系统停止构建和加固，不得静默生成一套新身份

### Requirement: Profile 必须使用不可变 Revision 升级
系统必须（MUST）将 Profile 身份和发布产物分离。每次显式升级必须生成不可变候选 Revision；候选只有在全部硬门槛通过后才能原子激活。失败候选不得修改当前活动 Revision，系统必须允许回滚到曾经完整验证通过的历史 Revision。

#### Scenario: 首次构建候选 Revision
- **WHEN** 已创建 Profile 尚无活动 Revision并执行 Profile 构建
- **THEN** 系统生成包含 Recipe、Overlay、生成器、核心源码、工具链和产物摘要的候选 Revision，但 APK 加固仍不得使用未激活候选

#### Scenario: 成功发布候选 Revision
- **WHEN** 候选 Revision 的契约、静态、相似度、包体、性能、行为和扫描硬门槛全部通过
- **THEN** 系统将 Revision 作为只读产物发布，并原子更新活动指针

#### Scenario: 升级候选验证失败
- **WHEN** 已存在活动 Revision 的 Profile 构建新候选，但任一硬门槛失败
- **THEN** 系统将候选标记为失败且不修改活动指针，旧活动 Revision 继续可用于 APK 加固

#### Scenario: 回滚历史 Revision
- **WHEN** 开发者请求回滚到同一 Profile 中曾经通过全部硬门槛的历史 Revision
- **THEN** 系统校验历史产物完整性后原子切换活动指针，不重新构建或改写该 Revision

#### Scenario: 拒绝回滚未验证候选
- **WHEN** 开发者请求激活从未通过全部硬门槛的候选或失败 Revision
- **THEN** 系统拒绝操作并保持当前活动 Revision 不变

### Requirement: Profile 必须由完整受测 Recipe 生成
系统必须（MUST）从已经作为整体通过契约和 APK 启动矩阵的 Recipe 中生成 Profile 专属 Java/Native 源码。系统不得在创建 Profile 时任意组合未经整体验证的独立策略槽，首版必须至少提供两个完整 Recipe。

#### Scenario: 显式选择有效 Recipe
- **WHEN** Profile 源配置指定一个已登记且兼容当前协议的 Recipe
- **THEN** 生成器使用该完整 Recipe 并把 Recipe ID 和版本写入候选 Revision

#### Scenario: 自动选择 Recipe
- **WHEN** Profile 源配置未指定 Recipe
- **THEN** 生成器只在已验证 Recipe 中选择与其他应用历史 Revision 结构相似度最低且满足全部门槛的候选

#### Scenario: 拒绝未验证 Recipe
- **WHEN** Profile 指定不存在、未完成整体验证或协议版本不兼容的 Recipe
- **THEN** 候选构建失败，不得使用其他 Recipe 静默兜底

#### Scenario: 没有 Recipe 满足重复度门槛
- **WHEN** 所有已验证 Recipe 生成的候选均超过跨应用结构相似度门槛
- **THEN** 系统阻止 Revision 发布，并要求补充受限 Overlay 或新增完整受测 Recipe

### Requirement: Profile 必须支持受约束的手工 Overlay
系统必须（MUST）允许开发者为单个 Profile 提供可信 Java/C++ Overlay，并把 Overlay 内容摘要写入 Revision。Overlay 只能实现启动协调、SO 处理、路径和配置适配及 Profile 私有辅助代码，不能覆盖不可变协议核心。

#### Scenario: 构建合法 Java 或 Native Overlay
- **WHEN** Overlay 只实现登记的扩展接口，且没有改变固定契约、依赖或 Manifest
- **THEN** 系统将 Overlay 纳入隔离源码、Revision 摘要、结构相似度和完整回归矩阵

#### Scenario: Overlay 改变 JNI 或运行协议
- **WHEN** Overlay 定义或修改 `JNI_OnLoad` 必要顺序、JNI 语义、JVM 签名、DEX/CodeItem 格式、密钥派生、ART Hook 或组件恢复语义
- **THEN** 构建期校验失败并阻止候选发布

#### Scenario: Overlay 引入越界能力
- **WHEN** Overlay 增加 Manifest 权限、导出组件、网络依赖、远程下载或远程动态代码加载
- **THEN** 依赖、Manifest 或产物扫描必须失败，当前活动 Revision 保持不变

#### Scenario: Overlay 覆盖生成文件
- **WHEN** Overlay 试图覆盖生成的代理入口、JNI Bridge 声明、Native 注册表或规范化契约
- **THEN** 生成器拒绝该 Overlay，并报告发生冲突的文件或语义 ID

### Requirement: 系统必须使用单一跨层 Profile 契约
系统必须（MUST）从同一份规范化 Profile 契约派生打包器配置、Manifest 代理入口、Java native 声明、Native JNI 注册表、业务 DEX 注入引用、SO 名、资产路径、运行目录、CMake 参数和 R8 keep 规则。JNI 语义 ID 对应的方法名和 JVM 签名在所有消费者之间必须一致。

#### Scenario: 生成一致的 JNI 契约
- **WHEN** 构建 Profile 壳 DEX 和 Native SO
- **THEN** 每个 JNI 语义 ID 在 Java 声明、Native 注册表和打包器注入配置中具有相同的 Profile 专属方法名和固定 JVM 签名

#### Scenario: JNI 方法签名失配
- **WHEN** Java、Native 或打包器中的任一 JNI 名称或签名与规范化契约不一致
- **THEN** 系统在发布前失败，并报告具体语义 ID、期望值和失配方

#### Scenario: Manifest 入口校验
- **WHEN** Profile 声明代理 Application 或代理 AppComponentFactory
- **THEN** 系统验证对应类存在于 Profile 壳 DEX，且 Manifest 写入值与契约完全相同

### Requirement: Profile Bundle 必须隔离且完整
系统必须（MUST）为每个 Profile Revision 发布独立、只读的 Bundle。Bundle 必须包含 Revision 清单、Profile 契约、产物哈希、`build-key`、壳 `classes.dex` 和 arm64 壳 SO，并声明兼容的 Profile 协议版本和生成器版本。

#### Scenario: 发布有效 arm64 Bundle
- **WHEN** 候选构建成功且全部本地与外部门槛通过
- **THEN** 系统将完整 Bundle 原子发布到该 Profile 的不可变 Revision 目录，并记录所有必需文件的 SHA-256

#### Scenario: Bundle 产物被修改
- **WHEN** 壳 DEX、arm64 SO、`build-key`、Profile 契约或 Revision 清单与记录哈希不一致
- **THEN** 系统拒绝激活或使用该 Revision，并报告损坏文件

#### Scenario: Bundle 缺少 arm64 壳 SO
- **WHEN** 候选或活动 Bundle 中不存在唯一预期的 `arm64-v8a` 壳 SO
- **THEN** 系统拒绝发布或加固，不使用其他 ABI 兜底

### Requirement: 首版 Profile 必须以 arm64 为目标 ABI
系统必须（MUST）把 `arm64-v8a` 作为首版 Profile 创建、构建、发布和 APK 回归的必需 ABI。其他 ABI 不得成为首版发布前置条件，也不得被 Profile 加固链隐式混入输出。

#### Scenario: 构建首版 Profile
- **WHEN** 开发者构建首版 Profile Revision
- **THEN** 系统只要求并验证 arm64 壳产物，Revision 和 Bundle 明确声明 `arm64-v8a`

#### Scenario: 输入 APK 只有非 arm64 业务库
- **WHEN** 输入 APK 含有业务 Native 库但不含 `arm64-v8a` 版本
- **THEN** 加固在第一次 Manifest、DEX 或 SO 写入前失败并报告 ABI 不兼容

#### Scenario: 输入 APK 没有业务 Native 库
- **WHEN** 输入 APK 不包含业务 Native 库且选择有效 arm64 Profile
- **THEN** 系统可以继续加固，并明确输出只保证在 arm64 设备上运行

### Requirement: APK 加固必须严格选择活动 Revision
系统必须（MUST）从输入 APK Manifest 读取 `applicationId`，通过注册表精确选择 Profile，再通过活动指针选择 Revision，并在处理业务内容前验证目标应用、协议版本、arm64 声明和 Bundle 完整性。任何失败均不得回退到全局 `shell-files` 或共享默认壳。

#### Scenario: 使用匹配活动 Revision 加固 APK
- **WHEN** 输入 APK 存在精确 Profile 绑定、有效活动 Revision 且全部完整性校验通过
- **THEN** 打包器使用该 Revision 的壳 DEX、arm64 SO、JNI 映射、资产名称和 `build-key` 继续 APK 加固

#### Scenario: Profile 尚无活动 Revision
- **WHEN** 输入 APK 的 Profile 已创建但没有活动 Revision
- **THEN** 加固在修改 APK 内容前失败，并明确报告 Profile 尚未发布可用 Revision

#### Scenario: Profile 目标应用不匹配
- **WHEN** 注册表、活动指针或 Bundle 声明的 `applicationId` 与输入 APK 不一致
- **THEN** 加固立即失败，不得使用该 Revision 或任何默认 Bundle

#### Scenario: Profile 协议版本不兼容
- **WHEN** `dpt.jar` 不支持活动 Revision 声明的 Profile 协议或生成器版本
- **THEN** 加固立即失败并报告工具版本与 Revision 版本

### Requirement: 加固中间数据必须隔离
系统必须（MUST）把活动 Revision Bundle 作为只读输入，并将重写壳 DEX、垃圾 DEX、写入 APK 随机密钥后的 SO 及其他单次加固中间文件放入当前 APK 的随机隔离工作目录。并发任务不得修改 Bundle 或其他任务的中间数据。

#### Scenario: 并发加固两个 APK
- **WHEN** 两个加固任务同时使用相同或不同 Profile
- **THEN** 每个任务只写入自己的工作目录，全部活动 Bundle 内容及哈希在任务前后保持不变

#### Scenario: 单次加固任务失败
- **WHEN** 加固在生成中间 DEX、改写 SO 或构建 APK 期间失败
- **THEN** 系统可以清理该任务工作目录，但不得删除或改写注册表、身份锁、Revision 和活动指针

### Requirement: 必须保持现有 APK 加固协议与应用行为
Profile 差异化必须（MUST）保持现有 APK 加固数据协议、密钥派生和运行时恢复行为。系统必须保留 Android 5～8 的代理 Application 启动链和 Android 9+ 的 AppComponentFactory 启动链，并恢复原 Application、AppComponentFactory、ClassLoader 和应用组件行为。

#### Scenario: 使用自定义 Application 的 APK
- **WHEN** 加固并启动声明自定义 Application 的 APK
- **THEN** 壳完成 DEX 恢复后创建原 Application，并按既有语义调用其生命周期

#### Scenario: 使用自定义 AppComponentFactory 的 APK
- **WHEN** 加固并启动声明自定义 AppComponentFactory 的 APK
- **THEN** 壳恢复并委托原 AppComponentFactory 创建 Application 和应用组件

#### Scenario: 两类 Android 启动路径
- **WHEN** 加固 APK 分别运行在 Android 5～8 和 Android 9+ 的受支持 arm64 环境
- **THEN** 对应代理启动链按固定依赖顺序完成 SO 加载、配置读取、JNI 注册、业务 DEX 恢复和原组件恢复

#### Scenario: APK 级随机密钥
- **WHEN** 使用同一活动 Revision 分别加固两个 APK
- **THEN** 每次加固继续生成独立 APK 级随机密钥，并使用 `applicationId + "_" + buildKey` 派生配置密钥

### Requirement: 发布壳必须删除已知公开标识
发布的 Profile 壳 DEX、壳 SO 和注入资产必须（MUST）排除 Demo Activity、Demo Application、Demo AppComponentFactory、示例界面资源、示例文案和已登记旧壳标识。Android 框架要求的代理组件、资产、目录和内部符号必须使用 Profile 专属名称替代。

#### Scenario: 扫描 Profile 壳产物
- **WHEN** 候选 Revision 执行静态标识扫描
- **THEN** `com.luoye`、`com.luoyesiqiu`、`MainActivity`、`ProxyApplication`、`ProxyComponentFactory`、`JniBridge`、`DPT_UNKNOWN_DATA`、`d_shell_data_001`、`i11111i111.zip`、`OoooooOooo`、`vwwwwwvwww`、`dpt-libs`、`code_cache` 和固定 JunkClass 在壳扫描范围内的命中数均为零

#### Scenario: 业务 APK 含有同名内容
- **WHEN** 输入业务 APK 自身包含与禁用清单相同的普通类名或字符串
- **THEN** 静态门槛只扫描 Profile 壳 DEX、壳 SO 和注入资产，不因业务代码内容误拒绝候选

#### Scenario: 移除 Demo 后验证代理能力
- **WHEN** 发布壳不再包含 Launcher Demo Activity 和示例应用资源
- **THEN** 系统使用专用测试 APK 验证代理 Application 和 AppComponentFactory 启动能力

### Requirement: 不同应用必须满足结构差异化硬门槛
系统必须（MUST）验证不同 `applicationId` 没有复用同一套 Profile 壳产物，并对 Profile 可变层执行去标识化结构比较。Java Jaccard 相似度必须不高于 `0.80`，Native Jaccard 相似度必须不高于 `0.85`。

#### Scenario: 比较两个应用的 Profile 产物
- **WHEN** 候选 Revision 与其他 `applicationId` 曾经激活过的 Revision 比较
- **THEN** 壳 DEX、arm64 壳 SO、Profile 专属标识和 `build-key` 均不相同，且 Java 与 Native 可变层分别满足相似度门槛

#### Scenario: 结构比较忽略表面重命名
- **WHEN** 系统提取 Java 或 Native 结构特征
- **THEN** 比较前移除包/类/方法/符号名称、字符串值、地址、重定位和调试信息，单纯重命名不能降低结构分数

#### Scenario: 候选超过结构门槛
- **WHEN** Java 相似度大于 `0.80` 或 Native 相似度大于 `0.85`
- **THEN** 系统阻止候选激活并报告比较对象、特征范围和实际分数

#### Scenario: 同一应用升级 Revision
- **WHEN** 候选与同一 Profile 的历史 Revision 进行报告比较
- **THEN** 系统记录完整产物相似度但不将其作为跨应用结构门槛阻断对象

### Requirement: APK 包体和壳 SO 必须满足硬门槛
系统必须（MUST）基于同一输入 APK、签名方式、arm64、`-S` 和工具链记录旧 DPT 基线。候选 APK 不得超过旧加固产物 `2 MiB`，且候选相对原 APK 的增幅不得超过旧基线增幅 `1` 个百分点；候选 arm64 壳 SO 不得超过旧 arm64 壳 SO 的 `110%`。

#### Scenario: 候选满足包体双门槛
- **WHEN** 候选 APK 同时满足绝对增量和相对增幅限制，且 arm64 壳 SO 满足比例限制
- **THEN** 包体门槛通过，并记录输入、基线和候选文件的 SHA-256 与字节数

#### Scenario: 任一包体门槛超限
- **WHEN** 候选 APK 或 arm64 壳 SO 超过任一限制
- **THEN** 系统阻止 Revision 激活并报告基线、阈值和实际值

### Requirement: 启动、加固和构建性能必须满足硬门槛
系统必须（MUST）在固定环境中对候选和旧 DPT 基线执行可比测试。至少 30 次冷启动的 P50 不得超过基线 `5%`、P95 不得超过 `10%`；10 次已有 Profile 加固的 P50 不得超过基线 `10%`、P95 不得超过 `15%`；首次 arm64 Profile 构建不得超过旧 arm64 壳构建基线 `25%`。

#### Scenario: 性能指标全部通过
- **WHEN** 候选在固定设备和 CI 环境完成规定次数的启动、加固和构建测试
- **THEN** 系统记录原始样本、P50、P95、环境和基线，且只有全部指标不超限时通过性能门槛

#### Scenario: 冷启动发生崩溃或 ANR
- **WHEN** 任一次规定的冷启动出现崩溃或 ANR
- **THEN** 行为与性能门槛立即失败，不使用其余统计结果放行

#### Scenario: 性能证据不完整
- **WHEN** 测试次数不足、环境与基线不一致或报告无法关联候选 APK SHA-256
- **THEN** 系统将门槛视为失败并阻止 Revision 激活

### Requirement: 发布必须保持权限组件并通过扫描门槛
候选加固 APK 必须（MUST）相对同一输入的旧 DPT 产物不新增权限、导出组件、网络能力或远程动态代码行为。同一扫描器集合和时间窗口内，多引擎检测数不得高于旧 DPT 基线，且不得新增明确的 Trojan、Banker、Spyware 或 RAT 家族标签。

#### Scenario: 权限和组件保持不变
- **WHEN** 系统比较旧 DPT 与候选加固 APK 的 Manifest、依赖和静态行为报告
- **THEN** 除 Profile 专属代理名称外，不存在新增权限、导出组件、网络能力或远程动态代码行为

#### Scenario: 候选新增高风险行为
- **WHEN** 候选相对基线新增权限、导出组件、网络能力或远程动态代码行为
- **THEN** 系统阻止 Revision 激活并报告新增项来源

#### Scenario: 多引擎结果不劣于基线
- **WHEN** 开发者提供与候选 APK SHA-256 对应、扫描器集合和时间窗口可比的扫描报告
- **THEN** 检测数不高于旧 DPT 基线且没有新增指定高风险家族标签时扫描门槛通过

#### Scenario: 扫描报告缺失或不可比
- **WHEN** 扫描报告缺失、过期、哈希不匹配、扫描器集合不完整或无法与基线比较
- **THEN** 系统严格阻止 Revision 激活，不自动上传 APK 或跳过门槛

### Requirement: APK Profile 流程不得改变外部交付语义
系统必须（MUST）仅在 APK 壳选择、构建和运行链路中引入 Profile，不得让 Profile 依赖签名、zipalign、验签或渠道输入。Profile 输出必须继续能够交给既有 APK 对齐、签名和验签流程处理。

#### Scenario: 完成 Profile APK 加固
- **WHEN** 打包器使用有效活动 Revision 生成加固 APK
- **THEN** 输出保持既有 APK 交付接口，不新增签名或渠道参数要求

#### Scenario: 未提供渠道输入
- **WHEN** 调用方只执行 APK Profile 加固而不提供渠道配置
- **THEN** Profile 选择和 APK 产物不受影响
