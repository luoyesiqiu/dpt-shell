# Profile 跨层契约现状映射

本文档记录 Profile 跨层契约的字段归属：哪些字段构成稳定身份、哪些随 Revision 变化、哪些必须按单次 APK 生成。本文只描述 APK 链路，AAB 不在范围内。

## 当前调用链

```text
Dpt.main
  -> Dpt.parseOptions
  -> Apk.protect
  -> Apk.process
      -> 解包并读取 Manifest applicationId
      -> AndroidPackage.resolveDefaultShellPackageName
      -> 保存原 Application / AppComponentFactory
      -> 写入代理 Application / AppComponentFactory
      -> AndroidPackage.extractDexCode
          -> ShellConfig.getJniClassNameSig
          -> DexUtils.injectInvokeMethod
          -> ReflectionClinitInjector.inject
      -> AndroidPackage.combineDexZipWithShellDex
      -> AndroidPackage.copyNativeLibs
      -> AndroidPackage.encryptSoFiles
          -> 按 RC4 key slot 写入单次 APK 随机密钥
      -> AndroidPackage.writeConfig
          -> applicationId + "_" + buildKey
      -> 构建 APK
```

运行时链路：

```text
Android 5～8
  ProxyApplication.attachBaseContext
    -> 解压 SO -> System.load -> JNI_OnLoad
    -> INIT_APP -> COMBINE_DEX_ELEMENTS -> READ_APPLICATION_NAME
  ProxyApplication.onCreate
    -> REPLACE_APPLICATION -> CALL_REAL_APPLICATION_ON_CREATE

Android 9+
  ProxyComponentFactory.instantiateClassLoader / instantiateApplication
    -> 解压 SO -> System.load -> JNI_OnLoad
    -> INIT_APP -> 读取原组件信息 -> COMBINE_DEX_ELEMENTS
    -> 委托原 AppComponentFactory 创建 Application 和组件

JNI_OnLoad
  -> read_shell_config
  -> antiRisk
  -> registerNativeMethods
```

`JNI_OnLoad` 的三步顺序、两条 Android 启动链的依赖顺序以及原组件委托语义均属于不可变协议，不得由 Recipe 或 Overlay 改写。

## 当前状态源和数据源

| 层级 | 当前状态源 | 当前消费者 | 结论 |
|---|---|---|---|
| 根构建 | `build/dpt-build-ids.properties`、根工程 `ext.soName/ext.dptBuildKey` | `dpt` JAR Manifest、`shell/build.gradle`、CMake | 显式传 `--profile` 时以归档内契约为准；未传时回退这组全局值 |
| 壳构建 | `BuildConfig.SO_NAME`、`SO_NAME`、`DPT_BUILD_KEY` CMake 宏 | Java `Global`、Native 配置解密 | 值应来自 Profile；宏名和用途保持固定 |
| 打包进程 | `ShellConfig` 单例（优先读 `profileContract`） | Manifest、DEX 注入、配置 JSON、签名/风险选项 | 单次 APK 通过 `ApkProfileProtectionContext` 注入只读契约；`ShellConfig` 只在无 Profile 时提供旧值 |
| Profile 身份 | `identity.lock.json` | 打包器、Java 声明、Native JNI 注册表 | 已接入：代理入口、JNI Bridge 名、SO/资产名和 build-key 均由契约提供 |
| 单次 APK | Manifest、命令行选项、随机 `encKey`、业务 DEX 签名 | 配置加密、SO key slot、风险/签名校验 | 必须继续按 APK 单次生成，不能固化进 Profile |
| APK 运行时 | 加密配置资产 -> Native `g_shell_config` | JNI 注册、原组件恢复、风险检查 | JSON key 和解析语义固定，字段值按 APK/Profile 注入 |

## 不可变协议字段

以下字段或行为是兼容性协议，不参与跨应用差异化：

| 协议项 | 当前定义位置 | 不可变要求 |
|---|---|---|
| 壳 DEX 组合格式 | `AndroidPackage.combineDexZipWithShellDex` | 保持 `shellDex + compressedDexZip + 4 字节长度` |
| MultiDex CodeItem 版本与布局 | `Const.MULTI_DEX_CODE_VERSION`、`MultiDexCode` | 版本、索引和 CodeItem 编解码语义保持一致 |
| 配置密钥派生 | `AndroidPackage.deriveConfigAesKey`、Native `read_shell_config` | 保持 APK 随机 key 与 `applicationId + "_" + buildKey` 的 HMAC-SHA256 语义一致 |
| 配置 JSON key | `ShellConfig.toJson`、Native `read_shell_config` | 固定为 `app_name`、`acf_name`、`jni_cls_name`、`app_sign_sha256`、`dex_sign`、`risk_check_flags` |
| JNI 语义集合 | `ProfileIdentityStore.JNI_SPECS` | 固定 10 个语义 ID，不允许 Recipe 增删或改变含义 |
| JNI JVM 签名 | `ProfileIdentityStore.JNI_SPECS`、Native `gMethods` | 每个语义 ID 的签名固定，只有方法名可按 Profile 变化 |
| Native 初始化顺序 | `JNI_OnLoad` | 固定为“读取配置 -> 风险检查 -> 注册 JNI” |
| 生命周期依赖 | `ProxyApplication`、`ProxyComponentFactory` | 保持 SO 加载、DEX 恢复、ClassLoader 替换和原组件恢复的依赖顺序 |
| 原应用行为 | Java 代理和 Native 反射恢复代码 | 原 Application、AppComponentFactory、Activity、Service、Receiver、Provider 的最终委托行为不变 |
| APK 随机性 | `KeyUtils.generateKey` 与 SO key slot 写入 | 每次 APK 加固继续生成独立密钥，不复用 Profile 固定密钥 |
| 首版 ABI | Profile 源配置和发布约束 | 只要求并发布 `arm64-v8a`，不隐式混入其他 ABI |

