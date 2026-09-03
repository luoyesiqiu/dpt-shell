## Context

现有实现已经能按 `applicationId` 生成独立身份和 Revision，但编译结果落在目录型 Bundle：打包器还要读取 Profile 注册表、活动指针和两个根目录。方案 B 只改变产物封装和消费边界，不改变既有壳运行协议、门禁规则或 Profile 身份语义。

## Goals / Non-Goals

**Goals:**

- 让一个 `.dptprofile` 文件包含完成一次 APK 加固所需的全部壳输入，并可在没有源码注册表的干净目录中验证和消费。
- 让候选/正式状态、目标 `applicationId`、Profile/Revision、协议/工具链版本和 `arm64-v8a` ABI 可被机器严格判断。
- 保持现有目录型生成、门禁和历史测试可回归，迁移时不轮换 Profile 稳定身份。
- 让 `dpt.jar` 通过显式参数读取文件，解包到本次任务临时目录，失败发生在任何 APK 内容写入之前。

**Non-Goals:**

- 不重写 DEX/CodeItem、密钥派生、Native 注册、ART Hook 或 Android 版本启动链。
- 不把签名密钥、密码、渠道配置或扫描 APK 放进 Profile。
- 不支持 AAB、多 ABI 扩展、远程下载 Profile 或自动上传扫描报告。
- 不保证删除旧目录型 API；目录型 API 只作为迁移期内部来源。

## Decisions

### 1. 单文件格式

`.dptprofile` 使用 ZIP 容器，条目按 UTF-8 Unix 路径排序并使用固定时间戳。必需条目为：

```text
manifest.json
profile.json
revision.json
artifact-sha256.json
checksums.sha256
shell-files/build-key
shell-files/dex/classes.dex
shell-files/libs/arm64-v8a/<profile-so>.so
```

`manifest.json` 是外层清单，声明 `formatVersion`、`status`、`applicationId`、`profileId`、`revisionId`、ABI、协议、核心/生成器/工具链版本和报告摘要。`profile.json` 继续使用现有规范化契约；`checksums.sha256` 覆盖除自身以外的全部条目，避免只校验元数据而漏掉壳文件。正式文件可附带 `reports/` 下的门禁/扫描报告，报告也纳入校验清单。

选择 ZIP 而不是目录或 TAR 的原因是 Java/CI/服务器环境普遍可读，单文件复制和归档简单；固定条目和时间戳保证相同输入生成相同字节序列，便于缓存和审计。

### 2. 安全读取与解包

Reader 先读取并验证清单，再将条目解包到调用方提供的临时目录。每个条目必须是普通文件或显式目录，路径不得为绝对路径、包含 `..`、反斜杠或重复规范化结果；目标路径必须保持在临时根目录内。限制条目总数、单文件大小和总解压大小，拒绝符号链接标记及非常规 ZIP 条目。解包完成后重新计算 `checksums.sha256` 和 `artifact-sha256.json`，然后复用现有契约、DEX 和 ELF 校验。

Reader 返回只读 `ProfileArchiveBundle` 视图；它不写入注册表、活动指针或源 Profile。调用方在 APK 任务结束时删除临时解包目录。

### 3. 候选到正式的生命周期

```text
目录候选 Bundle
       │ pack(status=candidate)
       ▼
*.candidate.dptprofile ── validate ──┐
       │                             │ 门禁/扫描报告
       └──────────── promote ────────┘
                       ▼
                 *.dptprofile (verified)
                       │
                 dpt --profile 消费
```

`pack` 不改变源目录和 Revision 状态；`promote` 只接受 `candidate`，要求报告与 Profile/Revision 及候选 APK 摘要匹配，再原子写出正式文件。输出文件已存在、候选状态错误、报告缺失或任一哈希/门禁不通过时严格失败，不覆盖旧文件。回滚通过选择上一份已验证的正式文件完成，不修改文件内容。

### 4. 显式消费与兼容策略

`Apk.Builder` 增加可选 `profileArchive`，`Dpt` 增加 `--profile`/`--profile-file` 长参数。指定该参数时，解析器完全以该文件为源，不查询或修改注册表，也不回退全局 `shell-files`；`applicationId` 和业务 ABI 从解包 APK 的 Manifest/`lib` 目录读取并逐项匹配。未指定参数的程序化旧入口继续走现有目录型解析，以保证迁移期回归；服务器和文档只使用显式单文件入口。

Archive 解包根放在 `Apk` 本次随机工作目录下，因此重写 DEX、写入 APK 随机密钥后的 SO 和垃圾 DEX 不会污染正式 Profile。已有 `ProfileBundle` API 通过适配层读取规范化的 `arm64` 内部路径，归档对外统一使用 `arm64-v8a`。

### 5. CLI 与 Gradle 接口

保留既有 `profileCreate`、`profileBuild`、`profilePublish`、`profileRollback`，并增加可移植产物命令：

```bash
dpt-profile pack --bundle <candidate-bundle-dir> --out <id>.candidate.dptprofile
dpt-profile validate --profile <id>.dptprofile [--apk <input.apk>]
dpt-profile promote --candidate <id>.candidate.dptprofile --reports <reports-dir> --out <id>.dptprofile
dpt-protect --apk <input.apk> --profile <id>.dptprofile
```

在现有 Java CLI 中以 `pack`、`validate`、`promote` 实现，并保留等价的 `ProfileCommandMain` 调用形式，方便 Gradle 和服务器直接使用。命令输出结构化 `PROFILE_*_RESULT`，错误沿用 `DPT-PROFILE-*` 稳定前缀。

## Risks / Trade-offs

- [旧目录与新归档同时存在] -> 明确服务器交付只接受 `.dptprofile`，并为目录输入保留只读适配和回归测试，后续再单独移除。
- [ZIP 炸弹或路径穿越] -> 使用规范化路径检查、条目/大小上限、拒绝链接和临时目录，任何失败都不触碰 APK。
- [不同 ZIP 实现产生不同字节] -> 固定排序、时间戳、压缩方法和权限字段，并用确定性测试比较两次输出 SHA-256。
- [正式文件被误当候选或反之] -> 清单状态、文件名后缀和 promote/consume 双重校验；消费只接受 `verified`。
- [现有业务脚本仍未传 `--profile`] -> 不修改脚本作为本变更边界，文档给出显式 `dpt.jar -f ... --profile ...` 用法；脚本适配另开变更。
- [报告包含敏感信息] -> Profile 不自动上传；只复制调用方明确提供的报告文件，且不写入签名私钥/密码。

## Migration Plan

1. 在现有 `profileBuild`/`assemble-candidate` 后执行 `pack`，生成 `.candidate.dptprofile`。
2. 用真实门禁和扫描报告执行 `promote`，把正式 `.dptprofile` 作为服务器/本地唯一交付物。
3. 加固时显式传 `--profile`，先在真机完成安装启动验收，再逐步替换调用方脚本。
4. 出现问题时切换到上一份已验证 `.dptprofile`；不删除或改写源注册表和历史 Revision。

## Open Questions

无。ABI、候选门禁、显式消费和脚本范围均已按已确认边界固定。
