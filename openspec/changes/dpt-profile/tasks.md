> 执行约束：每个任务开始前必须使用 CodeGraph 核对相关符号的调用方、状态源、数据源和影响面；完成后立即勾选该任务。只做当前任务的最小闭环改动，不顺手重构，不修改 `/Users/admin/AndroidStudioProjects/lanling_couduis/tools`。

## 1. Profile 身份、注册表与 Revision 模型

- [x] 1.1 定义 Profile 源配置、身份锁、Revision 清单、活动指针、发布契约、产物哈希和门禁报告的数据模型及版本字段。
- [x] 1.2 为 `applicationId`、Profile ID、Revision ID、Profile 专属标识、JNI 映射和仅包含 `arm64-v8a` 的 ABI 集合实现严格字段校验。
- [x] 1.3 实现 `applicationId -> profileId` 一对一注册表、Profile 级文件锁、冲突检查和临时文件原子替换，禁止多重绑定。
- [x] 1.4 实现不透明 Profile ID、稳定随机源、包/类/JNI/SO/资产/目录名和 `build-key` 的首次生成，并写入非构建目录中的身份锁。
- [x] 1.5 实现已有 Profile 的严格加载：身份锁缺失、哈希不一致、版本不支持或字段损坏时失败，不得静默重建。
- [x] 1.6 实现候选、验证通过、活动和失败 Revision 状态，以及单调递增 Revision ID 和不可变发布约束。
- [x] 1.7 实现活动指针的原子激活和历史 Revision 回滚，只允许激活曾通过全部门槛且完整性有效的 Revision。
- [x] 1.8 调整 Gradle `clean`，只删除可再生成的源码、缓存和未发布候选，不删除注册表、身份锁、已发布 Revision 或活动指针。
- [x] 1.9 增加注册、幂等创建、冲突绑定、身份损坏、Revision 状态、激活、失败保护、回滚和普通清理的单元测试。

## 2. Profile 管理入口

- [x] 2.1 建立根工程 Profile Gradle 入口和共享参数解析，统一处理 `applicationId`、Profile ID、Revision ID、Recipe、Overlay 和报告目录。
- [x] 2.2 实现 `profileCreate`：只显式创建或返回既有绑定，不构建、不发布且不修改已有稳定身份。
- [x] 2.3 实现 `profileShow`：输出绑定、身份锁摘要、活动 Revision、历史 Revision、ABI 和各门禁状态，不修改任何文件。
- [x] 2.4 实现 `profileBuild` 的候选生命周期入口，加载 Profile 源配置并创建隔离候选 Revision，不自动激活。
- [x] 2.5 实现 `profilePublish` 和 `profileRollback` 入口，发布前汇总全部硬门槛，失败时保持旧活动 Revision。
- [x] 2.6 为缺少参数、Profile 不存在、无活动 Revision、无效候选、重复发布和非法回滚提供稳定错误码及可定位中文信息。
- [x] 2.7 增加五个管理入口的 Gradle TestKit 或等价集成测试，验证幂等性、严格失败和无隐式副作用。

## 3. 规范化跨层 Profile 契约

- [x] 3.1 使用 CodeGraph 梳理现有打包器、`ShellConfig`、代理组件、JNI Bridge、Native 注册表、静态初始化注入及构建常量，明确不可变协议字段和可变 Profile 字段。
- [x] 3.2 建立由 `dpt`、Java 壳、Native 壳和构建逻辑共享的规范化 Profile 契约，并实现确定性序列化和版本兼容校验。
- [x] 3.3 为全部 JNI 操作定义稳定语义 ID、固定 JVM 签名和 Profile 专属方法名映射，覆盖 Application、组件恢复、DEX 恢复和静态初始化调用。
- [x] 3.4 让 `ShellConfig`、Manifest 代理入口、SO/资产路径、运行目录、RC4 key slot 和 `build-key` 从当前 Profile 契约读取。
- [x] 3.5 修改业务 DEX 静态初始化注入逻辑，使 JNI Bridge 类名和初始化方法名来自 Profile 契约，不再写死 `JniBridge.clinit`。
- [x] 3.6 实现跨层契约校验器，逐项核对打包器引用、Java native 声明、Native 注册表、Manifest 入口、构建常量和固定 JVM 签名。
- [x] 3.7 增加契约序列化往返、版本不兼容、语义缺失、JNI 签名失配、Manifest 类缺失和构建常量失配的单元测试。

