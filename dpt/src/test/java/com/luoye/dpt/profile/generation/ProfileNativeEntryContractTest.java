package com.luoye.dpt.profile.generation;

import com.luoye.dpt.profile.generation.recipe.ProfileRecipe;
import com.luoye.dpt.profile.generation.recipe.ProfileRecipeRegistry;
import com.luoye.dpt.profile.generation.recipe.ProfileRecipeTemplate;
import com.luoye.dpt.profile.generation.recipe.ProfileRecipeTemplateRegistry;
import com.luoye.dpt.profile.model.ProfileIdentityLock;
import com.luoye.dpt.profile.model.ProfileSchemaVersions;
import com.luoye.dpt.profile.storage.ProfileCreationResult;
import com.luoye.dpt.profile.storage.ProfileCreationService;
import com.luoye.dpt.profile.storage.ProfileIdentityStore;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ProfileNativeEntryContractTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void nativeEntryConsumesGeneratedProfileRegistryAndKeepsInitializationOrder()
            throws Exception {
        ProfileIdentityLock identity = createIdentity("com.example.nativeentry");
        ProfileCodeGenerator.GenerationLayout layout = new ProfileCodeGenerator(
                temporaryFolder.newFolder("generated").toPath())
                .generate(identity, "r000001", template());

        String generatedConfig = Files.readString(
                layout.getCppDirectory().resolve("profile_config.h"));
        Assert.assertTrue(generatedConfig.contains(
                "#define DPT_PROFILE_CONTRACT_ENABLED 1"));
        Assert.assertTrue(generatedConfig.contains(
                "assets/" + identity.getShellConfigAssetName()));
        Assert.assertTrue(generatedConfig.contains(
                "#define DPT_PROFILE_STARTUP_COORDINATOR"));

        String coreSource = Files.readString(findProjectRoot()
                .resolve("shell/src/main/cpp/dpt.cpp"));
        Assert.assertTrue(coreSource.contains("#include \"profile_config.h\""));
        Assert.assertTrue(coreSource.contains("#if !DPT_PROFILE_BUILD"));

        String registration = section(coreSource,
                "DPT_ENCRYPT static bool registerNativeMethods", "DPT_ENCRYPT void init_app");
        String profileRegistration = section(registration,
                "#if DPT_PROFILE_BUILD", "#else");
        Assert.assertTrue(profileRegistration.contains("DPT_PROFILE_JNI_BRIDGE"));
        Assert.assertTrue(profileRegistration.contains("DPT_PROFILE_JNI_TABLE_ACCESSOR()"));
        Assert.assertTrue(profileRegistration.contains("DPT_PROFILE_JNI_COUNT_ACCESSOR()"));
        Assert.assertTrue(profileRegistration.contains(
                "g_shell_config.jni_class_name != DPT_PROFILE_JNI_BRIDGE"));
        Assert.assertFalse(profileRegistration.contains("gMethods"));

        String legacyRegistration = section(registration, "#else", "#endif");
        Assert.assertTrue(legacyRegistration.contains("gMethods"));
        Assert.assertTrue(legacyRegistration.contains("g_shell_config.jni_class_name"));

        String onLoad = section(coreSource, "JNIEXPORT jint JNI_OnLoad",
                "JNIEXPORT void JNI_OnUnload");
        assertInOrder(onLoad,
                "ProfileStartupStage::READ_PROFILE_CONFIGURATION",
                "read_shell_config(env)",
                "ProfileStartupStage::RUN_EXISTING_RISK_CHECKS",
                "antiRisk()",
                "if (!configLoaded)",
                "ProfileStartupStage::REGISTER_PROFILE_JNI",
                "registerNativeMethods(env)");
    }

    @Test
    public void profileBuildModeIsExplicitAndCannotSilentlyFallBackToLegacy() throws Exception {
        ProfileIdentityLock identity = createIdentity("com.example.nativebuildmode");
        ProfileCodeGenerator.GenerationLayout layout = new ProfileCodeGenerator(
                temporaryFolder.newFolder("build-mode-generated").toPath())
                .generate(identity, "r000001", template());

        String parameters = Files.readString(
                layout.getCmakeDirectory().resolve("profile-parameters.cmake"));
        Assert.assertTrue(parameters.contains("set(DPT_PROFILE_BUILD 1)"));

        String coreSource = Files.readString(findProjectRoot()
                .resolve("shell/src/main/cpp/dpt.cpp"));
        String buildModeSelection = section(coreSource,
                "#ifndef DPT_PROFILE_BUILD", "#include <memory>");
        Assert.assertTrue(buildModeSelection.contains("#define DPT_PROFILE_BUILD 0"));
        Assert.assertTrue(buildModeSelection.contains("#if DPT_PROFILE_BUILD"));
        Assert.assertTrue(buildModeSelection.contains(
                "#error \"DPT_PROFILE_BUILD=1 requires generated profile_config.h\""));
        Assert.assertTrue(buildModeSelection.contains(
                "#error \"generated Profile contract is incomplete\""));

        String legacyTable = section(coreSource, "ShellConfig g_shell_config;",
                "DPT_ENCRYPT jobjectArray makePathElements");
        Assert.assertTrue(legacyTable.contains("#if !DPT_PROFILE_BUILD"));
    }

    @Test
    public void profileConfigIsCommittedAtomicallyAndFailureStopsRegistration() throws Exception {
        String coreSource = Files.readString(findProjectRoot()
                .resolve("shell/src/main/cpp/dpt.cpp"));
        String configReader = section(coreSource,
                "DPT_ENCRYPT bool read_shell_config", "void veritySignature");
        Assert.assertTrue(configReader.contains("ShellConfig loaded_config"));
        Assert.assertTrue(configReader.contains("ShellConfig &config_target = loaded_config"));
        Assert.assertTrue(configReader.contains(
                "g_shell_config = std::move(loaded_config)"));
        Assert.assertTrue(configReader.contains(
                "shell_config.at(keyAppName).get<std::string>()"));
        Assert.assertTrue(configReader.contains(
                "shell_config.at(keyJniClsName).get<std::string>()"));
        Assert.assertTrue(configReader.contains(
                "shell_config.at(keyRiskCheckFlags).is_number_integer()"));
        Assert.assertTrue(configReader.contains(
                "const char *shellConfigEntry = getShellConfigEntryName()"));
        Assert.assertTrue(configReader.contains(
                "const char *buildKey = getShellBuildKey()"));
        assertInOrder(configReader,
                "config_target.application_name",
                "config_target.jni_class_name",
                "g_shell_config = std::move(loaded_config)");

        String onLoad = section(coreSource, "JNIEXPORT jint JNI_OnLoad",
                "JNIEXPORT void JNI_OnUnload");
        assertInOrder(onLoad,
                "const bool configLoaded = read_shell_config(env)",
                "antiRisk()",
                "if (!configLoaded)",
                "registerNativeMethods(env)");
    }

    @Test
    public void runtimePathsUseGeneratedAdapterAndKeepLegacyValues() throws Exception {
        ProfileIdentityLock identity = createIdentity("com.example.nativepaths");
        ProfileCodeGenerator.GenerationLayout layout = new ProfileCodeGenerator(
                temporaryFolder.newFolder("runtime-path-generated").toPath())
                .generate(identity, "r000001", template());
        String generatedConfig = Files.readString(
                layout.getCppDirectory().resolve("profile_config.h"));
        Assert.assertTrue(generatedConfig.contains(
                "#define DPT_PROFILE_RUNTIME_CONFIG_ACCESSOR"));
        Assert.assertTrue(generatedConfig.contains(
                "#define DPT_PROFILE_RUNTIME_SHELL_CONFIG_FIELD"));

        Path projectRoot = findProjectRoot();
        String coreSource = Files.readString(projectRoot.resolve("shell/src/main/cpp/dpt.cpp"));
        String runtimeAccessors = section(coreSource,
                "const char *getShellConfigEntryName", "DPT_ENCRYPT jobjectArray makePathElements");
        Assert.assertTrue(runtimeAccessors.contains("DPT_PROFILE_RUNTIME_CONFIG_ACCESSOR()"));
        Assert.assertTrue(runtimeAccessors.contains(
                "DPT_PROFILE_RUNTIME_SHELL_CONFIG_FIELD"));
        Assert.assertTrue(runtimeAccessors.contains(
                "DPT_PROFILE_RUNTIME_CODE_ITEM_FIELD"));
        Assert.assertTrue(runtimeAccessors.contains(
                "DPT_PROFILE_RUNTIME_DEX_ASSET_FIELD"));
        Assert.assertTrue(runtimeAccessors.contains(
                "DPT_PROFILE_RUNTIME_DIRECTORY_FIELD"));
        Assert.assertTrue(runtimeAccessors.contains(
                "DPT_PROFILE_RUNTIME_BUILD_KEY_FIELD"));
        Assert.assertTrue(runtimeAccessors.contains("SHELL_CONFIG_IN_ZIP"));
        Assert.assertTrue(runtimeAccessors.contains("CODE_ITEM_NAME_IN_ZIP"));
        Assert.assertTrue(runtimeAccessors.contains("DEXES_ZIP_NAME"));
        Assert.assertTrue(runtimeAccessors.contains("CACHE_DIR"));
        Assert.assertTrue(runtimeAccessors.contains("DPT_BUILD_KEY"));

        String initApp = section(coreSource,
                "DPT_ENCRYPT void init_app", "DPT_ENCRYPT void readCodeItem");
        Assert.assertTrue(initApp.contains("getCodeItemEntryName()"));
        Assert.assertFalse(initApp.contains("CODE_ITEM_NAME_IN_ZIP"));

        String configReader = section(coreSource,
                "DPT_ENCRYPT bool read_shell_config", "void veritySignature");
        Assert.assertTrue(configReader.contains("getShellConfigEntryName()"));
        Assert.assertTrue(configReader.contains("getShellBuildKey()"));
        Assert.assertFalse(configReader.contains("DPT_PROFILE_SHELL_CONFIG_ASSET"));
        Assert.assertFalse(configReader.contains("DPT_PROFILE_BUILD_KEY"));

        String utilSource = Files.readString(
                projectRoot.resolve("shell/src/main/cpp/dpt_util.cpp"));
        String pathBuilders = section(utilSource,
                "void getCompressedDexesPath", "jstring getCompressedDexesPathExport");
        Assert.assertTrue(pathBuilders.contains("getRuntimeDirectoryName()"));
        Assert.assertTrue(pathBuilders.contains("getDexAssetFileName()"));
        Assert.assertFalse(pathBuilders.contains("CACHE_DIR"));
        Assert.assertFalse(pathBuilders.contains("DEXES_ZIP_NAME"));

        String hookSource = Files.readString(
                projectRoot.resolve("shell/src/main/cpp/dpt_hook.cpp"));
        Assert.assertTrue(hookSource.contains("getDexAssetFileName()"));
        Assert.assertFalse(hookSource.contains("DEXES_ZIP_NAME"));
    }

    @Test
    public void profileCoreUsesGeneratedRc4SlotAndSoNameWhileLegacyKeepsOldSymbols()
            throws Exception {
        String coreSource = Files.readString(findProjectRoot()
                .resolve("shell/src/main/cpp/dpt.cpp"));
        Assert.assertTrue(coreSource.contains("DPT_PROFILE_RC4_KEY_SYMBOL"));
        Assert.assertTrue(coreSource.contains("getShellSoName()"));

        String profileKeySlot = section(coreSource,
                "#if DPT_PROFILE_BUILD", "#if !DPT_PROFILE_BUILD");
        Assert.assertTrue(profileKeySlot.contains("extern \"C\""));
        Assert.assertTrue(profileKeySlot.contains("DPT_PROFILE_RC4_KEY_SYMBOL"));

        String decryptSection = section(coreSource,
                "void decrypt_section", "void decrypt_bitcode");
        Assert.assertTrue(decryptSection.contains("getShellSoName()"));
        Assert.assertTrue(decryptSection.contains("DPT_RUNTIME_KEY_SLOT"));
        Assert.assertFalse(decryptSection.contains("SO_NAME)"));
        Assert.assertFalse(decryptSection.contains("DPT_UNKNOWN_DATA), 16"));

        String configReader = section(coreSource,
                "DPT_ENCRYPT bool read_shell_config", "void veritySignature");
        Assert.assertTrue(configReader.contains("DPT_RUNTIME_KEY_SLOT"));
        Assert.assertFalse(configReader.contains("hmac_sha256(DPT_UNKNOWN_DATA"));
        Assert.assertFalse(configReader.contains("memcpy(iv, DPT_UNKNOWN_DATA"));

        String legacySlot = section(coreSource,
                "#if !DPT_PROFILE_BUILD", "static JNINativeMethod gMethods");
        Assert.assertTrue(legacySlot.contains("DPT_UNKNOWN_DATA"));
    }

    @Test
    public void nativeDexCacheBindsExtractedPayloadToCurrentClassesDexMetadata()
            throws Exception {
        String source = Files.readString(findProjectRoot()
                .resolve("shell/src/main/cpp/dpt_util.cpp"));
        Assert.assertTrue("Native cache must read ZIP entry metadata without reusing a blind hit",
                source.contains("read_zip_file_entry_info"));
        Assert.assertTrue("Native cache must persist a sidecar identity record",
                source.contains(".meta"));
        Assert.assertTrue("Cache identity must include the central-directory CRC",
                source.contains("crc"));
        Assert.assertTrue("Cache identity must include entry sizes",
                source.contains("compressed_size") && source.contains("uncompressed_size"));
        Assert.assertTrue("Cache replacement must use an atomic rename",
                source.contains("rename("));

        String extraction = section(source, "extractDexesInNeeded", "align_to_page_size");
        assertInOrder(extraction,
                "read_zip_file_entry_info",
                ".meta",
                "writeDexAchieve",
                "rename(");
        Assert.assertFalse("Existing cache cannot be accepted without metadata validation",
                extraction.contains("DLOGI(\"dex files is achieved!\")"));
    }

    private ProfileIdentityLock createIdentity(String applicationId) throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("identity").toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot)
                .create(applicationId);
        return new ProfileIdentityStore(profilesRoot).loadExisting(applicationId,
                created.getProfileId());
    }

    private static ProfileRecipeTemplate template() {
        ProfileRecipe recipe = ProfileRecipeRegistry.builtIn().requireVerified(
                ProfileRecipeRegistry.RECIPE_A, ProfileSchemaVersions.PROFILE_PROTOCOL);
        return ProfileRecipeTemplateRegistry.builtIn().requireFor(recipe);
    }

    private static String section(String source, String startToken, String endToken) {
        int start = source.indexOf(startToken);
        int end = start < 0 ? -1 : source.indexOf(endToken, start + startToken.length());
        Assert.assertTrue("Missing start token: " + startToken, start >= 0);
        Assert.assertTrue("Missing end token: " + endToken, end > start);
        return source.substring(start, end);
    }

    private static void assertInOrder(String source, String... tokens) {
        int previous = -1;
        for (String token : tokens) {
            int current = source.indexOf(token);
            Assert.assertTrue("Missing or out-of-order token: " + token, current > previous);
            previous = current;
        }
    }

    private static Path findProjectRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("settings.gradle"))
                    && Files.isRegularFile(current.resolve("gradlew"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("dpt-shell project root not found");
    }
}
