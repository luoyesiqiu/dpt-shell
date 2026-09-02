## 变更原因

当前所有应用共用一套壳工程结构和发布产物，固定类名、JNI 映射、资产名、SO 名及 Java/Native 代码组织容易成为跨应用的静态关联信号。仅修改包名或文件名无法证明壳代码重复度已经下降，也无法让同一应用安全地升级和回滚自己的壳实现。

需要引入按 `applicationId` 显式创建、独立构建和版本化发布的壳 Profile：不同应用使用不同的壳代码与产物，同一应用稳定复用一个 Profile 并通过不可变 Revision 升级；APK 加固协议和运行时行为保持兼容。

## 变更内容

- 新增 `applicationId -> profileId` 一对一注册表，以及 Profile 创建、查看、构建、发布和回滚入口；Profile 必须在 APK 加固前显式创建，加固过程不得自动创建。
- **BREAKING**：APK 加固必须存在与输入 `applicationId` 精确匹配、具有活动 Revision 且完整性校验通过的 Profile；缺少或损坏时严格失败，不再回退共享默认壳。
- 将 Profile 身份与发布产物分离：Profile ID、稳定标识、JNI 映射和 `build-key` 保持稳定；每次显式升级生成不可变 Revision，全部门槛通过后原子切换活动版本，并允许回滚到曾验证通过的旧 Revision。
- 使用经过整体测试的 Java/Native Recipe 生成 Profile 专属源码，不开放未经验证的策略笛卡尔积；首版允许开发者在安全扩展槽内提供受约束的手工 Java/C++ Overlay。
- 让打包器、Java 壳、Native 壳和构建配置共同读取同一份 Profile 契约，生成独立的包名、代理类、JNI 方法、SO 名、资产名、运行目录和源码组织。
- 将已知旧标识清零、DEX/SO 唯一性、Java/Native 结构相似度、包体、冷启动、构建耗时和多引擎扫描结果设置为 Revision 激活硬门槛。
- 首版 Profile Bundle 以 `arm64-v8a` 为必需和验收 ABI；其他 ABI、AAB、渠道包以及消费端脚本适配不纳入本变更。
- 保持 DEX/CodeItem 格式、密钥派生、JNI 注册时序、Android 5～8 与 Android 9+ 启动链、原 Application/组件行为和 APK 级随机密钥语义不变。
- 移除发布壳中的 Demo Activity、Demo Application、Demo AppComponentFactory、示例资源和固定公开标识，降低壳层静态重复度。

## 能力范围

### 新增能力

- `per-app-shell-profile`：管理按 `applicationId` 绑定的壳 Profile、不可变 Revision、受测 Recipe、受限 Overlay、发布硬门槛和 APK 加固时的严格 Profile 选择。

### 修改能力

无。当前仓库没有已存在的 OpenSpec 能力规格；本变更新增能力，不修改既有规格要求。

## 影响范围

- `dpt` 模块：增加 Profile 管理入口、注册表与 Revision 解析、只读 Bundle 校验，并让 Manifest、DEX 注入、SO/资产复制和配置写入使用 Profile 契约。
- `shell` 模块：拆分不可变协议核心与可变扩展槽，按 Recipe 和 Overlay 生成 Profile 专属 Java/Native 壳，同时保留现有两条 Android 启动链。
- Gradle/CMake：增加 arm64 Profile 隔离构建、候选 Revision、验证报告、原子激活和历史 Revision 保留，不再为 APK Profile 链路覆盖全局 `shell-files`。
- 验证基础设施：增加契约、静态标识、结构相似度、包体、性能、误报、并发和 APK 安装启动门槛；任一硬门槛失败均不得激活 Revision。
- 迁移：切换新版 APK 加固入口前，必须为每个待加固 `applicationId` 预先创建 Profile 并激活首个 Revision。
- 范围限制：只修改 `/Users/admin/AndroidStudioProjects/dpt-shell`；不修改当前项目的签名、zipalign、验签、渠道包或 `tools` 脚本，不实现或验收 AAB Profile。
- 能力边界：本变更只降低壳层公开静态重复度，不承诺消除证书、业务代码、第三方库、网络和运行行为产生的全部关联信号，也不以规避安全产品检测为目标。
