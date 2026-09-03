> 执行约束：每个任务开始前先用 CodeGraph（若环境提供 CLI/MCP）核对相关符号的调用方、状态源、数据源和影响面；若 CodeGraph 不可用，记录限制并完成全量符号/调用点静态核对。每个任务完成后立即勾选。只做当前任务的最小闭环，不修改消费端 `tools` 脚本。

## 1. 方案 B 契约与回归基线

- [x] 1.1 更新 proposal、design、spec、tasks，统一 `.dptprofile` 文件、candidate/verified 生命周期、显式 `--profile` 和 arm64/APK 边界，并通过 OpenSpec 状态检查
- [x] 1.2 记录现有目录 Bundle 与 `ApkProfileResolver`、`Dpt`、`Apk` 的调用链基线，运行 `:dpt:test` 作为迁移回归证据

## 2. 单文件归档核心

- [x] 2.1 定义版本化 Archive Manifest、状态和确定性编解码，验证未知字段、版本和状态会严格失败
- [x] 2.2 实现 `.dptprofile` Writer：规范化条目、固定 ZIP 元数据、arm64-v8a 路径映射、原子输出和候选后缀，验证重复打包 SHA-256 一致
- [x] 2.3 实现 `.dptprofile` Reader：路径穿越/链接/重复条目/大小限制检查，安全解包到临时目录并验证双重 SHA-256 清单
- [x] 2.4 复用现有契约、DEX、ELF 和 Recipe 校验，支持从单文件构造只读 `ProfileArchiveBundle`，验证缺失/篡改/版本不兼容均失败

## 3. 候选、正式和管理入口

- [x] 3.1 增加 `pack` 命令，从候选目录生成 `.candidate.dptprofile`，验证不修改源目录且拒绝覆盖输出
- [x] 3.2 增加 `validate` 命令，可在无注册表目录中验证 Profile，并可选核对输入 APK 的 `applicationId`/ABI
- [x] 3.3 增加 `promote` 命令，校验门禁/扫描报告和候选状态后原子生成正式 `.dptprofile`，验证失败保持旧正式文件
- [x] 3.4 保留现有 profileCreate/profileBuild/profilePublish/profileRollback 语义，增加单文件结果输出和稳定中文错误码回归测试

## 4. APK 显式消费

- [x] 4.1 使用 CodeGraph/静态调用点核对 `Dpt.parseOptions -> Apk.Builder -> ApkProfileResolver -> AndroidPackage`，增加 `--profile` 参数和 Builder 透传
- [x] 4.2 让显式 Profile 路径只读单文件并解包到本次随机工作目录，验证不查询注册表、不回退全局 shell-files、任务结束清理临时目录
- [x] 4.3 在 Manifest/DEX/SO 写入前校验 verified 状态、applicationId、协议/核心/生成器版本、哈希和 arm64 ABI，验证候选和不匹配输入严格失败
- [x] 4.4 验证正式 `.dptprofile` 在没有注册表、活动指针和源码的干净目录中完成一次 APK Profile 解析；保留旧目录链路回归

## 5. Gradle、文档和交付验证

- [x] 5.1 增加 Gradle/Java 调用示例和 `profilePack`/`profilePromote` 等价入口（如不增加任务则验证 CLI 可被服务器直接调用）
- [x] 5.2 更新简体中文 MVP/详细文档，说明产物生成、验证、promote、显式消费、服务器复制、回滚、arm64、APK/AAB/渠道边界
- [x] 5.3 补充确定性、篡改、路径安全、候选隔离、干净目录消费和旧行为回归测试，并执行 `:dpt:test`、`git diff --check`
- [x] 5.4 完成真机优先的 APK 静态/安装前检查；如需重复安装才使用临时通用 AVD，并记录设备、ABI、安装次数和清理后的磁盘证据

> 5.4 验证记录（2026-09-03）：输入 APK `app-momogirl-release_7zip_aligned_signed.apk` 的 `aapt2`、`apksigner`、`zipalign` 和 `unzip -t` 检查均返回 0；Manifest 为 `com.tangyu.momogirl`，包含 7 个 DEX 和 108 个 `arm64-v8a` Native 库。真机为 Pixel 6（序列号 `19241FDF60095G`，API 36，ABI `arm64-v8a,armeabi-v7a,armeabi`），安装次数为 0；当前没有合法 `verified .dptprofile`，因此未执行保护 APK 安装。报告目录：`/tmp/dpt-profile-5.4.JSuvw3`；主机磁盘检查为可用 17 GiB（96% 已用）。