## 4. Recipe、生成器与手工 Overlay

- [x] 4.1 建立 Profile 代码生成器和按 Profile/Revision 隔离的 Java、C++、R8、CMake 生成目录，并记录生成器和不可变核心版本。
- [x] 4.2 建立完整 Recipe 注册表，定义 Recipe ID、协议版本、Java/Native 组成和整体验证状态，禁止按独立策略槽生成未经测试的组合。
- [x] 4.3 实现至少两个完整 Recipe 的生成模板，并确保每个 Recipe 保持相同的启动依赖、配置结果和失败语义。
- [x] 4.4 实现显式 Recipe 选择和未指定时的最低跨应用相似度选择；没有候选满足门槛时严格失败。
- [x] 4.5 生成 Profile 专属 Java 包、代理类、JNI Bridge、辅助类、方法/字段名、构建常量和最小 R8 keep 规则。
- [x] 4.6 生成 Profile 专属 C++ 配置头、内部 namespace、辅助符号、JNI 注册表、可变 section、CMake 参数和编译单元清单。
- [x] 4.7 定义 Java/C++ Overlay 扩展接口和允许目录，只允许启动协调、SO 处理、路径/配置适配及 Profile 私有辅助代码。
- [x] 4.8 实现 Overlay 接口白名单、生成文件冲突、禁用 API/符号、依赖、Manifest 权限/组件、网络及远程动态代码扫描，并把 Overlay SHA-256 写入 Revision。
- [x] 4.9 增加相同身份稳定生成、不同 Profile 标识隔离、Recipe 拒绝、合法 Overlay 和各类越界 Overlay 的生成器测试。

## 5. Java 壳 Recipe 与 Demo 标识清理

- [x] 5.1 把 Android 5～8 代理 Application 和 Android 9+ AppComponentFactory 的固定生命周期依赖抽取为不可变启动契约。
- [x] 5.2 为两个完整 Recipe 实现不同的 Java 启动协调类图，保持 SO 加载、DEX 恢复、ClassLoader 替换和原 Application 恢复顺序一致。
- [x] 5.3 为两个完整 Recipe 实现不同的 SO 定位、解压和加载组织，保持 Zip Entry、CRC、覆盖、`0444` 权限和 `System.load` 语义一致。
- [x] 5.4 让代理 Application、代理 AppComponentFactory、JNI Bridge 和辅助类使用生成的 Profile 专属名称，并移除固定日志标签和示例文案。
- [x] 5.5 从发布壳移除 Demo `MainActivity`、Demo Application、Demo AppComponentFactory、Launcher Manifest、示例资产、布局和资源，测试代码改用专用测试 APK。
- [x] 5.6 为每个完整 Recipe 增加 Java 契约测试，覆盖首次启动、重复初始化、SO 已解压、CRC 变化、解压失败和原组件委托路径。

## 6. Native 壳 Recipe 与固定协议保护

- [x] 6.1 将 DEX/CodeItem 格式、密钥派生、配置解密、ART Hook、风险检查和组件恢复保留在不可变 Native 核心，并建立防止 Overlay 覆盖的边界。
- [x] 6.2 改造 `JNI_OnLoad` 及相关入口，从生成契约读取 Profile 配置和注册表，同时固定“读取配置 -> 风险检查 -> 注册 JNI”的顺序。
- [x] 6.3 为两个完整 Recipe 实现不同的 Native 启动协调、辅助函数拆分和编译单元组织，不改变初始化结果和失败语义。
- [x] 6.4 为两个完整 Recipe 实现不同的路径/配置适配类型和字段映射，保证最终资产路径、缓存路径和配置值一致。
- [x] 6.5 让 SO 名、RC4 key slot、内部 namespace、辅助函数和可变 section 使用 Profile 专属配置，并继续隐藏非必要导出符号。
- [x] 6.6 增加 arm64 Native 契约测试或可验证检查，覆盖配置解密、JNI 注册、路径解析、固定初始化顺序、禁用符号和 Overlay 边界。

