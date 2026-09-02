package com.luoye.dpt.profile.generation;

import com.luoye.dpt.profile.generation.overlay.ProfileOverlayProcessor;
import com.luoye.dpt.profile.generation.recipe.ProfileRecipe;
import com.luoye.dpt.profile.generation.recipe.ProfileRecipeRegistry;
import com.luoye.dpt.profile.generation.recipe.ProfileRecipeTemplate;
import com.luoye.dpt.profile.generation.recipe.ProfileRecipeTemplateRegistry;
import com.luoye.dpt.profile.generation.overlay.ProfileOverlayException;
import com.luoye.dpt.profile.model.JniMethodMapping;
import com.luoye.dpt.profile.model.ProfileIdentityLock;
import com.luoye.dpt.profile.model.ProfileSchemaVersions;
import com.luoye.dpt.profile.storage.ProfileCreationResult;
import com.luoye.dpt.profile.storage.ProfileCreationService;
import com.luoye.dpt.profile.storage.ProfileIdentityStore;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Native Profile contract checks that do not require a device or an installed NDK.
 * The checks intentionally inspect the generated arm64 inputs and immutable entry source.
 */
public class ProfileNativeArm64ContractTest {

    private static final String ARM64_ABI = "arm64-v8a";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void generatedNativeContractDeclaresOnlyArm64() throws Exception {
        ProfileIdentityLock identity = createIdentity("com.example.arm64.contract");
        ProfileCodeGenerator.GenerationLayout layout = new ProfileCodeGenerator(
                temporaryFolder.newFolder("arm64-generated").toPath())
                .generate(identity, "r000001", template(ProfileRecipeRegistry.RECIPE_A));

        String config = Files.readString(layout.getCppDirectory().resolve("profile_config.h"));
        String parameters = Files.readString(
                layout.getCmakeDirectory().resolve("profile-parameters.cmake"));
        Assert.assertTrue(config.contains("#define DPT_PROFILE_ABI \"" + ARM64_ABI + "\""));
        Assert.assertTrue(parameters.contains("set(DPT_PROFILE_ABI \"" + ARM64_ABI + "\")"));
        Assert.assertFalse(parameters.contains("armeabi-v7a"));
        Assert.assertFalse(parameters.contains("x86_64"));
    }

    @Test
    public void generatedNativeContractBindsJunkClassToProfileIdentity() throws Exception {
        ProfileIdentityLock identity = createIdentity("com.example.arm64.junk");
        ProfileCodeGenerator.GenerationLayout layout = new ProfileCodeGenerator(
                temporaryFolder.newFolder("junk-generated").toPath())
                .generate(identity, "r000001", template(ProfileRecipeRegistry.RECIPE_A));

        String config = Files.readString(layout.getCppDirectory().resolve("profile_config.h"));
        String profileJunkClass = identity.getJunkClassName().replace('.', '/');
        Assert.assertTrue("Profile contract must expose the generated junk class",
                config.contains(profileJunkClass));
        Assert.assertTrue("Profile contract must define a Native junk-class accessor",
                config.contains("#define DPT_PROFILE_JUNK_CLASS"));

        String coreSource = readProjectFile("shell/src/main/cpp/dpt.cpp");
        String riskSource = readProjectFile("shell/src/main/cpp/dpt_risk.cpp");
        String hookSource = readProjectFile("shell/src/main/cpp/dpt_hook.cpp");
        Assert.assertTrue("Profile runtime must expose the generated junk class",
                coreSource.contains("DPT_PROFILE_JUNK_CLASS")
                        && coreSource.contains("getJunkClassName()"));
        Assert.assertTrue("Native junk protection must consume the Profile class",
                riskSource.contains("getJunkClassName()"));
        Assert.assertTrue("ART hook junk check must consume the Profile class",
                hookSource.contains("getJunkClassName()"));
    }

    @Test
    public void junkClassDescriptorGuardAllowsExactProfileClassOnly() throws Exception {
        String source = readProjectFile("shell/src/main/cpp/dpt_hook.cpp");
        String matcher = section(source, "isExactJunkClassDescriptor",
                "DPT_ENCRYPT void patchClass");
        String patch = section(source, "DPT_ENCRYPT void patchClass",
                "if(LIKELY(dex_file != nullptr))");

        Assert.assertTrue("Junk descriptor matching must require the JVM descriptor prefix",
                matcher.contains("descriptor[0] == 'L'"));
        Assert.assertTrue("Junk descriptor matching must require the descriptor terminator",
                matcher.contains("descriptor[descriptorLength - 1] == ';'"));
        Assert.assertTrue("Junk descriptor matching must compare the complete class name",
                matcher.contains("dpt_memcmp(descriptor + 1, junkClassName, junkClassLength)"));
        Assert.assertTrue("Suffix checks must not index a short descriptor",
                patch.contains("descriptorLength >= 2"));
        Assert.assertTrue("The exact generated Profile class must bypass the suffix trap",
                patch.contains("!isExactJunkClassDescriptor(descriptor, junkClassName)"));
        Assert.assertTrue("Non-exact junk descriptors must retain the digit suffix guard",
                patch.contains("if(isdigit(ch)"));
    }

