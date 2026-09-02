package com.luoye.dpt.profile.generation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProfileNativeCoreContract {
    public static final String VERSION = "immutable-core-v1";

    private static final List<String> DEX_CORE = paths(
            "dex/CodeItem.cpp", "dex/CodeItem.h",
            "dex/MultiDexCode.cpp", "dex/MultiDexCode.h",
            "dex/dex_file.cpp", "dex/dex_file.h", "dex/modifiers.h");
    private static final List<String> CRYPTO_CORE = paths(
            "dpt.cpp", "dpt.h", "dpt_crypto.cpp", "dpt_crypto.h",
            "rc4/rc4.c", "rc4/rc4.h");
    private static final List<String> HOOK_CORE = paths(
            "dpt_hook.cpp", "dpt_hook.h", "dex/dex_file.cpp", "dex/dex_file.h",
            "dex/modifiers.h");
    private static final List<String> RISK_CORE = paths(
            "dpt.cpp", "dpt.h", "dpt_risk.cpp", "dpt_risk.h");
    private static final List<String> COMPONENT_CORE = paths(
            "dpt.cpp", "dpt.h", "dpt_util.cpp", "dpt_util.h",
            "reflect/dpt_reflect.h",
            "reflect/android_app_ActivityThread.cpp",
            "reflect/android_app_ActivityThread.h",
            "reflect/android_app_Application.cpp",
            "reflect/android_app_Application.h",
            "reflect/android_app_ContextImpl.cpp",
            "reflect/android_app_ContextImpl.h",
            "reflect/android_app_LoadedApk.cpp",
            "reflect/android_app_LoadedApk.h",
            "reflect/android_content_pm_ApplicationInfo.cpp",
            "reflect/android_content_pm_ApplicationInfo.h",
            "reflect/dalvik_system_BaseDexClassLoader.cpp",
            "reflect/dalvik_system_BaseDexClassLoader.h",
            "reflect/dalvik_system_DexPathList.cpp",
            "reflect/dalvik_system_DexPathList.h",
            "reflect/java_io_File.cpp", "reflect/java_io_File.h",
            "reflect/java_lang_Class.cpp", "reflect/java_lang_Class.h",
            "reflect/java_util_ArrayList.cpp", "reflect/java_util_ArrayList.h");

    private static final ProfileNativeCoreContract BUILT_IN =
            new ProfileNativeCoreContract(Arrays.asList(
                    boundary(Capability.DEX_CODE_ITEM_FORMAT, DEX_CORE,
                            exact("CodeItem"), exact("MultiDexCode"), exact("DexFile"),
                            exact("DexFileUtils"), exact("DexHeader"), exact("ClassDef"),
                            exact("ClassDataMethod"), exact("readCodeItem"), exact("dexMap")),
                    boundary(Capability.KEY_DERIVATION, CRYPTO_CORE,
                            exact("DPT_UNKNOWN_DATA"), exact("DPT_BUILD_KEY"),
                            exact("DPT_PROFILE_BUILD_KEY"), exact("hmac_sha256"),
                            exact("rc4_state"), exact("rc4"), exact("rc4_init"),
                            exact("rc4_crypt")),
                    boundary(Capability.CONFIG_DECRYPTION, CRYPTO_CORE,
                            exact("JNI_OnLoad"), exact("JNI_OnUnload"),
                            exact("RegisterNatives"), exact("JNINativeMethod"),
                            exact("DPT_PROFILE_BUILD"),
                            exact("DPT_PROFILE_CONTRACT_ENABLED"),
                            exact("DPT_PROFILE_JNI_BRIDGE"),
                            exact("DPT_PROFILE_JNI_TABLE_ACCESSOR"),
                            exact("DPT_PROFILE_JNI_COUNT_ACCESSOR"),
                            exact("DPT_PROFILE_STARTUP_COORDINATOR"),
                            exact("DPT_PROFILE_ABI"),
                            exact("DPT_PROFILE_SHELL_CONFIG_ASSET"),
                            exact("DPT_PROFILE_RUNTIME_CONFIG_TYPE"),
                            exact("DPT_PROFILE_RUNTIME_CONFIG_ACCESSOR"),
                            exact("DPT_PROFILE_RUNTIME_SHELL_CONFIG_FIELD"),
                            exact("DPT_PROFILE_RUNTIME_CODE_ITEM_FIELD"),
                            exact("DPT_PROFILE_RUNTIME_DEX_ASSET_FIELD"),
                            exact("DPT_PROFILE_RUNTIME_DIRECTORY_FIELD"),
                            exact("DPT_PROFILE_RUNTIME_BUILD_KEY_FIELD"),
                            exact("DPT_PROFILE_JUNK_CLASS"),
                            exact("DPT_PROFILE_SO_NAME"), exact("DPT_PROFILE_RC4_KEY_SYMBOL"),
                            exact("DPT_PROFILE_VARIABLE_SECTION"),
                            exact("DPT_RUNTIME_KEY_SLOT"), exact("getShellSoName"),
                            exact("getShellConfigEntryName"), exact("getCodeItemEntryName"),
                            exact("getDexAssetFileName"), exact("getRuntimeDirectoryName"),
                            exact("getShellBuildKey"),
                            exact("registerNativeMethods"), exact("read_shell_config"),
                            exact("aes_cbc_decrypt"), exact("ShellConfig"),
                            exact("g_shell_config"), exact("decrypt_section"),
                            exact("decrypt_bitcode"), exact("init_dpt")),
                    boundary(Capability.ART_HOOK, HOOK_CORE,
                            exact("dpt_hook"), exact("hook_LoadClass"),
                            exact("hook_DefineClass"), exact("hook_mmap"),
                            exact("hook_execve"), exact("hook_write"),
                            exact("change_dex_protective"), exact("patchMethod"),
                            exact("patchClass"), exact("LoadClassV23"),
                            exact("DefineClassV22"), exact("DefineClassV21"),
                            exact("fake_mmap"), exact("fake_execve"), exact("fake_write"),
                            prefix("Dobby"), prefix("bytehook")),
                    boundary(Capability.RISK_CHECK, RISK_CORE,
                            exact("antiRisk"), exact("detectRisk"),
                            exact("detectRiskOnThread"), exact("detectFrida"),
                            exact("detectDebugger"), exact("verifyLibcTextCrc"),
                            exact("verifyAppSignature"), exact("junkCodeDexProtect"),
                            exact("dpt_crash"), exact("veritySignature")),
                    boundary(Capability.COMPONENT_RESTORATION, COMPONENT_CORE,
                            exact("callRealApplicationOnCreate"), exact("init_app"),
                            exact("readAppComponentFactory"), exact("readApplicationName"),
                            exact("makePathElements"), exact("combineDexElement"),
                            exact("combineDexElements"), exact("removeDexElements"),
                            exact("replaceApplication"),
                            exact("replaceApplicationOnActivityThread"),
                            exact("replaceApplicationOnLoadedApk"),
                            exact("getRealApplicationClass"),
                            exact("getApplicationInstance"),
                            exact("getSourceDirExport"),
                            exact("getCompressedDexesPathExport"), exact("clinit"))));

    private final List<CoreBoundary> boundaries;
    private final Map<Capability, CoreBoundary> byCapability;

    private ProfileNativeCoreContract(List<CoreBoundary> boundaries) {
        this.boundaries = Collections.unmodifiableList(new ArrayList<>(boundaries));
        EnumMap<Capability, CoreBoundary> index = new EnumMap<>(Capability.class);
        for (CoreBoundary boundary : boundaries) {
            if (index.put(boundary.getCapability(), boundary) != null) {
                throw new IllegalArgumentException("Native 核心能力重复："
                        + boundary.getCapability());
            }
        }
        if (index.size() != Capability.values().length) {
            throw new IllegalArgumentException("Native 核心能力不完整");
        }
        byCapability = Collections.unmodifiableMap(index);
    }

    public static ProfileNativeCoreContract builtIn() {
        return BUILT_IN;
    }

    public String getVersion() {
        return VERSION;
    }

    public List<CoreBoundary> getBoundaries() {
        return boundaries;
    }

    public CoreBoundary requireBoundary(Capability capability) {
        CoreBoundary boundary = byCapability.get(capability);
        if (boundary == null) {
            throw new IllegalArgumentException("Native 核心能力未登记：" + capability);
        }
        return boundary;
    }

    private static CoreBoundary boundary(Capability capability, List<String> sourcePaths,
                                         ProtectedSymbol... protectedSymbols) {
        return new CoreBoundary(capability, sourcePaths, Arrays.asList(protectedSymbols));
    }

    private static ProtectedSymbol exact(String name) {
        return new ProtectedSymbol(name, Match.EXACT);
    }

    private static ProtectedSymbol prefix(String name) {
        return new ProtectedSymbol(name, Match.PREFIX);
    }

    private static List<String> paths(String... paths) {
        return Collections.unmodifiableList(Arrays.asList(paths));
    }

    public enum Capability {
        DEX_CODE_ITEM_FORMAT,
        KEY_DERIVATION,
        CONFIG_DECRYPTION,
        ART_HOOK,
        RISK_CHECK,
        COMPONENT_RESTORATION
    }

    public enum Match {
        EXACT,
        PREFIX
    }

    public static final class CoreBoundary {
        private final Capability capability;
        private final List<String> sourcePaths;
        private final List<ProtectedSymbol> protectedSymbols;

        private CoreBoundary(Capability capability, List<String> sourcePaths,
                             List<ProtectedSymbol> protectedSymbols) {
            if (capability == null || sourcePaths.isEmpty() || protectedSymbols.isEmpty()) {
                throw new IllegalArgumentException("Native 核心边界不能为空");
            }
            this.capability = capability;
            this.sourcePaths = immutableDistinct(sourcePaths, "Native 核心源码路径");
            this.protectedSymbols = immutableDistinctSymbols(protectedSymbols);
        }

        public Capability getCapability() {
            return capability;
        }

        public List<String> getSourcePaths() {
            return sourcePaths;
        }

        public List<ProtectedSymbol> getProtectedSymbols() {
            return protectedSymbols;
        }
    }

    public static final class ProtectedSymbol {
        private final String name;
        private final Match match;

        private ProtectedSymbol(String name, Match match) {
            if (name == null || name.isEmpty() || match == null) {
                throw new IllegalArgumentException("Native 核心保护符号不能为空");
            }
            this.name = name;
            this.match = match;
        }

        public String getName() {
            return name;
        }

        public Match getMatch() {
            return match;
        }
    }

    private static List<String> immutableDistinct(List<String> values, String label) {
        Set<String> distinct = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isEmpty() || !distinct.add(value)) {
                throw new IllegalArgumentException(label + "为空或重复：" + value);
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(distinct));
    }

    private static List<ProtectedSymbol> immutableDistinctSymbols(
            List<ProtectedSymbol> protectedSymbols) {
        Map<String, ProtectedSymbol> distinct = new LinkedHashMap<>();
        for (ProtectedSymbol protectedSymbol : protectedSymbols) {
            String key = protectedSymbol.getMatch() + ":" + protectedSymbol.getName();
            if (distinct.put(key, protectedSymbol) != null) {
                throw new IllegalArgumentException("Native 核心保护符号重复：" + key);
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(distinct.values()));
    }
}