固定 JNI 语义映射如下。左侧旧方法名只是当前实现，不属于固定标识；语义 ID 和 JVM 签名才是协议：

| 语义 ID | 当前方法名 | 固定 JVM 签名 |
|---|---|---|
| `CALL_REAL_APPLICATION_ON_CREATE` | `craoc` | `(Ljava/lang/String;)V` |
| `INIT_APP` | `ia` | `()V` |
| `GET_SOURCE_DIR` | `gap` | `()Ljava/lang/String;` |
| `GET_COMPRESSED_DEXES_PATH` | `gdp` | `()Ljava/lang/String;` |
| `READ_APP_COMPONENT_FACTORY` | `rcf` | `()Ljava/lang/String;` |
| `READ_APPLICATION_NAME` | `rapn` | `()Ljava/lang/String;` |
| `COMBINE_DEX_ELEMENTS` | `cbde` | `(Ljava/lang/ClassLoader;)V` |
| `REMOVE_DEX_ELEMENTS` | `rde` | `(Ljava/lang/ClassLoader;Ljava/lang/String;)V` |
| `REPLACE_APPLICATION` | `ra` | `(Ljava/lang/String;)Ljava/lang/Object;` |
| `STATIC_INITIALIZE` | `clinit` | `()V` |

## Profile 稳定可变字段

以下字段由 `identity.lock.json` 首次生成并在同一 Profile 的 Revision 间保持稳定。不同 `applicationId` 必须使用不同值：

| Profile 字段 | 旧固定来源或标识 | 必须同步的消费者 |
|---|---|---|
| `shellPackageName` | `applicationId + ".shell"` / `com.luoyesiqiu.shell` | 生成 Java 包、DEX 类描述符 |
| `proxyApplicationClassName` | `ProxyApplication` | Manifest、生成的代理类、契约校验器 |
| `proxyComponentFactoryClassName` | `ProxyComponentFactory` | Manifest、生成的代理类、契约校验器 |
| `jniBridgeClassName` | `JniBridge` | Java 声明、Native `FindClass`、DEX 静态初始化注入 |
| `jniMethods[].methodName` | `craoc/ia/.../clinit` | Java native 声明、Native 注册表、打包器注入配置 |
| `soName` | 根构建随机 `SO_NAME` / `BuildConfig.SO_NAME` | CMake target、Java SO 定位、Bundle 与 APK 资产 |
| `shellConfigAssetName` | `d_shell_data_001` | 打包器写入路径、Native ZIP 读取路径 |
| `dexAssetName` | `i11111i111.zip` | 打包器压缩路径、Native DEX 恢复路径 |
| `codeItemAssetName` | `OoooooOooo` | 打包器 CodeItem 输出、Native ZIP 读取路径 |
| `nativeLibAssetDirName` | `vwwwwwvwww` | 打包器 SO 资产目录、Java 解压入口 |
| `runtimeDirName` | `dpt-libs` / `code_cache` | Java 解压目录和 Native 运行路径适配器 |
| `junkClassName` | 固定 `com/luoye/dpt/junkcode/JunkClass` | 垃圾 DEX 生成及静态标识扫描 |
| `rc4KeySymbol` | `DPT_UNKNOWN_DATA` | C++ section 符号、打包器 ELF key slot 查找 |
| `buildKey` | 全局 `dptBuildKey` / `shell-files/build-key` | DPT 配置加密、Native 配置解密、Bundle 文件 |

Recipe 还可以改变 Java/Native 内部辅助类型、方法、namespace、可变 section 和编译单元组织，但这些字段属于 Revision 生成结果，不得反向修改上述稳定身份。

## Revision 字段

以下内容允许同一应用通过显式新 Revision 升级：

- `recipeId` 和完整 Recipe 版本。
- Overlay 内容及 SHA-256。
- 生成器版本、不可变核心版本和工具链版本。
- 生成源码摘要和产物摘要（可选的发布报告摘要不影响壳身份）。
- Java/Native 内部组织和辅助符号；前提是跨层契约、固定生命周期与失败语义不变。

## 单次 APK 字段

以下内容来自输入 APK 或本次命令，不得写入稳定 Profile 身份：

- 输入 `applicationId`、原 Application、原 AppComponentFactory。
- 输入 APK 签名摘要、业务 DEX 签名和风险检查开关。
- 单次随机 `encKey`、由其派生的配置 AES key、CodeItem RC4 key 和写入 SO key slot 的 16 字节值。
- 输入/输出路径、临时工作目录、签名和 zipalign 参数。

## 契约接入现状

显式传入 `--profile` 时，壳身份的唯一来源是归档内的 Profile 契约：代理入口类名、JNI Bridge 名、
SO 名、资产目录名、build-key 和壳 SO/DEX 全部由契约驱动，不再读取全局 `shell-files` 或全局
SO 名/build-key（见 `Apk.getShellNativeLibsRoot`、`ShellConfig.getJniSlashClassName`、
`AndroidPackage.getProxyApplicationName`）。

未传 `--profile` 时仍保留注册表 + 活动 Revision 的目录 Bundle 旧路径，仅用于迁移和回归；
新增调用一律显式传 `--profile`。日常操作见 [dpt-profile.zh-CN.md](./dpt-profile.zh-CN.md)。