    @Test
    public void profileConfigDecryptionUsesRuntimeKeyAndAtomicCommit() throws Exception {
        String source = readProjectFile("shell/src/main/cpp/dpt.cpp");
        String reader = section(source, "DPT_ENCRYPT bool read_shell_config",
                "void veritySignature");

        Assert.assertTrue(reader.contains("hmac_sha256(DPT_RUNTIME_KEY_SLOT"));
        Assert.assertTrue(reader.contains("memcpy(iv, DPT_RUNTIME_KEY_SLOT, 16)"));
        Assert.assertTrue(reader.contains("aes_cbc_decrypt(aes_key.data(), 256"));
        Assert.assertTrue(reader.contains("ShellConfig loaded_config"));
        Assert.assertTrue(reader.contains("g_shell_config = std::move(loaded_config)"));
        assertInOrder(reader,
                "const char *shellConfigEntry = getShellConfigEntryName()",
                "const char *buildKey = getShellBuildKey()",
                "hmac_sha256(DPT_RUNTIME_KEY_SLOT",
                "memcpy(iv, DPT_RUNTIME_KEY_SLOT, 16)",
                "aes_cbc_decrypt",
                "g_shell_config = std::move(loaded_config)");

        String profileBranch = section(reader, "#if DPT_PROFILE_BUILD", "#else");
        Assert.assertFalse(profileBranch.contains("DPT_UNKNOWN_DATA"));
    }

    @Test
    public void profileJniRegistrationUsesGeneratedTableAndFixedMappings() throws Exception {
        ProfileIdentityLock identity = createIdentity("com.example.arm64.jni");
        ProfileCodeGenerator.GenerationLayout layout = new ProfileCodeGenerator(
                temporaryFolder.newFolder("jni-generated").toPath())
                .generate(identity, "r000001", template(ProfileRecipeRegistry.RECIPE_A));
        String registry = Files.readString(
                layout.getCppDirectory().resolve("profile_jni_registry.cpp"));
        for (JniMethodMapping mapping : identity.getJniMethods()) {
            Assert.assertTrue(registry.contains("\"" + mapping.getMethodName() + "\""));
            Assert.assertTrue(registry.contains("\"" + mapping.getJvmSignature() + "\""));
        }
        Assert.assertTrue(registry.contains("std::size_t"));
        Assert.assertTrue(registry.contains("sizeof("));

        String source = readProjectFile("shell/src/main/cpp/dpt.cpp");
        String registration = section(source, "DPT_ENCRYPT static bool registerNativeMethods",
                "DPT_ENCRYPT void init_app");
        String profileBranch = section(registration, "#if DPT_PROFILE_BUILD", "#else");
        Assert.assertTrue(profileBranch.contains("DPT_PROFILE_JNI_BRIDGE"));
        Assert.assertTrue(profileBranch.contains("DPT_PROFILE_JNI_TABLE_ACCESSOR()"));
        Assert.assertTrue(profileBranch.contains("DPT_PROFILE_JNI_COUNT_ACCESSOR()"));
        Assert.assertFalse(profileBranch.contains("gMethods"));
        Assert.assertTrue(registration.contains("RegisterNatives"));
    }

    @Test
    public void profilePathsAndJniOnLoadOrderRemainStable() throws Exception {
        String core = readProjectFile("shell/src/main/cpp/dpt.cpp");
        String util = readProjectFile("shell/src/main/cpp/dpt_util.cpp");
        String hook = readProjectFile("shell/src/main/cpp/dpt_hook.cpp");

        for (String accessor : Arrays.asList("getShellConfigEntryName()",
                "getCodeItemEntryName()", "getShellBuildKey()", "getShellSoName()")) {
            Assert.assertTrue(accessor, core.contains(accessor));
        }
        Assert.assertTrue(util.contains("getRuntimeDirectoryName()"));
        Assert.assertTrue(util.contains("getDexAssetFileName()"));
        Assert.assertTrue(hook.contains("getDexAssetFileName()"));

        String onLoad = section(core, "JNIEXPORT jint JNI_OnLoad",
                "JNIEXPORT void JNI_OnUnload");
        assertInOrder(onLoad,
                "READ_PROFILE_CONFIGURATION",
                "read_shell_config(env)",
                "RUN_EXISTING_RISK_CHECKS",
                "antiRisk()",
                "REGISTER_PROFILE_JNI",
                "registerNativeMethods(env)");
    }

