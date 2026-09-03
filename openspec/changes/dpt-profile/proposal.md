## Why

当前 Profile 的可消费产物是依赖注册表、活动指针和多级目录的 Bundle。它在本地可以工作，但复制到另一台服务器时容易遗漏状态文件，也无法仅凭一个文件判断目标应用、版本和壳文件是否完整。方案 B 将已经验证的壳封装为单文件 `.dptprofile`，让本地和服务器只需传递一个明确版本的产物。

## What Changes

- 新增版本化、可移植的 `.dptprofile` 单文件格式，包含 Profile 清单、契约、Revision 快照、壳 DEX、`arm64-v8a` SO、`build-key` 和 SHA-256 校验清单。
- 将候选和正式产物明确分离：候选文件只能验证和 promote，正式文件只能由通过门禁的候选生成；正式加固拒绝候选文件。
- 增加确定性打包、解包、完整性校验和路径安全检查；拒绝篡改、路径穿越、重复条目、符号链接和不支持的版本。
- 增加 `validate`、`promote` 等单文件管理入口，并保留现有 Profile 创建/构建流程作为产物生成来源。
- 为 `dpt` 增加显式 `--profile <file.dptprofile>` 消费入口。加固时按 APK Manifest 的 `applicationId`、ABI、协议和文件哈希严格校验，不自动创建或重建 Profile。
- 将解包后的 Profile 放入本次 APK 的临时工作目录，活动/正式 `.dptprofile` 本身保持只读，签名、zipalign 和渠道包继续由外部交付流程负责。
- 保留现有目录型 Bundle 作为迁移期内部输入和回归基线，但不再把它作为方案 B 的服务器交付接口。

## Capabilities

### New Capabilities

- `per-app-shell-profile`: 为每个 `applicationId` 生成、验证、promote 和显式消费可移植的单文件壳 Profile。

### Modified Capabilities

- 无。当前仓库没有已归档的主规格；本变更创建新的能力规格。

## Impact

- `dpt` 模块：新增 Archive Writer/Reader、清单编解码、严格校验和 `--profile` 参数，并接入 `Apk` 的 Profile 解析。
- 现有 Profile 存储与 Gradle 入口：增加从候选目录导出 `.dptprofile`、从候选 promote 正式文件的入口；原有注册表和 Revision 状态仍用于生成与审计。
- 测试：覆盖确定性、篡改/路径安全、候选隔离、跨目录消费、`applicationId`/ABI/版本不匹配和旧链路回归。
- 范围限制：只修改 `/Users/admin/AndroidStudioProjects/dpt-shell`，只保证 APK 和 `arm64-v8a`；不处理 AAB，不把渠道包纳入 Profile 生命周期，不修改消费端 `tools` 脚本，不上传扫描样本。
- 业务边界：允许受限 Java/C++ Overlay，但不得改变固定 DEX、JNI 语义/签名、密钥、ClassLoader 和启动协议；本变更降低壳层静态重复度，不承诺完全不可关联或规避安全产品检测。
