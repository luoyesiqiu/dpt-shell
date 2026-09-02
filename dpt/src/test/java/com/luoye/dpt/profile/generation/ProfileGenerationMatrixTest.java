package com.luoye.dpt.profile.generation;

import com.luoye.dpt.profile.generation.overlay.ProfileOverlayProcessor;
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
import com.luoye.dpt.util.MessageDigestUtils;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProfileGenerationMatrixTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void sameIdentityRecipeAndOverlayGenerateIdenticalCompleteSourceTree() throws Exception {
        ProfileIdentityLock identity = createIdentity("com.example.stable");
        Path overlayRoot = temporaryFolder.newFolder("stable-overlay").toPath();
        Path javaRelative = Path.of(identity.getShellPackageName().replace('.', '/'))
                .resolve("StableOverlay.java");
        write(overlayRoot.resolve("java/profile-private").resolve(javaRelative),
                "package " + identity.getShellPackageName() + ";\n"
                        + "final class StableOverlay { int value() { return 11; } }\n");
        write(overlayRoot.resolve("cpp/path-config/stable_overlay.cpp"),
                "#include \"profile_config.h\"\n"
                        + "int stable_overlay_value() noexcept { return 11; }\n");
        ProfileOverlayProcessor.PreparedOverlay overlay = new ProfileOverlayProcessor()
                .prepare(overlayRoot, identity);
        ProfileRecipeTemplate recipe = template(ProfileRecipeRegistry.RECIPE_B);

        ProfileCodeGenerator.GenerationLayout first = new ProfileCodeGenerator(
                temporaryFolder.newFolder("stable-first").toPath())
                .generate(identity, "r000001", recipe, overlay);
        ProfileCodeGenerator.GenerationLayout second = new ProfileCodeGenerator(
                temporaryFolder.newFolder("stable-second").toPath())
                .generate(identity, "r000001", recipe, overlay);

        Assert.assertEquals(first.getGeneratedSourceSha256(), second.getGeneratedSourceSha256());
        Assert.assertEquals(sourceTree(first), sourceTree(second));
        Assert.assertTrue(Files.isRegularFile(first.getJavaDirectory().resolve(javaRelative)));
        Assert.assertTrue(Files.isRegularFile(first.getCppDirectory()
                .resolve("stable_overlay.cpp")));
        String cmakeSources = Files.readString(
                first.getCmakeDirectory().resolve("profile-sources.cmake"));
        Assert.assertTrue(cmakeSources.contains("stable_overlay.cpp"));
        Assert.assertFalse(cmakeSources.contains("StableOverlay.java"));
    }

    @Test
    public void differentProfilesUseDisjointStableIdentifiersAndDifferentCompleteSources()
            throws Exception {
        ProfileIdentityLock alpha = createIdentity("com.example.isolated.alpha");
        ProfileIdentityLock beta = createIdentity("com.example.isolated.beta");
        ProfileRecipeTemplate recipe = template(ProfileRecipeRegistry.RECIPE_A);

        ProfileCodeGenerator.GenerationLayout alphaLayout = new ProfileCodeGenerator(
                temporaryFolder.newFolder("isolated-alpha").toPath())
                .generate(alpha, "r000001", recipe);
        ProfileCodeGenerator.GenerationLayout betaLayout = new ProfileCodeGenerator(
                temporaryFolder.newFolder("isolated-beta").toPath())
                .generate(beta, "r000001", recipe);

        Set<String> alphaIdentifiers = stableIdentifiers(alpha);
        Set<String> betaIdentifiers = stableIdentifiers(beta);
        Assert.assertTrue(alphaIdentifiers.toString(),
                Collections.disjoint(alphaIdentifiers, betaIdentifiers));
        Assert.assertNotEquals(alphaLayout.getGeneratedSourceSha256(),
                betaLayout.getGeneratedSourceSha256());
        Assert.assertNotEquals(sourceTree(alphaLayout), sourceTree(betaLayout));

        String alphaSources = readAll(alphaLayout.getRevisionRoot());
        String betaSources = readAll(betaLayout.getRevisionRoot());
        Assert.assertTrue(alphaSources.contains(alpha.getShellPackageName()));
        Assert.assertTrue(alphaSources.contains(alpha.getSoName()));
        Assert.assertFalse(alphaSources.contains(beta.getShellPackageName()));
        Assert.assertFalse(alphaSources.contains(beta.getSoName()));
        Assert.assertTrue(betaSources.contains(beta.getShellPackageName()));
        Assert.assertTrue(betaSources.contains(beta.getSoName()));
        Assert.assertFalse(betaSources.contains(alpha.getShellPackageName()));
        Assert.assertFalse(betaSources.contains(alpha.getSoName()));
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

    private static Set<String> stableIdentifiers(ProfileIdentityLock identity) {
        List<String> identifiers = new ArrayList<>();
        identifiers.add(identity.getShellPackageName());
        identifiers.add(identity.getProxyApplicationClassName());
        identifiers.add(identity.getProxyComponentFactoryClassName());
        identifiers.add(identity.getJniBridgeClassName());
        identifiers.add(identity.getSoName());
        identifiers.add(identity.getShellConfigAssetName());
        identifiers.add(identity.getDexAssetName());
        identifiers.add(identity.getCodeItemAssetName());
        identifiers.add(identity.getNativeLibAssetDirName());
        identifiers.add(identity.getRuntimeDirName());
        identifiers.add(identity.getBuildKey());
        identifiers.add(identity.getRc4KeySymbol());
        identifiers.add(identity.getJunkClassName());
        identifiers.addAll(identity.getJniMethods().stream()
                .map(JniMethodMapping::getMethodName).collect(Collectors.toList()));
        return identifiers.stream().collect(Collectors.toSet());
    }

    private static Map<String, String> sourceTree(ProfileCodeGenerator.GenerationLayout layout)
            throws Exception {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(layout.getRevisionRoot())) {
            for (Path file : paths.filter(Files::isRegularFile).sorted()
                    .collect(Collectors.toList())) {
                String relative = layout.getRevisionRoot().relativize(file).toString()
                        .replace(file.getFileSystem().getSeparator(), "/");
                result.put(relative, MessageDigestUtils.sha256(Files.readAllBytes(file)));
            }
        }
        return result;
    }

    private static String readAll(Path root) throws Exception {
        StringBuilder result = new StringBuilder();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path file : paths.filter(Files::isRegularFile).sorted()
                    .collect(Collectors.toList())) {
                result.append(Files.readString(file, StandardCharsets.UTF_8));
            }
        }
        return result.toString();
    }

    private static void write(Path path, String content) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
