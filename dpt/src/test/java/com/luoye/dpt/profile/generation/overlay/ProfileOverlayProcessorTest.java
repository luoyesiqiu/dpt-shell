package com.luoye.dpt.profile.generation.overlay;

import com.luoye.dpt.profile.generation.ProfileCodeGenerator;
import com.luoye.dpt.profile.generation.recipe.ProfileRecipe;
import com.luoye.dpt.profile.generation.recipe.ProfileRecipeRegistry;
import com.luoye.dpt.profile.generation.recipe.ProfileRecipeTemplate;
import com.luoye.dpt.profile.generation.recipe.ProfileRecipeTemplateRegistry;
import com.luoye.dpt.profile.model.ProfileIdentityLock;
import com.luoye.dpt.profile.model.ProfileSchemaVersions;
import com.luoye.dpt.profile.storage.ProfileCreationResult;
import com.luoye.dpt.profile.storage.ProfileCreationService;
import com.luoye.dpt.profile.storage.ProfileIdentityStore;
import com.luoye.dpt.util.MessageDigestUtils;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ProfileOverlayProcessorTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void validatesHashesAndCopiesRegisteredJavaAndCppSources() throws Exception {
        ProfileIdentityLock identity = createIdentity("com.example.valid");
        Path overlayRoot = newOverlay("valid-overlay");
        Path javaRelative = javaRelative(identity, "OverlayHelper.java");
        write(overlayRoot.resolve("java/profile-private").resolve(javaRelative),
                "package " + identity.getShellPackageName() + ";\n"
                        + "final class OverlayHelper { String value() { return \"local\"; } }\n");
        write(overlayRoot.resolve("cpp/path-config/overlay_paths.cpp"),
                "#include \"profile_config.h\"\n"
                        + "int overlay_path_value(int value) noexcept { return value + 1; }\n");

        ProfileOverlayProcessor.PreparedOverlay prepared = new ProfileOverlayProcessor()
                .prepare(overlayRoot, identity);
        ProfileCodeGenerator.GenerationLayout layout = new ProfileCodeGenerator(
                temporaryFolder.newFolder("generated").toPath())
                .prepare(identity.getProfileId(), "r000001");
        prepared.copyTo(layout);

        Assert.assertEquals(2, prepared.getFileCount());
        Assert.assertTrue(prepared.getSha256().matches("[0-9a-f]{64}"));
        Assert.assertNotEquals(MessageDigestUtils.sha256(new byte[0]), prepared.getSha256());
        Assert.assertTrue(Files.isRegularFile(layout.getJavaDirectory().resolve(javaRelative)));
        Assert.assertTrue(Files.isRegularFile(layout.getCppDirectory()
                .resolve("overlay_paths.cpp")));
    }

    @Test
    public void rejectsUnregisteredInterfaceDirectoryAndUnsupportedSuffix() throws Exception {
        ProfileIdentityLock identity = createIdentity("com.example.interfacecheck");
        Path unknown = newOverlay("unknown-interface");
        write(unknown.resolve("java/jni-registration").resolve(javaRelative(identity,
                        "Invalid.java")),
                "package " + identity.getShellPackageName() + "; final class Invalid {}\n");
        assertRejected("扩展接口", () -> prepare(unknown, identity));

        Path unsupported = newOverlay("unsupported-suffix");
        write(unsupported.resolve("java/profile-private/Invalid.kt"), "class Invalid\n");
        assertRejected("文件类型", () -> prepare(unsupported, identity));
    }

    @Test
    public void rejectsGeneratedFileConflictBeforeCopyingAnyOverlaySource() throws Exception {
        ProfileIdentityLock identity = createIdentity("com.example.conflict");
        Path overlayRoot = newOverlay("conflict-overlay");
        write(overlayRoot.resolve("cpp/profile-private/profile_config.h"), "#pragma once\n");
        write(overlayRoot.resolve("cpp/profile-private/extra.cpp"),
                "int profile_extra() noexcept { return 1; }\n");
        ProfileOverlayProcessor.PreparedOverlay prepared = prepare(overlayRoot, identity);
        ProfileCodeGenerator.GenerationLayout layout = new ProfileCodeGenerator(
                temporaryFolder.newFolder("conflict-generated").toPath())
                .generate(identity, "r000001", recipeATemplate());

        assertRejected("生成文件冲突", () -> prepared.copyTo(layout));

        Assert.assertFalse(Files.exists(layout.getCppDirectory().resolve("extra.cpp")));
    }

    @Test
    public void rejectsGeneratedJavaTypeConflictEvenWhenFileNameDiffers() throws Exception {
        ProfileIdentityLock identity = createIdentity("com.example.typeconflict");
        Path overlayRoot = newOverlay("type-conflict-overlay");
        String proxySimpleName = identity.getProxyApplicationClassName().substring(
                identity.getProxyApplicationClassName().lastIndexOf('.') + 1);
        writeJava(overlayRoot, identity, "DifferentFile.java",
                "final class " + proxySimpleName + " {}\n");
        ProfileOverlayProcessor.PreparedOverlay prepared = prepare(overlayRoot, identity);
        ProfileCodeGenerator.GenerationLayout layout = new ProfileCodeGenerator(
                temporaryFolder.newFolder("type-conflict-generated").toPath())
                .generate(identity, "r000001", recipeATemplate());

        assertRejected("生成文件冲突", () -> prepared.copyTo(layout));
    }

    @Test
    public void rejectsDependencyAndManifestChanges() throws Exception {
        ProfileIdentityLock identity = createIdentity("com.example.metadata");
        Path dependency = newOverlay("dependency-overlay");
        write(dependency.resolve("build.gradle"), "dependencies { implementation 'x:y:1' }\n");
        assertRejected("依赖", () -> prepare(dependency, identity));

        Path importedDependency = newOverlay("imported-dependency");
        writeJava(importedDependency, identity, "External.java",
                "import okhttp3.OkHttpClient;\nfinal class External {}\n");
        assertRejected("依赖", () -> prepare(importedDependency, identity));

        Path manifest = newOverlay("manifest-overlay");
        write(manifest.resolve("AndroidManifest.xml"),
                "<manifest><uses-permission android:name=\"android.permission.INTERNET\"/>"
                        + "<application><activity android:name=\".Extra\"/></application></manifest>\n");
        assertRejected("Manifest", () -> prepare(manifest, identity));
    }

    @Test
    public void rejectsImmutableCoreAndJniSymbols() throws Exception {
        ProfileIdentityLock identity = createIdentity("com.example.core");
        Path nativeOverlay = newOverlay("native-core-overlay");
        write(nativeOverlay.resolve("cpp/startup-coordination/entry.cpp"),
                "jint JNI_OnLoad(JavaVM *vm, void *) { return JNI_VERSION_1_6; }\n");
        assertRejected("禁用 API/符号", () -> prepare(nativeOverlay, identity));

        Path javaOverlay = newOverlay("java-core-overlay");
        writeJava(javaOverlay, identity, "NativeChange.java",
                "final class NativeChange { native void replaceApplication(); }\n");
        assertRejected("禁用 API/符号", () -> prepare(javaOverlay, identity));
    }

    @Test
    public void rejectsEveryImmutableNativeCoreCapability() throws Exception {
        ProfileIdentityLock identity = createIdentity("com.example.nativecore");
        String[] protectedSources = {
                "struct DexHeader {};\n",
                "const char *key = DPT_BUILD_KEY;\n",
                "void decode() { aes_cbc_decrypt(nullptr, 0, nullptr, nullptr, 0); }\n",
                "bool install() { return hook_DefineClass(); }\n",
                "void inspect() { detectRisk(); }\n",
                "void restore() { readApplicationName(nullptr, nullptr); }\n"
        };
        for (int index = 0; index < protectedSources.length; index++) {
            Path overlayRoot = newOverlay("native-core-capability-" + index);
            write(overlayRoot.resolve("cpp/profile-private/core_attempt_" + index + ".cpp"),
                    protectedSources[index]);
            assertRejected("不可变 Native 核心", () -> prepare(overlayRoot, identity));
        }
    }

    @Test
    public void rejectsOverlaySourcePathThatShadowsImmutableNativeCore() throws Exception {
        ProfileIdentityLock identity = createIdentity("com.example.nativecorepath");
        Path overlayRoot = newOverlay("native-core-path-overlay");
        write(overlayRoot.resolve("cpp/profile-private/dpt_crypto.cpp"),
                "int profile_private_helper() noexcept { return 1; }\n");

        assertRejected("不可变 Native 核心源码", () -> prepare(overlayRoot, identity));
    }

    @Test
    public void rejectsGeneratedNativeEntryContractSymbols() throws Exception {
        ProfileIdentityLock identity = createIdentity("com.example.nativeentrycontract");
        Path overlayRoot = newOverlay("native-entry-contract-overlay");
        write(overlayRoot.resolve("cpp/startup-coordination/entry_attempt.cpp"),
                "void register_profile() { DPT_PROFILE_JNI_TABLE_ACCESSOR(); }\n");

        assertRejected("不可变 Native 核心", () -> prepare(overlayRoot, identity));

        Path buildModeOverlay = newOverlay("native-build-mode-overlay");
        write(buildModeOverlay.resolve("cpp/startup-coordination/build_mode_attempt.cpp"),
                "#if DPT_PROFILE_BUILD\nint profile_mode() { return 1; }\n#endif\n");

        assertRejected("不可变 Native 核心", () -> prepare(buildModeOverlay, identity));

        Path configAssetOverlay = newOverlay("native-config-asset-overlay");
        write(configAssetOverlay.resolve("cpp/path-config/config_asset_attempt.cpp"),
                "const char *config_asset = DPT_PROFILE_SHELL_CONFIG_ASSET;\n");

        assertRejected("不可变 Native 核心", () -> prepare(configAssetOverlay, identity));

        Path startupOverlay = newOverlay("native-startup-coordinator-overlay");
        write(startupOverlay.resolve("cpp/startup-coordination/startup_attempt.cpp"),
                "void startup_attempt() { DPT_PROFILE_STARTUP_COORDINATOR(0u); }\n");

        assertRejected("不可变 Native 核心", () -> prepare(startupOverlay, identity));

        Path runtimeConfigOverlay = newOverlay("native-runtime-config-overlay");
        write(runtimeConfigOverlay.resolve("cpp/path-config/runtime_config_attempt.cpp"),
                "const auto &config = DPT_PROFILE_RUNTIME_CONFIG_ACCESSOR();\n"
                        + "const char *entry = config."
                        + "DPT_PROFILE_RUNTIME_SHELL_CONFIG_FIELD;\n");

        assertRejected("不可变 Native 核心", () -> prepare(runtimeConfigOverlay, identity));
    }

    @Test
    public void rejectsNetworkAndRemoteDynamicCode() throws Exception {
        ProfileIdentityLock identity = createIdentity("com.example.remote");
        Path networkOverlay = newOverlay("network-overlay");
        writeJava(networkOverlay, identity, "NetworkHelper.java",
                "import java.net.URL;\nfinal class NetworkHelper { URL endpoint; }\n");
        assertRejected("网络", () -> prepare(networkOverlay, identity));

        Path dynamicOverlay = newOverlay("dynamic-overlay");
        writeJava(dynamicOverlay, identity, "DynamicHelper.java",
                "import dalvik.system.DexClassLoader;\nfinal class DynamicHelper {}\n");
        assertRejected("远程动态代码", () -> prepare(dynamicOverlay, identity));
    }

    @Test
    public void rejectsSymbolicLinksInsteadOfOmittingThemFromDigest() throws Exception {
        ProfileIdentityLock identity = createIdentity("com.example.symlink");
        Path overlayRoot = newOverlay("symlink-overlay");
        Path external = temporaryFolder.newFolder("external-overlay-source").toPath();
        write(external.resolve("External.java"), "package " + identity.getShellPackageName()
                + ";\nfinal class External {}\n");
        Path javaRoot = overlayRoot.resolve("java");
        Files.createDirectories(javaRoot);
        Files.createSymbolicLink(javaRoot.resolve("profile-private"), external);

        assertRejected("符号链接", () -> prepare(overlayRoot, identity));
    }

    private ProfileOverlayProcessor.PreparedOverlay prepare(Path overlayRoot,
                                                            ProfileIdentityLock identity)
            throws Exception {
        return new ProfileOverlayProcessor().prepare(overlayRoot, identity);
    }

    private ProfileIdentityLock createIdentity(String applicationId) throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("identity-"
                + applicationId.substring(applicationId.lastIndexOf('.') + 1)).toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot)
                .create(applicationId);
        return new ProfileIdentityStore(profilesRoot).loadExisting(applicationId,
                created.getProfileId());
    }

    private Path newOverlay(String name) throws Exception {
        return temporaryFolder.newFolder(name).toPath();
    }

    private static ProfileRecipeTemplate recipeATemplate() {
        ProfileRecipe recipe = ProfileRecipeRegistry.builtIn().requireVerified(
                ProfileRecipeRegistry.RECIPE_A, ProfileSchemaVersions.PROFILE_PROTOCOL);
        return ProfileRecipeTemplateRegistry.builtIn().requireFor(recipe);
    }

    private static void writeJava(Path overlayRoot, ProfileIdentityLock identity, String fileName,
                                  String body) throws Exception {
        write(overlayRoot.resolve("java/profile-private").resolve(javaRelative(identity, fileName)),
                "package " + identity.getShellPackageName() + ";\n" + body);
    }

    private static Path javaRelative(ProfileIdentityLock identity, String fileName) {
        return Path.of(identity.getShellPackageName().replace('.', '/')).resolve(fileName);
    }

    private static void write(Path path, String content) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static void assertRejected(String expectedMessage, ThrowingRunnable action)
            throws Exception {
        try {
            action.run();
            Assert.fail("Expected Overlay rejection: " + expectedMessage);
        } catch (ProfileOverlayException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(expectedMessage));
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
