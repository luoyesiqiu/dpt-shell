# 旧 DPT 基线证据

本目录对应测试 `applicationId` `com.example.dptfixture.both`，用于 `dpt-profile` 发布门禁的可比基线。目录只保存小型 JSON 和说明文件，不保存 APK、SO 或旧源码快照；大文件仍位于被忽略的 `build/dpt-profile-baseline/`。

## 已固化证据

- `baseline-report.json`：输入 APK、旧 DPT 加固 APK、旧 arm64 壳 SO、签名方式、`-S`、工具链、设备和 CI 环境，以及 SHA-256。
- `performance.json`：10 次旧 DPT 加固、30 次冷启动和旧 arm64 壳构建样本。冷启动样本使用本轮临时 `Pixel 6 / API 32 / arm64-v8a` AVD；该 AVD 已停止并删除。
- `behavior-snapshot.json`：旧 DPT 加固 APK 的权限、导出组件、依赖、网络能力和动态代码能力快照。
- `manual-scan-request.json`：多引擎手工扫描的待办和字段模板，目前不是可通过的扫描报告。

## 代表制品

- 输入 APK：`com.example.dptfixture.both`，SHA-256 `6f79ab7d0ec02a0bb4c0485a5090cce3d4bd3a1888443a0bb73ef15b9f9382b6`，13,289 字节。
- 旧 DPT 签名 APK：`legacy-fixture-signed.apk`，SHA-256 `da7a8f29bb3f734e2d3c03338c4f43b8dc2c1676235f35270a4eccab1fbfe603`，307,930 字节；`apksigner verify --verbose` 的 v1/v2/v3 均通过。
- 旧 arm64 壳 SO：`lib2a80863609dc72ed.so`，SHA-256 `fe613b21de3f57aaa9f33af112be81dff1340e470f5913fe49af267febcb7f27`，719,152 字节。

## 尚未完成

旧 DPT 的多引擎扫描结果必须由人工对 `legacy-fixture-signed.apk` 执行并回填。工具不会自动上传样本，也不能用占位检测数替代真实报告；在此之前，生产 `profilePublish` 会严格失败。