## 7. arm64 隔离构建与不可变 Bundle

- [x] 7.1 改造 Gradle/CMake Profile 构建参数，使首版候选只构建 `arm64-v8a` 壳 SO，并使用独立生成源码、缓存和输出目录。
- [x] 7.2 以身份锁、Recipe、Overlay、生成器、不可变核心和工具链版本建立候选缓存键，只复用输入完全一致且完整的结果。
- [x] 7.3 组装候选 Bundle，包含 `revision.json`、`profile.json`、`artifact-sha256.json`、`build-key`、壳 `classes.dex` 和唯一 arm64 壳 SO。
- [x] 7.4 实现 Bundle 本地校验，检查 Profile/Revision/协议/生成器版本、Manifest 入口、JNI 契约、arm64 完整性、文件唯一性和 SHA-256。
- [x] 7.5 使用 Profile 级文件锁、临时候选目录和原子移动发布不可变 Revision，构建或发布失败不得留下半成品。
- [x] 7.6 实现 `active.json` 的校验后原子替换，并验证失败发布和并发发布均不改变原活动 Revision。
- [x] 7.7 实现历史 Revision 完整性校验和无重建回滚，拒绝失败、未验证、缺失或哈希损坏的 Revision。
- [x] 7.8 增加两个 Profile 并发构建、同一 Profile 并发发布、失败恢复、活动指针原子性、只读 Revision 和 arm64 缺失测试。

## 8. APK 加固链路接入

- [x] 8.1 在第一次业务内容写入前解析 APK Manifest 的 `applicationId` 和业务 Native ABI，通过注册表和活动指针精确解析 Revision。
- [x] 8.2 将活动 Bundle 封装为单次加固只读上下文，替换 APK 路径中对全局 `shell-files`、固定壳 DEX、固定 SO 和固定资产名的读取。
- [x] 8.3 使用 Profile 契约写入代理 Application/AppComponentFactory，并驱动业务 DEX 注入、壳 DEX 合并、SO/资产复制和运行配置。
- [x] 8.4 保持 `shellDex + compressedDexZip + 4 字节长度`、MultiDex CodeItem、APK 随机密钥和 `applicationId + "_" + buildKey` 派生逻辑不变。
- [x] 8.5 将垃圾 DEX、重写壳 DEX、写入 APK 随机密钥后的 SO 和其他可变文件迁移到单次 APK 随机工作目录，禁止修改 Bundle。
- [x] 8.6 实现 arm64 输入校验：含业务 Native 库但缺少 arm64 时在内容写入前失败；无业务 Native 库时允许生成仅保证 arm64 的 APK。
- [x] 8.7 为未注册、无活动 Revision、目标不匹配、协议不兼容、arm64 缺失和哈希失败提供稳定错误码，并彻底移除 APK Profile 路径的共享壳回退。
- [x] 8.8 增加严格失败、只读 Bundle、相同/不同 Profile 并发加固和失败清理测试，验证注册表、身份锁、Revision 和活动指针不被修改。

## 9. 静态标识与结构差异化硬门槛

- [x] 9.1 建立版本化旧标识清单和壳产物扫描器，覆盖 Demo 类/文案、原始包名、固定代理/JNI 名、资产/目录名、RC4 符号和固定 JunkClass。
- [x] 9.2 将扫描范围限定为 Profile 壳 DEX、壳 SO 和注入资产，并增加业务 APK 同名内容不触发误报的测试。
- [x] 9.3 实现不同 Profile 的包/类/JNI/SO/资产/目录/`build-key` 唯一性及壳 DEX、arm64 SO SHA-256 差异检查。
- [x] 9.4 实现 Java 可变层规范化结构特征提取，移除名称、字符串和调试信息，并计算精确 Jaccard 相似度。
- [x] 9.5 实现 Native 可变层函数、基本块和指令特征提取，移除符号、地址、重定位和调试信息，并计算精确 Jaccard 相似度。
- [x] 9.6 比较候选与所有其他 `applicationId` 曾激活 Revision，强制 Java `<= 0.80`、Native `<= 0.85`；同一 Profile 历史 Revision 只报告不阻断。
- [x] 9.7 生成包含比较对象、特征范围、实际分数、完整产物报告和失败原因的差异化报告，并把摘要写入 Revision。
- [x] 9.8 增加纯重命名无法通过、超过阈值阻断、历史 Revision 纳入比较、同应用升级不阻断和无候选 Recipe 的测试。