    @Test
    public void generatedNativeSourcesHaveNoKnownMarkersAndOverlayBoundaryIsEnforced()
            throws Exception {
        ProfileIdentityLock identity = createIdentity("com.example.arm64.overlay");
        Path overlayRoot = temporaryFolder.newFolder("valid-overlay").toPath();
        write(overlayRoot.resolve("cpp/profile-private/private_helper.cpp"),
                "int profile_private_helper(int value) noexcept { return value + 1; }\n");
        ProfileOverlayProcessor.PreparedOverlay prepared = new ProfileOverlayProcessor()
                .prepare(overlayRoot, identity);
        ProfileCodeGenerator.GenerationLayout layout = new ProfileCodeGenerator(
                temporaryFolder.newFolder("overlay-generated").toPath())
                .generate(identity, "r000001", template(ProfileRecipeRegistry.RECIPE_B), prepared);
        String generated = readAll(layout.getCppDirectory());
        for (String marker : Arrays.asList("com/luoye", "com/luoyesiqiu", "MainActivity",
                "ProxyApplication", "ProxyComponentFactory", "JniBridge", "DPT_UNKNOWN_DATA",
                "d_shell_data_001", "i11111i111.zip", "OoooooOooo", "vwwwwwvwww")) {
            Assert.assertFalse("unexpected marker: " + marker, generated.contains(marker));
        }
        Assert.assertTrue(Files.isRegularFile(
                layout.getCppDirectory().resolve("private_helper.cpp")));
        String sources = Files.readString(
                layout.getCmakeDirectory().resolve("profile-sources.cmake"));
        Assert.assertTrue("Native Overlay C++ must be part of the explicit CMake source list",
                sources.contains("private_helper.cpp"));

        Path forbiddenSymbol = temporaryFolder.newFolder("forbidden-symbol").toPath();
        write(forbiddenSymbol.resolve("cpp/profile-private/entry.cpp"),
                "jint JNI_OnLoad(JavaVM *vm, void *) { return JNI_VERSION_1_6; }\n");
        assertRejected("禁用 API/符号", () -> new ProfileOverlayProcessor()
                .prepare(forbiddenSymbol, identity));

        Path forbiddenPath = temporaryFolder.newFolder("forbidden-path").toPath();
        write(forbiddenPath.resolve("cpp/profile-private/dpt_crypto.cpp"),
                "int profile_private_helper() noexcept { return 1; }\n");
        assertRejected("不可变 Native 核心源码", () -> new ProfileOverlayProcessor()
                .prepare(forbiddenPath, identity));

        Path forbiddenAbi = temporaryFolder.newFolder("forbidden-abi").toPath();
        write(forbiddenAbi.resolve("cpp/path-config/abi_attempt.cpp"),
                "const char *abi = DPT_PROFILE_ABI;\n");
        assertRejected("不可变 Native 核心", () -> new ProfileOverlayProcessor()
                .prepare(forbiddenAbi, identity));
    }

    private ProfileIdentityLock createIdentity(String applicationId) throws Exception {
        Path profilesRoot = temporaryFolder.newFolder(
                "identity-" + applicationId.substring(applicationId.lastIndexOf('.') + 1))
                .toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot)
                .create(applicationId);
        return new ProfileIdentityStore(profilesRoot).loadExisting(applicationId,
                created.getProfileId());
    }

    private static ProfileRecipeTemplate template(String recipeId) {
        ProfileRecipe recipe = ProfileRecipeRegistry.builtIn().requireVerified(recipeId,
                ProfileSchemaVersions.PROFILE_PROTOCOL);
        return ProfileRecipeTemplateRegistry.builtIn().requireFor(recipe);
    }

    private static String readProjectFile(String relativePath) throws Exception {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate);
            }
            current = current.getParent();
        }
        throw new IllegalStateException("dpt-shell project root not found: " + relativePath);
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

    private static String readAll(Path directory) throws Exception {
        StringBuilder result = new StringBuilder();
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path file : paths.filter(Files::isRegularFile).sorted()
                    .collect(Collectors.toList())) {
                result.append(Files.readString(file));
            }
        }
        return result.toString();
    }

    private static void write(Path path, String content) throws Exception {
        Files.createDirectories(path.getParent());
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
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
