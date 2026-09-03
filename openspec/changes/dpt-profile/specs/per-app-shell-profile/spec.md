## Purpose

为每个 Android `applicationId` 提供可验证、可复制、可在本地或服务器消费的独立壳 Profile 文件，同时保持既有 APK 加固运行协议和严格失败边界。

## ADDED Requirements

### Requirement: Profile 必须以单文件形式交付

系统 MUST 能把一个完整壳 Revision 导出为 `.dptprofile` 文件。文件必须包含外层清单、Profile 契约、Revision 快照、壳 DEX、唯一的 `arm64-v8a` 壳 SO、`build-key` 和 SHA-256 校验清单；不得包含私钥、签名密码或渠道配置。

#### Scenario: 从候选 Bundle 生成候选文件

- **WHEN** 调用方对完整候选 Bundle 执行 pack
- **THEN** 系统原子生成一个后缀为 `.candidate.dptprofile` 的文件，清单记录 `status=candidate`、目标 `applicationId`、Profile/Revision、协议和工具链版本

#### Scenario: 缺少壳文件时拒绝打包

- **WHEN** 候选缺少 classes.dex、`arm64-v8a` SO、build-key 或契约文件
- **THEN** 系统失败且不留下部分归档、不修改候选源和已发布产物

### Requirement: 归档输出必须确定且防篡改

系统 MUST 对归档条目执行规范化路径检查、重复检查、符号链接/非常规文件拒绝和大小限制，并验证 `checksums.sha256` 与 `artifact-sha256.json`。相同输入 MUST 产生相同归档字节序列。

#### Scenario: 两次打包结果一致

- **WHEN** 对相同候选目录执行两次 pack
- **THEN** 两个 `.dptprofile` 的文件 SHA-256 相同，且条目顺序、时间戳和内容一致

#### Scenario: 归档被篡改

- **WHEN** 修改归档中的 profile.json、classes.dex 或 SO 后执行 validate
- **THEN** 系统报告完整性失败，不返回可消费 Profile

#### Scenario: 归档包含越界路径

- **WHEN** ZIP 条目包含绝对路径、`..`、反斜杠、重复条目或链接标记
- **THEN** 系统拒绝解包，临时目录之外不得出现任何文件

### Requirement: 候选和正式 Profile 必须隔离

系统 MUST 区分 `candidate` 与 `verified` 状态。promote 只能把通过完整门禁和报告校验的候选原子写成正式 `.dptprofile`；正式 APK 加固 MUST 拒绝候选文件、失败状态和报告不完整的文件。

#### Scenario: 候选不能直接加固

- **WHEN** `dpt` 使用 `status=candidate` 的 `.dptprofile`
- **THEN** 在写入 APK Manifest、DEX、SO 或配置前返回稳定错误码并保持输入和 Profile 不变

#### Scenario: promote 成功

- **WHEN** 候选文件、门禁报告、扫描报告、目标 APK 摘要和全部 SHA-256 均匹配
- **THEN** 系统原子生成 `status=verified` 的正式 `.dptprofile`，不覆盖已有正式文件

#### Scenario: promote 失败保护

- **WHEN** 报告缺失、候选哈希不匹配、门禁失败或输出文件已存在
- **THEN** promote 失败且既有正式 Profile、源注册表和活动 Revision 保持不变

### Requirement: 显式消费必须绑定 APK 身份和 ABI

系统 MUST 支持通过显式 `--profile` 文件为一次 APK 加固选择 Profile，并在任何 APK 内容写入前核对 Manifest `applicationId`、Profile 清单、契约版本、Revision 和业务 Native ABI。指定文件时不得查询或回退共享 `shell-files`。

#### Scenario: 干净目录消费正式 Profile

- **WHEN** 在不含 Profile 注册表和活动指针的服务器目录中，用匹配的 APK 和正式 `.dptprofile` 执行加固
- **THEN** 系统仅从该文件解包壳输入并继续既有 zipalign/签名交付流程

#### Scenario: applicationId 不匹配

- **WHEN** APK Manifest 的 `applicationId` 与 Profile 清单不一致
- **THEN** 系统返回目标不匹配错误，不写入解包 APK 的 Manifest、DEX、SO 或配置

#### Scenario: 业务 ABI 不兼容

- **WHEN** APK 含业务 Native 库但没有 `arm64-v8a`
- **THEN** 系统在内容写入前返回 ABI 不兼容错误；无业务 Native 库时只承诺 arm64 设备

### Requirement: 归档必须保持既有运行和交付边界

系统 MUST 保持固定 DEX/CodeItem 格式、密钥派生、JNI 语义/签名、ClassLoader 和 Android 启动顺序不变；Profile 文件不得依赖签名、zipalign、验签或渠道输入。活动归档在任务期间 MUST 只读。

#### Scenario: 归档输入经过现有交付流程

- **WHEN** 使用已验证 Profile 生成 APK
- **THEN** 输出仍可交给既有 zipalign、签名、验签和可选渠道包步骤，且未提供渠道输入时不生成渠道包

#### Scenario: Profile 不被 APK 加固修改

- **WHEN** 加固过程写入 APK 随机密钥、重写 DEX 或加密 SO
- **THEN** 所有可变副本位于本次临时工作目录，正式 `.dptprofile` 的字节和 SHA-256 保持不变
