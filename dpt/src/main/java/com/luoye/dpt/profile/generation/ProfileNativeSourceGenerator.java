package com.luoye.dpt.profile.generation;

import com.luoye.dpt.profile.contract.ProfileJniSemantic;
import com.luoye.dpt.profile.generation.recipe.ProfileRecipeTemplate;
import com.luoye.dpt.profile.model.ProfileIdentityLock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class ProfileNativeSourceGenerator {
    private static final Set<String> FIXED_UNITS = new HashSet<>(Arrays.asList(
            "jni-entry", "registration-table"));
    private static final String STARTUP_LINEAR = "startup-linear";
    private static final String STARTUP_PHASES = "startup-phases";
    private static final String STARTUP_CONFIG_RISK_PHASE = "startup-config-risk-phase";
    private static final String STARTUP_REGISTRATION_PHASE = "startup-registration-phase";
    private static final Set<String> STARTUP_UNITS = new HashSet<>(Arrays.asList(
            STARTUP_LINEAR, STARTUP_PHASES, STARTUP_CONFIG_RISK_PHASE,
            STARTUP_REGISTRATION_PHASE));
    private static final String PATH_DIRECT = "path-direct";
    private static final String PATH_CONTEXT = "path-context";
    private static final String CONFIG_ADAPTER = "config-adapter";
    private static final Set<String> PATH_CONFIG_UNITS = new HashSet<>(Arrays.asList(
            PATH_DIRECT, PATH_CONTEXT, CONFIG_ADAPTER));

    void generate(ProfileCodeGenerator.GenerationLayout layout, ProfileIdentityLock identity,
                  ProfileRecipeTemplate template) throws IOException {
        generate(layout, identity, template, Collections.emptyList());
    }

    void generate(ProfileCodeGenerator.GenerationLayout layout, ProfileIdentityLock identity,
                  ProfileRecipeTemplate template, List<String> overlaySourceFileNames)
            throws IOException {
        if (layout == null || identity == null || template == null) {
            throw new IllegalArgumentException("native generation arguments must not be null");
        }
        if (overlaySourceFileNames == null) {
            throw new IllegalArgumentException("overlay source names must not be null");
        }
        NativeNames names = new NativeNames(identity, template);
        List<String> sourceFileNames = new ArrayList<>();
        write(layout.getCppDirectory().resolve("profile_config.h"),
                configHeader(identity, names));
        write(layout.getCppDirectory().resolve("profile_jni_registry.cpp"),
                jniRegistrySource(identity, names));
        sourceFileNames.add("profile_jni_registry.cpp");
        write(layout.getCppDirectory().resolve("profile_variable.cpp"),
                variableSource(identity, names));
        sourceFileNames.add("profile_variable.cpp");
        generateStartupSources(layout, identity, template, names, sourceFileNames);
        generatePathConfigSources(layout, identity, template, names, sourceFileNames);

        for (String unit : template.getNativeSourceUnits()) {
            if (FIXED_UNITS.contains(unit) || STARTUP_UNITS.contains(unit)
                    || PATH_CONFIG_UNITS.contains(unit)) {
                continue;
            }
            String fileName = sourceFileName(identity, template, unit);
            write(layout.getCppDirectory().resolve(fileName),
                    helperSource(identity, template, unit, names));
            sourceFileNames.add(fileName);
        }

        // Overlay C++ files are copied into the same isolated directory by the caller. Keep
        // them in the explicit manifest so CMake never relies on an unsafe directory glob.
        sourceFileNames.addAll(overlaySourceFileNames);

        write(layout.getCmakeDirectory().resolve("profile-parameters.cmake"),
                cmakeParameters(identity, names));
        write(layout.getCmakeDirectory().resolve("profile-sources.cmake"),
                cmakeSources(sourceFileNames));
    }

    private static String configHeader(ProfileIdentityLock identity, NativeNames names) {
        String guard = "N" + names.namespace.substring(1).toUpperCase() + "_CONFIG_H";
        String bridgePath = identity.getJniBridgeClassName().replace('.', '/');
        return "#ifndef " + guard + "\n"
                + "#define " + guard + "\n\n"
                + "#define DPT_PROFILE_CONTRACT_ENABLED 1\n\n"
                + "#define DPT_PROFILE_ABI \"" + ProfileCodeGenerator.PROFILE_ABI + "\"\n\n"
                + "#include <cstddef>\n"
                + "#include <cstdint>\n"
                + "#include <jni.h>\n\n"
                + "namespace " + names.namespace + " {\n"
                + constant(identity, "so-name", identity.getSoName())
                + constant(identity, "jni-bridge", bridgePath)
                + constant(identity, "shell-config",
                        "assets/" + identity.getShellConfigAssetName())
                + constant(identity, "dex-asset", identity.getDexAssetName())
                + constant(identity, "code-item",
                        "assets/" + identity.getCodeItemAssetName())
                + constant(identity, "native-dir", identity.getNativeLibAssetDirName())
                + constant(identity, "runtime-dir", identity.getRuntimeDirName())
                + constant(identity, "junk-class",
                        identity.getJunkClassName().replace('.', '/'))
                + constant(identity, "build-key", identity.getBuildKey())
                + constant(identity, "section", names.variableSection)
                + "\nstruct " + names.runtimeConfigType + " {\n"
                + "    const char *" + names.runtimeShellConfigField + ";\n"
                + "    const char *" + names.runtimeCodeItemField + ";\n"
                + "    const char *" + names.runtimeDexAssetField + ";\n"
                + "    const char *" + names.runtimeDirectoryField + ";\n"
                + "    const char *" + names.runtimeBuildKeyField + ";\n"
                + "};\n"
                + "const " + names.runtimeConfigType + "& "
                + names.runtimeConfigAccessor + "() noexcept;\n\n"
                + "JNINativeMethod* " + names.tableAccessor + "() noexcept;\n"
                + "std::size_t " + names.countAccessor + "() noexcept;\n"
                + "void " + names.startupCoordinator
                + "(std::uint32_t stage) noexcept;\n"
                + "}\n\n"
                + macro("SO_NAME", names, constantName(identity, "so-name"))
                + macro("JNI_BRIDGE", names, constantName(identity, "jni-bridge"))
                + macro("SHELL_CONFIG_ASSET", names,
                        constantName(identity, "shell-config"))
                + macro("DEX_ASSET", names, constantName(identity, "dex-asset"))
                + macro("CODE_ITEM_ASSET", names, constantName(identity, "code-item"))
                + macro("NATIVE_LIB_ASSET_DIR", names,
                        constantName(identity, "native-dir"))
                + macro("RUNTIME_DIR", names, constantName(identity, "runtime-dir"))
                + macro("JUNK_CLASS", names, constantName(identity, "junk-class"))
                + macro("BUILD_KEY", names, constantName(identity, "build-key"))
                + macro("VARIABLE_SECTION", names, constantName(identity, "section"))
                + "#define DPT_PROFILE_RC4_KEY_SYMBOL " + identity.getRc4KeySymbol() + "\n"
                + "#define DPT_PROFILE_RUNTIME_CONFIG_TYPE " + names.namespace + "::"
                + names.runtimeConfigType + "\n"
                + "#define DPT_PROFILE_RUNTIME_CONFIG_ACCESSOR " + names.namespace + "::"
                + names.runtimeConfigAccessor + "\n"
                + "#define DPT_PROFILE_RUNTIME_SHELL_CONFIG_FIELD "
                + names.runtimeShellConfigField + "\n"
                + "#define DPT_PROFILE_RUNTIME_CODE_ITEM_FIELD "
                + names.runtimeCodeItemField + "\n"
                + "#define DPT_PROFILE_RUNTIME_DEX_ASSET_FIELD "
                + names.runtimeDexAssetField + "\n"
                + "#define DPT_PROFILE_RUNTIME_DIRECTORY_FIELD "
                + names.runtimeDirectoryField + "\n"
                + "#define DPT_PROFILE_RUNTIME_BUILD_KEY_FIELD "
                + names.runtimeBuildKeyField + "\n"
                + "#define DPT_PROFILE_JNI_TABLE_ACCESSOR " + names.namespace + "::"
                + names.tableAccessor + "\n"
                + "#define DPT_PROFILE_JNI_COUNT_ACCESSOR " + names.namespace + "::"
                + names.countAccessor + "\n\n"
                + "#define DPT_PROFILE_STARTUP_COORDINATOR " + names.namespace + "::"
                + names.startupCoordinator + "\n\n"
                + "#endif\n";
    }

    private static void generatePathConfigSources(
            ProfileCodeGenerator.GenerationLayout layout,
            ProfileIdentityLock identity,
            ProfileRecipeTemplate template,
            NativeNames names,
            List<String> sourceFileNames) throws IOException {
        Set<String> selectedUnits = new HashSet<>();
        for (String unit : template.getNativeSourceUnits()) {
            if (PATH_CONFIG_UNITS.contains(unit)) {
                selectedUnits.add(unit);
            }
        }
        if (selectedUnits.equals(new HashSet<>(Arrays.asList(PATH_DIRECT)))) {
            writePathConfigSource(layout, identity, template, PATH_DIRECT,
                    directPathConfigSource(names), sourceFileNames);
            return;
        }
        if (selectedUnits.equals(new HashSet<>(Arrays.asList(PATH_CONTEXT, CONFIG_ADAPTER)))) {
            String contextHeaderName = sourceHeaderFileName(identity, template, PATH_CONTEXT);
            write(layout.getCppDirectory().resolve(contextHeaderName),
                    pathContextHeader(identity, template, names));
            writePathConfigSource(layout, identity, template, PATH_CONTEXT,
                    pathContextSource(contextHeaderName, names), sourceFileNames);
            writePathConfigSource(layout, identity, template, CONFIG_ADAPTER,
                    configAdapterSource(contextHeaderName, names), sourceFileNames);
            return;
        }
        throw new IllegalArgumentException("Recipe Native 路径配置单元不完整："
                + template.getRecipeId());
    }

    private static void writePathConfigSource(
            ProfileCodeGenerator.GenerationLayout layout,
            ProfileIdentityLock identity,
            ProfileRecipeTemplate template,
            String unit,
            String source,
            List<String> sourceFileNames) throws IOException {
        String fileName = sourceFileName(identity, template, unit);
        write(layout.getCppDirectory().resolve(fileName), source);
        sourceFileNames.add(fileName);
    }

    private static String directPathConfigSource(NativeNames names) {
        return "#include \"profile_config.h\"\n\n"
                + "namespace " + names.namespace + " {\n"
                + "namespace {\n"
                + "const " + names.runtimeConfigType + " " + names.runtimeConfigSymbol
                + " = {\n"
                + "        DPT_PROFILE_SHELL_CONFIG_ASSET,\n"
                + "        DPT_PROFILE_CODE_ITEM_ASSET,\n"
                + "        DPT_PROFILE_DEX_ASSET,\n"
                + "        DPT_PROFILE_RUNTIME_DIR,\n"
                + "        DPT_PROFILE_BUILD_KEY\n"
                + "};\n"
                + "}\n\n"
                + "const " + names.runtimeConfigType + "& "
                + names.runtimeConfigAccessor + "() noexcept {\n"
                + "    return " + names.runtimeConfigSymbol + ";\n"
                + "}\n"
                + "}\n";
    }

    private static String pathContextHeader(ProfileIdentityLock identity,
                                            ProfileRecipeTemplate template,
                                            NativeNames names) {
        String guard = derive(identity, "native-path-context-guard-"
                + template.getRecipeId(), "g").toUpperCase(Locale.US) + "_H";
        return "#ifndef " + guard + "\n"
                + "#define " + guard + "\n\n"
                + "namespace " + names.namespace + " {\n"
                + "struct " + names.pathContextType + " {\n"
                + "    const char *" + names.pathContextRuntimeField + ";\n"
                + "    const char *" + names.pathContextBuildKeyField + ";\n"
                + "    const char *" + names.pathContextCodeItemField + ";\n"
                + "    const char *" + names.pathContextShellConfigField + ";\n"
                + "    const char *" + names.pathContextDexAssetField + ";\n"
                + "};\n"
                + "const " + names.pathContextType + "& "
                + names.pathContextAccessor + "() noexcept;\n"
                + "}\n\n"
                + "#endif\n";
    }

    private static String pathContextSource(String contextHeaderName, NativeNames names) {
        return "#include \"profile_config.h\"\n"
                + "#include \"" + contextHeaderName + "\"\n\n"
                + "namespace " + names.namespace + " {\n"
                + "namespace {\n"
                + "const " + names.pathContextType + " " + names.pathContextSymbol
                + " = {\n"
                + "        DPT_PROFILE_RUNTIME_DIR,\n"
                + "        DPT_PROFILE_BUILD_KEY,\n"
                + "        DPT_PROFILE_CODE_ITEM_ASSET,\n"
                + "        DPT_PROFILE_SHELL_CONFIG_ASSET,\n"
                + "        DPT_PROFILE_DEX_ASSET\n"
                + "};\n"
                + "}\n\n"
                + "const " + names.pathContextType + "& "
                + names.pathContextAccessor + "() noexcept {\n"
                + "    return " + names.pathContextSymbol + ";\n"
                + "}\n"
                + "}\n";
    }

    private static String configAdapterSource(String contextHeaderName, NativeNames names) {
        return "#include \"profile_config.h\"\n"
                + "#include \"" + contextHeaderName + "\"\n\n"
                + "namespace " + names.namespace + " {\n"
                + "const " + names.runtimeConfigType + "& "
                + names.runtimeConfigAccessor + "() noexcept {\n"
                + "    const auto &context = " + names.pathContextAccessor + "();\n"
                + "    static const " + names.runtimeConfigType + " config = {\n"
                + "            context." + names.pathContextShellConfigField + ",\n"
                + "            context." + names.pathContextCodeItemField + ",\n"
                + "            context." + names.pathContextDexAssetField + ",\n"
                + "            context." + names.pathContextRuntimeField + ",\n"
                + "            context." + names.pathContextBuildKeyField + "\n"
                + "    };\n"
                + "    return config;\n"
                + "}\n"
                + "}\n";
    }

    private static void generateStartupSources(ProfileCodeGenerator.GenerationLayout layout,
                                               ProfileIdentityLock identity,
                                               ProfileRecipeTemplate template,
                                               NativeNames names,
                                               List<String> sourceFileNames)
            throws IOException {
        Set<String> selectedUnits = new HashSet<>();
        for (String unit : template.getNativeSourceUnits()) {
            if (STARTUP_UNITS.contains(unit)) {
                selectedUnits.add(unit);
            }
        }
        Set<String> linearUnits = new HashSet<>(Arrays.asList(STARTUP_LINEAR));
        if (selectedUnits.equals(linearUnits)) {
            writeStartupSource(layout, identity, template, STARTUP_LINEAR,
                    linearStartupSource(identity, template, names), sourceFileNames);
            return;
        }
        Set<String> phasedUnits = new HashSet<>(Arrays.asList(STARTUP_PHASES,
                STARTUP_CONFIG_RISK_PHASE, STARTUP_REGISTRATION_PHASE));
        if (selectedUnits.equals(phasedUnits)) {
            String configRiskHelper = startupHelperName(identity, template,
                    STARTUP_CONFIG_RISK_PHASE);
            String registrationHelper = startupHelperName(identity, template,
                    STARTUP_REGISTRATION_PHASE);
            writeStartupSource(layout, identity, template, STARTUP_PHASES,
                    phasedStartupSource(names, configRiskHelper, registrationHelper),
                    sourceFileNames);
            writeStartupSource(layout, identity, template, STARTUP_CONFIG_RISK_PHASE,
                    phasedHelperSource(identity, template, names, STARTUP_CONFIG_RISK_PHASE,
                            configRiskHelper), sourceFileNames);
            writeStartupSource(layout, identity, template, STARTUP_REGISTRATION_PHASE,
                    phasedHelperSource(identity, template, names,
                            STARTUP_REGISTRATION_PHASE, registrationHelper), sourceFileNames);
            return;
        }
        throw new IllegalArgumentException("Recipe Native 启动单元不完整："
                + template.getRecipeId());
    }

    private static void writeStartupSource(ProfileCodeGenerator.GenerationLayout layout,
                                           ProfileIdentityLock identity,
                                           ProfileRecipeTemplate template,
                                           String unit, String source,
                                           List<String> sourceFileNames)
            throws IOException {
        String fileName = sourceFileName(identity, template, unit);
        write(layout.getCppDirectory().resolve(fileName), source);
        sourceFileNames.add(fileName);
    }

    private static String linearStartupSource(ProfileIdentityLock identity,
                                              ProfileRecipeTemplate template,
                                              NativeNames names) {
        String first = startupHelperName(identity, template, "startup-linear-first");
        String second = startupHelperName(identity, template, "startup-linear-second");
        String firstValue = startupValue(identity, template, "startup-linear-first");
        String secondValue = startupValue(identity, template, "startup-linear-second");
        return "#include \"profile_config.h\"\n\n"
                + "#include <cstdint>\n\n"
                + "namespace " + names.namespace + " {\n"
                + "namespace {\n"
                + "std::uint32_t " + first + "(std::uint32_t stage) noexcept {\n"
                + "    return stage + 0x" + firstValue + "u;\n"
                + "}\n\n"
                + "std::uint32_t " + second + "(std::uint32_t value) noexcept {\n"
                + "    return (value ^ 0x" + secondValue + "u) + 1u;\n"
                + "}\n"
                + "}\n\n"
                + "void " + names.startupCoordinator
                + "(std::uint32_t stage) noexcept {\n"
                + "    volatile std::uint32_t observed = " + second + "("
                + first + "(stage));\n"
                + "    (void) observed;\n"
                + "}\n"
                + "}\n";
    }

    private static String phasedStartupSource(NativeNames names, String configRiskHelper,
                                              String registrationHelper) {
        return "#include \"profile_config.h\"\n\n"
                + "#include <cstdint>\n\n"
                + "namespace " + names.namespace + " {\n"
                + "std::uint32_t " + configRiskHelper
                + "(std::uint32_t stage) noexcept;\n"
                + "std::uint32_t " + registrationHelper
                + "(std::uint32_t stage) noexcept;\n\n"
                + "void " + names.startupCoordinator
                + "(std::uint32_t stage) noexcept {\n"
                + "    volatile std::uint32_t observed = 0u;\n"
                + "    switch (stage) {\n"
                + "        case 0u:\n"
                + "        case 1u:\n"
                + "            observed = " + configRiskHelper + "(stage);\n"
                + "            break;\n"
                + "        default:\n"
                + "            observed = " + registrationHelper + "(stage);\n"
                + "            break;\n"
                + "    }\n"
                + "    (void) observed;\n"
                + "}\n"
                + "}\n";
    }

    private static String phasedHelperSource(ProfileIdentityLock identity,
                                             ProfileRecipeTemplate template,
                                             NativeNames names, String unit,
                                             String helperName) {
        String value = startupValue(identity, template, unit);
        return "#include \"profile_config.h\"\n\n"
                + "#include <cstdint>\n\n"
                + "namespace " + names.namespace + " {\n"
                + "std::uint32_t " + helperName
                + "(std::uint32_t stage) noexcept {\n"
                + "    return (stage ^ 0x" + value + "u) + 1u;\n"
                + "}\n"
                + "}\n";
    }

    private static String constant(ProfileIdentityLock identity, String label, String value) {
        return "inline constexpr char " + constantName(identity, label)
                + "[] = \"" + escape(value) + "\";\n";
    }

    private static String constantName(ProfileIdentityLock identity, String label) {
        return derive(identity, "native-constant-" + label, "c");
    }

    private static String macro(String suffix, NativeNames names, String constantName) {
        return "#define DPT_PROFILE_" + suffix + " " + names.namespace + "::"
                + constantName + "\n";
    }

    private static String jniRegistrySource(ProfileIdentityLock identity, NativeNames names) {
        StringBuilder source = new StringBuilder("#include \"dpt.h\"\n")
                .append("#include \"profile_config.h\"\n\n")
                .append("namespace ").append(names.namespace).append(" {\n")
                .append("namespace {\n")
                .append("JNINativeMethod ").append(names.tableSymbol).append("[] = {\n");
        for (ProfileJniSemantic semantic : ProfileJniSemantic.values()) {
            source.append("        {\"")
                    .append(semantic.requireMethodName(identity.getJniMethods()))
                    .append("\", \"").append(semantic.getJvmSignature())
                    .append("\", (void *) ").append(coreFunction(semantic)).append("},\n");
        }
        return source.append("};\n")
                .append("}\n\n")
                .append("JNINativeMethod* ").append(names.tableAccessor)
                .append("() noexcept {\n")
                .append("    return ").append(names.tableSymbol).append(";\n")
                .append("}\n\n")
                .append("std::size_t ").append(names.countAccessor)
                .append("() noexcept {\n")
                .append("    return sizeof(").append(names.tableSymbol).append(") / sizeof(")
                .append(names.tableSymbol).append("[0]);\n")
                .append("}\n")
                .append("}\n").toString();
    }

    private static String variableSource(ProfileIdentityLock identity, NativeNames names) {
        byte[] initialValue = identity.getBuildKey().substring(0, 16)
                .getBytes(StandardCharsets.US_ASCII);
        StringBuilder bytes = new StringBuilder();
        for (int index = 0; index < initialValue.length; index++) {
            if (index > 0) {
                bytes.append(", ");
            }
            bytes.append(String.format(Locale.US, "0x%02x", initialValue[index] & 0xff));
        }
        return "#include <cstdint>\n\n"
                + "extern \"C\" {\n"
                + "__attribute__((visibility(\"default\"), used, section(\""
                + names.variableSection + "\")))\n"
                + "std::uint8_t " + identity.getRc4KeySymbol() + "[16] = {"
                + bytes + "};\n"
                + "}\n";
    }

    private static String helperSource(ProfileIdentityLock identity,
                                       ProfileRecipeTemplate template, String unit,
                                       NativeNames names) {
        String label = template.getRecipeId() + "-" + unit;
        String functionName = derive(identity, "native-helper-function-" + label, "h");
        String constantHex = derive(identity, "native-helper-value-" + label, "v")
                .substring(1, 7);
        return "#include \"profile_config.h\"\n\n"
                + "namespace " + names.namespace + " {\n"
                + "int " + functionName + "(int value) noexcept {\n"
                + "    return value ^ 0x" + constantHex + ";\n"
                + "}\n"
                + "}\n";
    }

    private static String cmakeParameters(ProfileIdentityLock identity, NativeNames names) {
        return "set(DPT_PROFILE_BUILD 1)\n"
                + "set(DPT_PROFILE_ABI \"" + ProfileCodeGenerator.PROFILE_ABI + "\")\n"
                + "set(DPT_PROFILE_SO_FILE \"" + identity.getSoName() + "\")\n"
                + "set(DPT_PROFILE_SO_TARGET \"" + soTarget(identity.getSoName()) + "\")\n"
                + "set(DPT_PROFILE_BUILD_KEY \"" + identity.getBuildKey() + "\")\n"
                + "set(DPT_PROFILE_JNI_BRIDGE \""
                + identity.getJniBridgeClassName().replace('.', '/') + "\")\n"
                + "set(DPT_PROFILE_NAMESPACE \"" + names.namespace + "\")\n"
                + "set(DPT_PROFILE_VARIABLE_SECTION \"" + names.variableSection + "\")\n"
                + "set(DPT_PROFILE_RC4_KEY_SYMBOL \"" + identity.getRc4KeySymbol() + "\")\n"
                + "set(DPT_PROFILE_JNI_TABLE_ACCESSOR \"" + names.namespace + "::"
                + names.tableAccessor + "\")\n"
                + "set(DPT_PROFILE_JNI_COUNT_ACCESSOR \"" + names.namespace + "::"
                + names.countAccessor + "\")\n"
                + "set(DPT_PROFILE_STARTUP_COORDINATOR \"" + names.namespace + "::"
                + names.startupCoordinator + "\")\n"
                + "set(DPT_PROFILE_GENERATED_INCLUDE_DIR \"${CMAKE_CURRENT_LIST_DIR}/../cpp\")\n";
    }

    private static String cmakeSources(List<String> sourceFileNames) {
        StringBuilder source = new StringBuilder("set(DPT_PROFILE_GENERATED_SOURCES\n");
        for (String fileName : sourceFileNames) {
            source.append("    \"${CMAKE_CURRENT_LIST_DIR}/../cpp/")
                    .append(fileName).append("\"\n");
        }
        return source.append(")\n").toString();
    }

    private static String coreFunction(ProfileJniSemantic semantic) {
        switch (semantic) {
            case CALL_REAL_APPLICATION_ON_CREATE:
                return "callRealApplicationOnCreate";
            case INIT_APP:
                return "init_app";
            case GET_SOURCE_DIR:
                return "getSourceDirExport";
            case GET_COMPRESSED_DEXES_PATH:
                return "getCompressedDexesPathExport";
            case READ_APP_COMPONENT_FACTORY:
                return "readAppComponentFactory";
            case READ_APPLICATION_NAME:
                return "readApplicationName";
            case COMBINE_DEX_ELEMENTS:
                return "combineDexElements";
            case REMOVE_DEX_ELEMENTS:
                return "removeDexElements";
            case REPLACE_APPLICATION:
                return "replaceApplication";
            case STATIC_INITIALIZE:
                return "clinit";
            default:
                throw new IllegalStateException("unsupported JNI semantic: " + semantic);
        }
    }

    private static String soTarget(String soName) {
        return soName.substring("lib".length(), soName.length() - ".so".length());
    }

    private static String sourceFileName(ProfileIdentityLock identity,
                                         ProfileRecipeTemplate template, String unit) {
        return derive(identity, "native-file-" + template.getRecipeId() + "-" + unit,
                "u") + ".cpp";
    }

    private static String sourceHeaderFileName(ProfileIdentityLock identity,
                                               ProfileRecipeTemplate template, String unit) {
        return derive(identity, "native-header-" + template.getRecipeId() + "-" + unit,
                "u") + ".h";
    }

    private static String startupHelperName(ProfileIdentityLock identity,
                                            ProfileRecipeTemplate template, String unit) {
        return derive(identity, "native-startup-helper-" + template.getRecipeId()
                + "-" + unit, "h");
    }

    private static String startupValue(ProfileIdentityLock identity,
                                       ProfileRecipeTemplate template, String unit) {
        return derive(identity, "native-startup-value-" + template.getRecipeId()
                + "-" + unit, "v").substring(1, 7);
    }

    private static String derive(ProfileIdentityLock identity, String label, String prefix) {
        return ProfileStableNameDeriver.identifier(identity.getIdentitySeed(), label, prefix, 12);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static final class NativeNames {
        private final String namespace;
        private final String tableSymbol;
        private final String tableAccessor;
        private final String countAccessor;
        private final String startupCoordinator;
        private final String variableSection;
        private final String runtimeConfigType;
        private final String runtimeConfigSymbol;
        private final String runtimeConfigAccessor;
        private final String runtimeShellConfigField;
        private final String runtimeCodeItemField;
        private final String runtimeDexAssetField;
        private final String runtimeDirectoryField;
        private final String runtimeBuildKeyField;
        private final String pathContextType;
        private final String pathContextSymbol;
        private final String pathContextAccessor;
        private final String pathContextShellConfigField;
        private final String pathContextCodeItemField;
        private final String pathContextDexAssetField;
        private final String pathContextRuntimeField;
        private final String pathContextBuildKeyField;

        private NativeNames(ProfileIdentityLock identity, ProfileRecipeTemplate template) {
            String recipeId = template.getRecipeId();
            namespace = derive(identity, "native-namespace", "n");
            tableSymbol = derive(identity, "native-jni-table", "t");
            tableAccessor = derive(identity, "native-jni-table-accessor", "m");
            countAccessor = derive(identity, "native-jni-count-accessor", "m");
            startupCoordinator = derive(identity, "native-startup-coordinator", "m");
            variableSection = "." + derive(identity,
                    "native-variable-section-" + recipeId, "s");
            runtimeConfigType = derive(identity,
                    "native-runtime-config-type-" + recipeId, "p");
            runtimeConfigSymbol = derive(identity,
                    "native-runtime-config-symbol-" + recipeId, "v");
            runtimeConfigAccessor = derive(identity,
                    "native-runtime-config-accessor-" + recipeId, "m");
            runtimeShellConfigField = derive(identity,
                    "native-runtime-config-field-shell-config-" + recipeId, "f");
            runtimeCodeItemField = derive(identity,
                    "native-runtime-config-field-code-item-" + recipeId, "f");
            runtimeDexAssetField = derive(identity,
                    "native-runtime-config-field-dex-asset-" + recipeId, "f");
            runtimeDirectoryField = derive(identity,
                    "native-runtime-config-field-runtime-dir-" + recipeId, "f");
            runtimeBuildKeyField = derive(identity,
                    "native-runtime-config-field-build-key-" + recipeId, "f");
            pathContextType = derive(identity,
                    "native-path-context-type-" + recipeId, "q");
            pathContextSymbol = derive(identity,
                    "native-path-context-symbol-" + recipeId, "v");
            pathContextAccessor = derive(identity,
                    "native-path-context-accessor-" + recipeId, "m");
            pathContextShellConfigField = derive(identity,
                    "native-path-context-field-shell-config-" + recipeId, "f");
            pathContextCodeItemField = derive(identity,
                    "native-path-context-field-code-item-" + recipeId, "f");
            pathContextDexAssetField = derive(identity,
                    "native-path-context-field-dex-asset-" + recipeId, "f");
            pathContextRuntimeField = derive(identity,
                    "native-path-context-field-runtime-dir-" + recipeId, "f");
            pathContextBuildKeyField = derive(identity,
                    "native-path-context-field-build-key-" + recipeId, "f");
        }
    }
}
