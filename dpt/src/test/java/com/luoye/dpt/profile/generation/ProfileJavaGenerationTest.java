package com.luoye.dpt.profile.generation;

import com.luoye.dpt.profile.contract.ProfileJniSemantic;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProfileJavaGenerationTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void generatesProfileSpecificEntrypointsBridgeHelpersConstantsAndMinimalR8Rules()
            throws Exception {
        Path identityRoot = temporaryFolder.newFolder("identity").toPath();
        ProfileCreationResult created = new ProfileCreationService(identityRoot)
                .create("com.example.alpha");
        ProfileIdentityLock identity = new ProfileIdentityStore(identityRoot)
                .loadExisting("com.example.alpha", created.getProfileId());
        Path generatedRoot = temporaryFolder.newFolder("generated").toPath();

        ProfileCodeGenerator.GenerationLayout layout = new ProfileCodeGenerator(generatedRoot)
                .generateJava(identity, "r000001", template(ProfileRecipeRegistry.RECIPE_A));

        Path applicationSource = sourcePath(layout, identity.getProxyApplicationClassName());
        Path componentFactorySource = sourcePath(layout,
                identity.getProxyComponentFactoryClassName());
        Path bridgeSource = sourcePath(layout, identity.getJniBridgeClassName());
        Assert.assertTrue(Files.isRegularFile(applicationSource));
        Assert.assertTrue(Files.isRegularFile(componentFactorySource));
        Assert.assertTrue(Files.isRegularFile(bridgeSource));
        String bridge = Files.readString(bridgeSource);
        for (JniMethodMapping mapping : identity.getJniMethods()) {
            Assert.assertTrue(mapping.getSemanticId(), bridge.contains("native ")
                    && bridge.contains(mapping.getMethodName() + "("));
            Assert.assertEquals(ProfileJniSemantic.fromSemanticId(mapping.getSemanticId())
                    .getJvmSignature(), mapping.getJvmSignature());
        }

        List<Path> javaSources;
        try (Stream<Path> paths = Files.walk(layout.getJavaDirectory())) {
            javaSources = paths.filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }
        Assert.assertTrue(javaSources.toString(), javaSources.size() >= 7);
        String allSources = readAll(javaSources);
        Assert.assertTrue(allSources.contains(identity.getSoName()));
        Assert.assertTrue(allSources.contains(identity.getShellConfigAssetName()));
        Assert.assertTrue(allSources.contains(identity.getRuntimeDirName()));
        Assert.assertFalse(allSources.contains("com.luoye"));
        Assert.assertFalse(allSources.contains("com.luoyesiqiu"));
        Assert.assertFalse(allSources.contains("ProxyApplication"));
        Assert.assertFalse(allSources.contains("ProxyComponentFactory"));
        Assert.assertFalse(allSources.contains("JniBridge"));

        String r8 = Files.readString(layout.getR8Directory().resolve("profile-rules.pro"));
        Assert.assertTrue(r8.contains(identity.getProxyApplicationClassName()));
        Assert.assertTrue(r8.contains(identity.getProxyComponentFactoryClassName()));
        Assert.assertTrue(r8.contains(identity.getJniBridgeClassName()));
        Assert.assertTrue(r8.contains("native <methods>"));
        Assert.assertFalse(r8.contains("-keep class **"));
        Assert.assertFalse(layout.getGeneratedSourceSha256().equals(
                com.luoye.dpt.util.MessageDigestUtils.sha256(new byte[0])));
    }

    @Test
    public void generatesManifestWithProfileSpecificEntrypoints() throws Exception {
        Path identityRoot = temporaryFolder.newFolder("manifest-identity").toPath();
        ProfileCreationResult created = new ProfileCreationService(identityRoot)
                .create("com.example.manifest");
        ProfileIdentityLock identity = new ProfileIdentityStore(identityRoot)
                .loadExisting("com.example.manifest", created.getProfileId());
        Path generatedRoot = temporaryFolder.newFolder("manifest-generated").toPath();

        ProfileCodeGenerator.GenerationLayout layout = new ProfileCodeGenerator(generatedRoot)
                .generateJava(identity, "r000001", template(ProfileRecipeRegistry.RECIPE_A));

        Path manifest = layout.getRevisionRoot().resolve("manifest/AndroidManifest.xml");
        Assert.assertTrue("generated profile manifest is missing", Files.isRegularFile(manifest));
        String content = Files.readString(manifest);
        Assert.assertTrue(content.contains(identity.getProxyApplicationClassName()));
        Assert.assertTrue(content.contains(identity.getProxyComponentFactoryClassName()));
        Assert.assertFalse(content.contains("com.luoyesiqiu.shell"));
    }

    @Test
    public void sameIdentityGeneratesDeterministicallyAndDifferentProfilesDoNotShareSources()
            throws Exception {
        Path identityRoot = temporaryFolder.newFolder("identities").toPath();
        ProfileCreationResult alpha = new ProfileCreationService(identityRoot)
                .create("com.example.alpha");
        ProfileCreationResult beta = new ProfileCreationService(identityRoot)
                .create("com.example.beta");
        ProfileIdentityStore identities = new ProfileIdentityStore(identityRoot);
        ProfileIdentityLock alphaIdentity = identities.loadExisting("com.example.alpha",
                alpha.getProfileId());
        ProfileIdentityLock betaIdentity = identities.loadExisting("com.example.beta",
                beta.getProfileId());
        ProfileRecipeTemplate template = template(ProfileRecipeRegistry.RECIPE_B);

        ProfileCodeGenerator.GenerationLayout first = new ProfileCodeGenerator(
                temporaryFolder.newFolder("first").toPath())
                .generateJava(alphaIdentity, "r000001", template);
        ProfileCodeGenerator.GenerationLayout second = new ProfileCodeGenerator(
                temporaryFolder.newFolder("second").toPath())
                .generateJava(alphaIdentity, "r000001", template);
        ProfileCodeGenerator.GenerationLayout other = new ProfileCodeGenerator(
                temporaryFolder.newFolder("other").toPath())
                .generateJava(betaIdentity, "r000001", template);

        Assert.assertEquals(first.getGeneratedSourceSha256(), second.getGeneratedSourceSha256());
        Assert.assertNotEquals(first.getGeneratedSourceSha256(), other.getGeneratedSourceSha256());
    }

    @Test
    public void recipesUseOnlyProfileSpecificTopLevelNamesWithoutLegacyPresentationMarkers()
            throws Exception {
        Path identityRoot = temporaryFolder.newFolder("profile-identities").toPath();
        ProfileCreationService creationService = new ProfileCreationService(identityRoot);
        ProfileCreationResult alpha = creationService.create("com.example.naming.alpha");
        ProfileCreationResult beta = creationService.create("com.example.naming.beta");
        ProfileIdentityStore identityStore = new ProfileIdentityStore(identityRoot);
        ProfileIdentityLock alphaIdentity = identityStore.loadExisting(
                "com.example.naming.alpha", alpha.getProfileId());
        ProfileIdentityLock betaIdentity = identityStore.loadExisting(
                "com.example.naming.beta", beta.getProfileId());

        for (String recipeId : Arrays.asList(ProfileRecipeRegistry.RECIPE_A,
                ProfileRecipeRegistry.RECIPE_B)) {
            ProfileRecipeTemplate recipe = template(recipeId);
            ProfileCodeGenerator.GenerationLayout alphaLayout = new ProfileCodeGenerator(
                    temporaryFolder.newFolder("alpha-" + recipeId).toPath())
                    .generateJava(alphaIdentity, "r000001", recipe);
            ProfileCodeGenerator.GenerationLayout betaLayout = new ProfileCodeGenerator(
                    temporaryFolder.newFolder("beta-" + recipeId).toPath())
                    .generateJava(betaIdentity, "r000001", recipe);

            assertProfileSpecificJavaTree(alphaLayout, alphaIdentity);
            assertProfileSpecificJavaTree(betaLayout, betaIdentity);
            Assert.assertTrue(recipeId, javaSimpleNames(alphaLayout).stream().noneMatch(
                    javaSimpleNames(betaLayout)::contains));
        }
    }

    private static ProfileRecipeTemplate template(String recipeId) {
        ProfileRecipe recipe = ProfileRecipeRegistry.builtIn().requireVerified(recipeId,
                ProfileSchemaVersions.PROFILE_PROTOCOL);
        return ProfileRecipeTemplateRegistry.builtIn().requireFor(recipe);
    }

    private static Path sourcePath(ProfileCodeGenerator.GenerationLayout layout,
                                   String className) {
        return layout.getJavaDirectory().resolve(className.replace('.', '/') + ".java");
    }

    private static void assertProfileSpecificJavaTree(
            ProfileCodeGenerator.GenerationLayout layout, ProfileIdentityLock identity)
            throws Exception {
        Assert.assertTrue(Files.isRegularFile(sourcePath(layout,
                identity.getProxyApplicationClassName())));
        Assert.assertTrue(Files.isRegularFile(sourcePath(layout,
                identity.getProxyComponentFactoryClassName())));
        Assert.assertTrue(Files.isRegularFile(sourcePath(layout,
                identity.getJniBridgeClassName())));

        Path packageDirectory = layout.getJavaDirectory().resolve(
                identity.getShellPackageName().replace('.', '/'));
        List<Path> sources = javaSources(layout);
        Assert.assertFalse(sources.isEmpty());
        for (Path source : sources) {
            Assert.assertTrue(source.toString(), source.startsWith(packageDirectory));
        }

        String generatedText = readAll(sources);
        for (String marker : Arrays.asList(
                "com.luoye",
                "com.luoyesiqiu",
                "ProxyApplication",
                "ProxyComponentFactory",
                "JniBridge",
                "MainActivity",
                "android.util.Log",
                "Log.",
                "TAG",
                "dpt_",
                "Created by",
                "Hello World",
                "com.example")) {
            Assert.assertFalse(marker, generatedText.contains(marker));
        }

        String expectedR8 = "-keep class " + identity.getProxyApplicationClassName()
                + " { *; }\n"
                + "-keep class " + identity.getProxyComponentFactoryClassName()
                + " { *; }\n"
                + "-keep class " + identity.getJniBridgeClassName() + " {\n"
                + "    native <methods>;\n"
                + "}\n"
                + "-keep class " + identity.getJunkClassName() + " { *; }\n";
        Assert.assertEquals(expectedR8,
                Files.readString(layout.getR8Directory().resolve("profile-rules.pro")));
    }

    private static Set<String> javaSimpleNames(ProfileCodeGenerator.GenerationLayout layout)
            throws Exception {
        Set<String> names = new HashSet<>();
        for (Path source : javaSources(layout)) {
            String fileName = source.getFileName().toString();
            names.add(fileName.substring(0, fileName.length() - ".java".length()));
        }
        return names;
    }

    private static List<Path> javaSources(ProfileCodeGenerator.GenerationLayout layout)
            throws Exception {
        try (Stream<Path> paths = Files.walk(layout.getJavaDirectory())) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }
    }

    private static String readAll(List<Path> files) throws Exception {
        StringBuilder result = new StringBuilder();
        for (Path file : files) {
            result.append(Files.readString(file));
        }
        return result.toString();
    }
}