## 10. 包体、权限组件和性能硬门槛

- [x] 10.1 定义版本化基线报告格式，绑定输入 APK、旧 DPT 产物、签名方式、`-S`、arm64、工具链、设备/CI 环境及文件 SHA-256。
- [x] 10.2 实现 APK 包体双门槛：候选不超过旧加固产物 `2 MiB`，且相对原 APK 的增幅不超过旧基线 `1` 个百分点。
- [x] 10.3 实现 arm64 壳 SO `<=` 旧壳 SO `110%` 的门槛，并输出基线、阈值和实际字节数。
- [x] 10.4 实现旧 DPT 与候选 APK 的 Manifest、依赖和静态行为对比，拒绝新增权限、导出组件、网络能力或远程动态代码行为。
- [x] 10.5 建立固定环境冷启动基准，至少执行 30 次并强制 P50 `<= +5%`、P95 `<= +10%`、崩溃和 ANR 为零。
- [x] 10.6 建立已有 Profile 的 10 次 APK 加固基准，强制 P50 `<= +10%`、P95 `<= +15%`。
- [x] 10.7 建立首次 arm64 Profile 构建基准，强制固定 CI 上耗时不超过旧 arm64 壳构建基线 `25%`。
- [x] 10.8 增加报告缺失、环境不匹配、样本不足、SHA 不匹配和各阈值超限的失败测试，所有情况均不得激活 Revision。

## 11. APK 行为矩阵与扫描发布门禁

- [x] 11.1 准备不声明自定义 Application、只声明 Application、只声明 AppComponentFactory、二者同时声明的四类专用测试 APK。
- [x] 11.2 为每个完整 Recipe 和合法 Overlay Revision 执行 APK 加固、安装和启动，验证原 Application、Activity、Service、Receiver、Provider 和 ClassLoader 行为。
- [x] 11.3 在 Android 5～8 arm64 环境验证代理 Application 链，在 Android 9、Android 10 和当前 targetSdk 对应环境验证 AppComponentFactory/ClassLoader 链。
- [x] 11.4 实现手工多引擎扫描报告解析，强制报告与候选 APK SHA-256、扫描器集合和时间窗口匹配，工具不得自动上传 APK。
- [x] 11.5 强制检测数不高于同窗口旧 DPT 基线，并在新增 Trojan、Banker、Spyware 或 RAT 家族标签时阻止发布。
- [x] 11.6 实现统一发布门禁汇总器，只有契约、arm64、静态标识、结构差异、包体、权限组件、性能、行为和扫描全部通过才能激活。
- [x] 11.7 增加任一门禁失败均保持旧活动 Revision、报告不可比时失败关闭以及完整成功发布的端到端测试。

## 12. 迁移、交付兼容与中文文档

- [x] 12.1 在切换 APK 入口前采集并固化旧 DPT 的包体、arm64 SO、启动、加固、构建、权限组件和扫描基线。
- [x] 12.2 为测试 `applicationId` 创建两个独立 Profile，验证显式创建、首个 Revision 发布、升级失败保护、成功升级和历史回滚。
- [x] 12.3 验证 Profile APK 输出仍可交给既有 zipalign、签名和验签流程，且 Profile 创建和加固不需要渠道输入。
- [x] 12.4 编写简体中文使用文档，说明五个 Profile 入口、目录与备份、Recipe/Overlay 边界、arm64 限制、严格错误、门禁报告、迁移和回滚。
- [x] 12.5 在文档中明确本变更只修改 `dpt-shell`、只保证 APK、不会自动上传扫描样本、不承诺完全不可关联且不修改消费端 `tools` 脚本。
- [ ] 12.6 执行全部单元、集成、并发、结构、性能和 APK 行为验证，确认所有 Spec Scenario 有对应证据后再标记变更完成。
