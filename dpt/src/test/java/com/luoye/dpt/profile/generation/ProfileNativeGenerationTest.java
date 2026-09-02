package com.luoye.dpt.profile.generation;

import com.luoye.dpt.profile.generation.recipe.ProfileRecipe;
import com.luoye.dpt.profile.generation.recipe.ProfileRecipeRegistry;
import com.luoye.dpt.profile.generation.recipe.ProfileRecipeTemplate;
import com.luoye.dpt.profile.generation.recipe.ProfileRecipeTemplateRegistry;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProfileNativeGenerationTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void generatesConfigNamespaceJniRegistryVariableSectionAndCmakeManifest()
            throws Exception {
        ProfileIdentityLock identity = createIdentity("com.example.alpha");
        ProfileCodeGenerator.GenerationLayout layout = new ProfileCodeGenerator(
                temporaryFolder.newFolder("generated").toPath())
                .generate(identity, "r000001", template(ProfileRecipeRegistry.RECIPE_A));

        Path configHeader = layout.getCppDirectory().resolve("profile_config.h");
        Path registrySource = layout.getCppDirectory().resolve("profile_jni_registry.cpp");
        Path variableSource = layout.getCppDirectory().resolve("profile_variable.cpp");
        Path parameters = layout.getCmakeDirectory().resolve("profile-parameters.cmake");
        Path sources = layout.getCmakeDirectory().resolve("profile-sources.cmake");
        Assert.assertTrue(Files.isRegularFile(configHeader));
        Assert.assertTrue(Files.isRegularFile(registrySource));
        Assert.assertTrue(Files.isRegularFile(variableSource));
        Assert.assertTrue(Files.isRegularFile(parameters));
        Assert.assertTrue(Files.isRegularFile(sources));

        String config = Files.readString(configHeader);
        Assert.assertTrue(config.contains(identity.getSoName()));
        Assert.assertTrue(config.contains(identity.getJniBridgeClassName().replace('.', '/')));
        Assert.assertTrue(config.contains(identity.getBuildKey()));
        Assert.assertTrue(config.contains(identity.getShellConfigAssetName()));
        Assert.assertTrue(config.matches("(?s).*namespace n[0-9a-f]{12}.*"));
        Assert.assertTrue(config.contains("#define DPT_PROFILE_JNI_TABLE_ACCESSOR"));
        Assert.assertTrue(config.contains("#define DPT_PROFILE_JNI_COUNT_ACCESSOR"));
        Assert.assertTrue(config.contains("#define DPT_PROFILE_RUNTIME_DIR"));

        String registry = Files.readString(registrySource);
        for (JniMethodMapping mapping : identity.getJniMethods()) {
            Assert.assertTrue(mapping.getSemanticId(), registry.contains("\""
                    + mapping.getMethodName() + "\""));
            Assert.assertTrue(mapping.getSemanticId(), registry.contains("\""
                    + mapping.getJvmSignature() + "\""));
        }
        Assert.assertTrue(registry.contains("callRealApplicationOnCreate"));
        Assert.assertTrue(registry.contains("getCompressedDexesPathExport"));
        Assert.assertTrue(registry.contains("replaceApplication"));

        String variable = Files.readString(variableSource);
        Assert.assertTrue(variable.contains(identity.getRc4KeySymbol()));
        Assert.assertTrue(variable.matches("(?s).*section\\(\"\\.s[0-9a-f]{12}\"\\).*"));
        Assert.assertFalse(variable.contains("DPT_UNKNOWN_DATA"));
        Assert.assertFalse(variable.contains("1234567890abcdef"));

        String cmakeParameters = Files.readString(parameters);
        Assert.assertTrue(cmakeParameters.contains(identity.getSoName()));
        Assert.assertTrue(cmakeParameters.contains(identity.getBuildKey()));
        Assert.assertTrue(cmakeParameters.contains(identity.getJniBridgeClassName()
                .replace('.', '/')));
        Assert.assertTrue(cmakeParameters.contains(identity.getRc4KeySymbol()));
        Assert.assertTrue(cmakeParameters.contains("DPT_PROFILE_JNI_TABLE_ACCESSOR"));
        String cmakeSources = Files.readString(sources);
        List<Path> cppSources;
        try (Stream<Path> paths = Files.list(layout.getCppDirectory())) {
            cppSources = paths.filter(path -> path.toString().endsWith(".cpp"))
                    .collect(Collectors.toList());
        }
        Assert.assertTrue(cppSources.toString(), cppSources.size() >= 4);
        for (Path source : cppSources) {
            Assert.assertTrue(source.toString(), cmakeSources.contains(source.getFileName().toString()));
        }

        String allNative = readAll(layout.getCppDirectory());
        Assert.assertFalse(allNative.contains("com/luoyesiqiu"));
        Assert.assertFalse(allNative.contains("DPT_UNKNOWN_DATA"));
        Assert.assertFalse(allNative.contains("d_shell_data_001"));
        Assert.assertFalse(allNative.contains("i11111i111.zip"));
    }

    @Test
    public void recipesAndProfilesGenerateDifferentNativeUnits() throws Exception {
        ProfileIdentityLock alpha = createIdentity("com.example.alpha");
        ProfileIdentityLock beta = createIdentity("com.example.beta");

        ProfileCodeGenerator.GenerationLayout alphaA = new ProfileCodeGenerator(
                temporaryFolder.newFolder("alpha-a").toPath())
                .generate(alpha, "r000001", template(ProfileRecipeRegistry.RECIPE_A));
        ProfileCodeGenerator.GenerationLayout alphaB = new ProfileCodeGenerator(
                temporaryFolder.newFolder("alpha-b").toPath())
                .generate(alpha, "r000001", template(ProfileRecipeRegistry.RECIPE_B));
        ProfileCodeGenerator.GenerationLayout betaA = new ProfileCodeGenerator(
                temporaryFolder.newFolder("beta-a").toPath())
                .generate(beta, "r000001", template(ProfileRecipeRegistry.RECIPE_A));

        Assert.assertNotEquals(alphaA.getGeneratedSourceSha256(),
                alphaB.getGeneratedSourceSha256());
        Assert.assertNotEquals(alphaA.getGeneratedSourceSha256(),
                betaA.getGeneratedSourceSha256());
        Assert.assertNotEquals(cppFileNames(alphaA), cppFileNames(alphaB));
    }

    @Test
    public void recipesConnectDifferentNativeStartupGraphsToOneStableContract()
            throws Exception {
        ProfileIdentityLock identity = createIdentity("com.example.native.startup");
        ProfileRecipeTemplate linearTemplate = template(ProfileRecipeRegistry.RECIPE_A);
        ProfileRecipeTemplate phasedTemplate = template(ProfileRecipeRegistry.RECIPE_B);
        ProfileCodeGenerator.GenerationLayout linear = new ProfileCodeGenerator(
                temporaryFolder.newFolder("native-linear").toPath())
                .generate(identity, "r000001", linearTemplate);
        ProfileCodeGenerator.GenerationLayout phased = new ProfileCodeGenerator(
                temporaryFolder.newFolder("native-phased").toPath())
                .generate(identity, "r000001", phasedTemplate);

        String namespace = derive(identity, "native-namespace", "n");
        String coordinator = derive(identity, "native-startup-coordinator", "m");
        String coordinatorMacro = "#define DPT_PROFILE_STARTUP_COORDINATOR "
                + namespace + "::" + coordinator;
        Assert.assertTrue(Files.readString(linear.getCppDirectory().resolve("profile_config.h"))
                .contains(coordinatorMacro));
        Assert.assertTrue(Files.readString(phased.getCppDirectory().resolve("profile_config.h"))
                .contains(coordinatorMacro));

        String coordinatorDefinition = "void " + coordinator
                + "(std::uint32_t stage) noexcept {";
        Path linearCoordinator = requireSourceContaining(linear, coordinatorDefinition);
        Path phasedCoordinator = requireSourceContaining(phased, coordinatorDefinition);
        String linearSource = Files.readString(linearCoordinator);
        String phasedSource = Files.readString(phasedCoordinator);
        Assert.assertTrue(linearSource.contains("volatile std::uint32_t observed"));
        Assert.assertFalse(linearSource.contains("switch (stage)"));
        Assert.assertTrue(phasedSource.contains("switch (stage)"));

        String configRiskHelper = derive(identity,
                "native-startup-helper-" + ProfileRecipeRegistry.RECIPE_B
                        + "-startup-config-risk-phase", "h");
        String registrationHelper = derive(identity,
                "native-startup-helper-" + ProfileRecipeRegistry.RECIPE_B
                        + "-startup-registration-phase", "h");
        Path configRiskSource = requireSourceContaining(phased,
                "std::uint32_t " + configRiskHelper
                        + "(std::uint32_t stage) noexcept {");
        Path registrationSource = requireSourceContaining(phased,
                "std::uint32_t " + registrationHelper
                        + "(std::uint32_t stage) noexcept {");
        Assert.assertNotEquals(phasedCoordinator, configRiskSource);
        Assert.assertNotEquals(phasedCoordinator, registrationSource);
        Assert.assertNotEquals(configRiskSource, registrationSource);
        Assert.assertTrue(phasedSource.contains(configRiskHelper + "(stage)"));
        Assert.assertTrue(phasedSource.contains(registrationHelper + "(stage)"));
        Assert.assertTrue(cppFileNames(phased).size() > cppFileNames(linear).size());

        String phasedCmake = Files.readString(
                phased.getCmakeDirectory().resolve("profile-sources.cmake"));
        Assert.assertTrue(phasedCmake.contains(phasedCoordinator.getFileName().toString()));
        Assert.assertTrue(phasedCmake.contains(configRiskSource.getFileName().toString()));
        Assert.assertTrue(phasedCmake.contains(registrationSource.getFileName().toString()));
    }

    @Test
    public void recipesGenerateDifferentPathConfigAdaptersWithEquivalentRuntimeValues()
            throws Exception {
        ProfileIdentityLock identity = createIdentity("com.example.native.paths");
        ProfileCodeGenerator.GenerationLayout direct = new ProfileCodeGenerator(
                temporaryFolder.newFolder("native-path-direct").toPath())
                .generate(identity, "r000001", template(ProfileRecipeRegistry.RECIPE_A));
        ProfileCodeGenerator.GenerationLayout adapted = new ProfileCodeGenerator(
                temporaryFolder.newFolder("native-path-adapted").toPath())
                .generate(identity, "r000001", template(ProfileRecipeRegistry.RECIPE_B));

        String directType = derive(identity,
                "native-runtime-config-type-" + ProfileRecipeRegistry.RECIPE_A, "p");
        String adaptedType = derive(identity,
                "native-runtime-config-type-" + ProfileRecipeRegistry.RECIPE_B, "p");
        String directAccessor = derive(identity,
                "native-runtime-config-accessor-" + ProfileRecipeRegistry.RECIPE_A, "m");
        String adaptedAccessor = derive(identity,
                "native-runtime-config-accessor-" + ProfileRecipeRegistry.RECIPE_B, "m");
        Assert.assertNotEquals(directType, adaptedType);
        Assert.assertNotEquals(directAccessor, adaptedAccessor);

        String directConfig = Files.readString(
                direct.getCppDirectory().resolve("profile_config.h"));
        String adaptedConfig = Files.readString(
                adapted.getCppDirectory().resolve("profile_config.h"));
        for (String config : new String[]{directConfig, adaptedConfig}) {
            Assert.assertTrue(config.contains("#define DPT_PROFILE_RUNTIME_CONFIG_TYPE"));
            Assert.assertTrue(config.contains("#define DPT_PROFILE_RUNTIME_CONFIG_ACCESSOR"));
            Assert.assertTrue(config.contains(
                    "#define DPT_PROFILE_RUNTIME_SHELL_CONFIG_FIELD"));
            Assert.assertTrue(config.contains(
                    "#define DPT_PROFILE_RUNTIME_CODE_ITEM_FIELD"));
            Assert.assertTrue(config.contains(
                    "#define DPT_PROFILE_RUNTIME_DEX_ASSET_FIELD"));
            Assert.assertTrue(config.contains(
                    "#define DPT_PROFILE_RUNTIME_DIRECTORY_FIELD"));
            Assert.assertTrue(config.contains(
                    "#define DPT_PROFILE_RUNTIME_BUILD_KEY_FIELD"));
            Assert.assertTrue(config.contains(
                    "assets/" + identity.getShellConfigAssetName()));
            Assert.assertTrue(config.contains(
                    "assets/" + identity.getCodeItemAssetName()));
            Assert.assertTrue(config.contains(identity.getDexAssetName()));
            Assert.assertTrue(config.contains(identity.getRuntimeDirName()));
            Assert.assertTrue(config.contains(identity.getBuildKey()));
        }
        Assert.assertTrue(directConfig.contains("struct " + directType));
        Assert.assertTrue(adaptedConfig.contains("struct " + adaptedType));

        Path directSource = requireSourceContaining(direct,
                "const " + directType + "& " + directAccessor + "() noexcept {");
        String directSourceText = Files.readString(directSource);
        assertInOrder(directSourceText,
                "DPT_PROFILE_SHELL_CONFIG_ASSET",
                "DPT_PROFILE_CODE_ITEM_ASSET",
                "DPT_PROFILE_DEX_ASSET",
                "DPT_PROFILE_RUNTIME_DIR",
                "DPT_PROFILE_BUILD_KEY");

        String contextType = derive(identity,
                "native-path-context-type-" + ProfileRecipeRegistry.RECIPE_B, "q");
        String contextAccessor = derive(identity,
                "native-path-context-accessor-" + ProfileRecipeRegistry.RECIPE_B, "m");
        String shellField = derive(identity,
                "native-path-context-field-shell-config-"
                        + ProfileRecipeRegistry.RECIPE_B, "f");
        String codeItemField = derive(identity,
                "native-path-context-field-code-item-"
                        + ProfileRecipeRegistry.RECIPE_B, "f");
        String dexField = derive(identity,
                "native-path-context-field-dex-asset-"
                        + ProfileRecipeRegistry.RECIPE_B, "f");
        String runtimeField = derive(identity,
                "native-path-context-field-runtime-dir-"
                        + ProfileRecipeRegistry.RECIPE_B, "f");
        String buildKeyField = derive(identity,
                "native-path-context-field-build-key-"
                        + ProfileRecipeRegistry.RECIPE_B, "f");
        Path contextHeader = requireHeaderContaining(adapted, "struct " + contextType);
        String contextHeaderText = Files.readString(contextHeader);
        assertInOrder(contextHeaderText, runtimeField, buildKeyField, codeItemField,
                shellField, dexField);

        Path contextSource = requireSourceContaining(adapted,
                "const " + contextType + "& " + contextAccessor + "() noexcept {");
        String contextSourceText = Files.readString(contextSource);
        assertInOrder(contextSourceText,
                "DPT_PROFILE_RUNTIME_DIR",
                "DPT_PROFILE_BUILD_KEY",
                "DPT_PROFILE_CODE_ITEM_ASSET",
                "DPT_PROFILE_SHELL_CONFIG_ASSET",
                "DPT_PROFILE_DEX_ASSET");

        Path adapterSource = requireSourceContaining(adapted,
                "const " + adaptedType + "& " + adaptedAccessor + "() noexcept {");
        Assert.assertNotEquals(contextSource, adapterSource);
        String adapterSourceText = Files.readString(adapterSource);
        assertInOrder(adapterSourceText,
                "context." + shellField,
                "context." + codeItemField,
                "context." + dexField,
                "context." + runtimeField,
                "context." + buildKeyField);
    }

    @Test
    public void generatedNativeIdentityControlsRc4SlotSoNameAndExportVisibility() throws Exception {
        ProfileIdentityLock identity = createIdentity("com.example.native.identity");
        ProfileCodeGenerator.GenerationLayout layout = new ProfileCodeGenerator(
                temporaryFolder.newFolder("native-identity-generated").toPath())
                .generate(identity, "r000001", template(ProfileRecipeRegistry.RECIPE_A));

        String config = Files.readString(layout.getCppDirectory().resolve("profile_config.h"));
        Assert.assertTrue(config.contains("#define DPT_PROFILE_SO_NAME"));
        Assert.assertTrue(config.contains("#define DPT_PROFILE_RC4_KEY_SYMBOL"));
        Assert.assertTrue(config.contains("#define DPT_PROFILE_VARIABLE_SECTION"));
        Assert.assertTrue(config.contains(identity.getSoName()));
        Assert.assertTrue(config.contains(identity.getRc4KeySymbol()));

        String variable = Files.readString(layout.getCppDirectory()
                .resolve("profile_variable.cpp"));
        Assert.assertTrue(variable.contains("extern \"C\""));
        Assert.assertTrue(variable.contains(identity.getRc4KeySymbol()));
        Assert.assertTrue(variable.contains("visibility(\"default\")"));
        Assert.assertTrue(variable.contains("section(\".s"));

        String directType = derive(identity,
                "native-runtime-config-type-" + ProfileRecipeRegistry.RECIPE_A, "p");
        String directAccessor = derive(identity,
                "native-runtime-config-accessor-" + ProfileRecipeRegistry.RECIPE_A, "m");
        String directSource = Files.readString(requireSourceContaining(layout,
                "const " + directType + "& " + directAccessor + "() noexcept {"));
        Assert.assertTrue(directSource.contains("namespace n"));
        Assert.assertFalse(directSource.contains("visibility(\"default\")"));

        String parameters = Files.readString(layout.getCmakeDirectory()
                .resolve("profile-parameters.cmake"));
        Assert.assertTrue(parameters.contains("DPT_PROFILE_SO_FILE"));
        Assert.assertTrue(parameters.contains("DPT_PROFILE_RC4_KEY_SYMBOL"));
        Assert.assertTrue(parameters.contains("DPT_PROFILE_VARIABLE_SECTION"));
        Assert.assertTrue(parameters.contains(identity.getSoName()));
        Assert.assertTrue(parameters.contains(identity.getRc4KeySymbol()));
    }

    private ProfileIdentityLock createIdentity(String applicationId) throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("identity-"
                + applicationId.substring(applicationId.lastIndexOf('.') + 1)).toPath();
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

    private static List<String> cppFileNames(ProfileCodeGenerator.GenerationLayout layout)
            throws Exception {
        try (Stream<Path> paths = Files.list(layout.getCppDirectory())) {
            return paths.filter(path -> path.toString().endsWith(".cpp"))
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private static Path requireSourceContaining(ProfileCodeGenerator.GenerationLayout layout,
                                                String token) throws Exception {
        List<Path> matches;
        try (Stream<Path> paths = Files.list(layout.getCppDirectory())) {
            matches = paths.filter(path -> path.toString().endsWith(".cpp"))
                    .filter(path -> contains(path, token))
                    .collect(Collectors.toList());
        }
        Assert.assertEquals("Native 源码命中数量错误：" + token + " -> " + matches,
                1, matches.size());
        return matches.get(0);
    }

    private static Path requireHeaderContaining(ProfileCodeGenerator.GenerationLayout layout,
                                                String token) throws Exception {
        List<Path> matches;
        try (Stream<Path> paths = Files.list(layout.getCppDirectory())) {
            matches = paths.filter(path -> path.toString().endsWith(".h"))
                    .filter(path -> contains(path, token))
                    .collect(Collectors.toList());
        }
        Assert.assertEquals("Native 头文件命中数量错误：" + token + " -> " + matches,
                1, matches.size());
        return matches.get(0);
    }

    private static void assertInOrder(String source, String... tokens) {
        int previous = -1;
        for (String token : tokens) {
            int current = source.indexOf(token);
            Assert.assertTrue("Missing or out-of-order token: " + token,
                    current > previous);
            previous = current;
        }
    }

    private static boolean contains(Path path, String token) {
        try {
            return Files.readString(path).contains(token);
        } catch (Exception e) {
            throw new IllegalStateException("读取 Native 生成源码失败：" + path, e);
        }
    }

    private static String derive(ProfileIdentityLock identity, String label, String prefix) {
        return ProfileStableNameDeriver.identifier(identity.getIdentitySeed(), label,
                prefix, 12);
    }

    private static String readAll(Path directory) throws Exception {
        StringBuilder result = new StringBuilder();
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path file : paths.filter(Files::isRegularFile)
                    .sorted().collect(Collectors.toList())) {
                result.append(Files.readString(file));
            }
        }
        return result.toString();
    }
}
