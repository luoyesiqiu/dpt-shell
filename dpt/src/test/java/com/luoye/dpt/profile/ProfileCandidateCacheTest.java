package com.luoye.dpt.profile;

import com.alibaba.fastjson2.JSON;
import com.luoye.dpt.profile.generation.ProfileCodeGenerator;
import com.luoye.dpt.profile.generation.recipe.ProfileRecipe;
import com.luoye.dpt.profile.generation.recipe.ProfileRecipeRegistry;
import com.luoye.dpt.profile.generation.recipe.ProfileRecipeTemplate;
import com.luoye.dpt.profile.generation.recipe.ProfileRecipeTemplateRegistry;
import com.luoye.dpt.profile.model.ProfileIdentityLock;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.model.ProfileSchemaVersions;
import com.luoye.dpt.profile.storage.ProfileCandidateCache;
import com.luoye.dpt.profile.storage.ProfileCandidateCacheKey;
import com.luoye.dpt.profile.storage.ProfileBuildService;
import com.luoye.dpt.profile.storage.ProfileCreationResult;
import com.luoye.dpt.profile.storage.ProfileCreationService;
import com.luoye.dpt.profile.storage.ProfileIdentityStore;
import com.luoye.dpt.util.MessageDigestUtils;
import com.luoye.dpt.util.HexUtils;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

public class ProfileCandidateCacheTest {
    private static final String APPLICATION_ID = "com.example.cache";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void cacheKeyChangesWhenAnyBuildInputChanges() throws Exception {
        ProfileIdentityLock identity = createIdentity(APPLICATION_ID);
        ProfileRecipe recipeA = ProfileRecipeRegistry.builtIn().requireVerified(
                ProfileRecipeRegistry.RECIPE_A, ProfileSchemaVersions.PROFILE_PROTOCOL);
        ProfileRecipe recipeB = ProfileRecipeRegistry.builtIn().requireVerified(
                ProfileRecipeRegistry.RECIPE_B, ProfileSchemaVersions.PROFILE_PROTOCOL);

        ProfileCandidateCacheKey original = ProfileCandidateCacheKey.from(identity, recipeA,
                "a".repeat(64), "generator-v1", "core-v1", "toolchain-v1");
        Assert.assertEquals(original.getKey(), original.getCacheKey());
        Assert.assertEquals(original.getKey(), ProfileCandidateCacheKey.from(identity, recipeA,
                "a".repeat(64), "generator-v1", "core-v1", "toolchain-v1").getKey());
        Assert.assertNotEquals(original.getKey(), ProfileCandidateCacheKey.from(identity, recipeB,
                "a".repeat(64), "generator-v1", "core-v1", "toolchain-v1").getKey());
        Assert.assertNotEquals(original.getKey(), ProfileCandidateCacheKey.from(identity, recipeA,
                "b".repeat(64), "generator-v1", "core-v1", "toolchain-v1").getKey());
        Assert.assertNotEquals(original.getKey(), ProfileCandidateCacheKey.from(identity, recipeA,
                "a".repeat(64), "generator-v2", "core-v1", "toolchain-v1").getKey());
        Assert.assertNotEquals(original.getKey(), ProfileCandidateCacheKey.from(identity, recipeA,
                "a".repeat(64), "generator-v1", "core-v2", "toolchain-v1").getKey());
        Assert.assertNotEquals(original.getKey(), ProfileCandidateCacheKey.from(identity, recipeA,
                "a".repeat(64), "generator-v1", "core-v1", "toolchain-v2").getKey());
        Assert.assertNotEquals(original.getKey(), ProfileCandidateCacheKey.from(identity,
                "f".repeat(64), recipeA, "a".repeat(64), "generator-v1", "core-v1",
                "toolchain-v1").getKey());
    }

    @Test
    public void cacheRejectsMissingOrTamperedResult() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("cache").toPath();
        ProfileIdentityLock identity = createIdentity(APPLICATION_ID);
        ProfileRecipe recipe = ProfileRecipeRegistry.builtIn().requireVerified(
                ProfileRecipeRegistry.RECIPE_A, ProfileSchemaVersions.PROFILE_PROTOCOL);
        ProfileCandidateCacheKey key = ProfileCandidateCacheKey.from(identity, recipe,
                "a".repeat(64), "generator-v1", "core-v1", "toolchain-v1");
        Path sourceRoot = temporaryFolder.newFolder("source").toPath();
        Files.writeString(sourceRoot.resolve("source.txt"), "stable", StandardCharsets.UTF_8);

        ProfileCandidateCache cache = new ProfileCandidateCache(profilesRoot);
        cache.store(key, sourceRoot);
        Assert.assertTrue(cache.find(key).isPresent());

        Path cachedFile = cache.getEntryPath(key).resolve("generated/source.txt");
        Files.writeString(cachedFile, "tampered", StandardCharsets.UTF_8);
        Assert.assertFalse("tampered cache must not be reused", cache.find(key).isPresent());

        cache.store(key, sourceRoot);
        Assert.assertTrue(cache.find(key).isPresent());
        Files.delete(cache.getEntryPath(key).resolve("complete"));
        Assert.assertFalse("incomplete cache must not be reused", cache.find(key).isPresent());
    }

    @Test
    public void differentProfilesAndToolchainsCannotShareCacheKey() throws Exception {
        ProfileIdentityLock firstIdentity = createIdentity("com.example.cache.first");
        ProfileIdentityLock secondIdentity = createIdentity("com.example.cache.second");
        ProfileRecipe recipe = ProfileRecipeRegistry.builtIn().requireVerified(
                ProfileRecipeRegistry.RECIPE_A, ProfileSchemaVersions.PROFILE_PROTOCOL);
        ProfileCandidateCacheKey first = ProfileCandidateCacheKey.from(firstIdentity, recipe,
                "a".repeat(64), "generator-v1", "core-v1", "toolchain-v1");
        ProfileCandidateCacheKey second = ProfileCandidateCacheKey.from(secondIdentity, recipe,
                "a".repeat(64), "generator-v1", "core-v1", "toolchain-v1");
        Assert.assertNotEquals(first.getKey(), second.getKey());
        Assert.assertNotEquals(first.getKey(), ProfileCandidateCacheKey.from(firstIdentity, recipe,
                "a".repeat(64), "generator-v1", "core-v1", "toolchain-v2").getKey());
    }

    @Test
    public void buildRecordsKeyAndRestoresACompleteCacheForAnotherRevision() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("service-profiles").toPath();
        Path activeRoot = temporaryFolder.newFolder("service-active").toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot).create(APPLICATION_ID);
        ProfileBuildService service = new ProfileBuildService(profilesRoot, activeRoot);

        ProfileRevisionManifest first = service.build(APPLICATION_ID, null,
                ProfileRecipeRegistry.RECIPE_A, null);
        Assert.assertNotNull(first.getCacheKey());
        Path firstGenerated = profilesRoot.resolve("generated").resolve(created.getProfileId())
                .resolve(first.getRevisionId());
        Assert.assertTrue(Files.isDirectory(firstGenerated));
        String firstTree = readTree(firstGenerated);
        try (java.util.stream.Stream<Path> paths = Files.walk(firstGenerated)) {
            paths.sorted((left, right) -> right.compareTo(left)).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        ProfileRevisionManifest second = service.build(APPLICATION_ID, null,
                ProfileRecipeRegistry.RECIPE_A, null);
        Assert.assertEquals(first.getCacheKey(), second.getCacheKey());
        Path secondGenerated = profilesRoot.resolve("generated").resolve(created.getProfileId())
                .resolve(second.getRevisionId());
        Assert.assertEquals(firstTree, readTree(secondGenerated));
    }

    @Test
    public void generatedContractChangeDoesNotReuseLegacyCacheNamespace() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("legacy-cache-profiles").toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("legacy-cache-active").toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot)
                .create(APPLICATION_ID);
        ProfileIdentityStore identityStore = new ProfileIdentityStore(profilesRoot);
        ProfileIdentityLock identity = identityStore.loadExisting(APPLICATION_ID,
                created.getProfileId());
        ProfileRecipe recipe = ProfileRecipeRegistry.builtIn().requireVerified(
                ProfileRecipeRegistry.RECIPE_A, ProfileSchemaVersions.PROFILE_PROTOCOL);
        String toolchain = "legacy-cache-test-toolchain";
        String emptyOverlaySha256 = MessageDigestUtils.sha256(new byte[0]);
        String identityLockSha256 = Files.readString(
                identityStore.getIdentityHashPath(created.getProfileId()),
                StandardCharsets.UTF_8).trim();

        Path staleSourceRoot = temporaryFolder.newFolder("legacy-cache-source").toPath();
        ProfileRecipeTemplate template = ProfileRecipeTemplateRegistry.builtIn()
                .requireFor(recipe);
        ProfileCodeGenerator.GenerationLayout staleLayout = new ProfileCodeGenerator(
                staleSourceRoot).generate(identity, "r000001", template);
        Path staleHeader = staleLayout.getCppDirectory().resolve("profile_config.h");
        Files.writeString(staleHeader, Files.readString(staleHeader, StandardCharsets.UTF_8)
                .replaceAll("(?m)^#define DPT_PROFILE_JUNK_CLASS.*\\R", ""),
                StandardCharsets.UTF_8);

        String legacyKey = legacyCacheKey(identity, identityLockSha256, recipe,
                emptyOverlaySha256, toolchain);
        seedLegacyCache(profilesRoot, identity, identityLockSha256, recipe,
                emptyOverlaySha256, toolchain, legacyKey, staleLayout.getRevisionRoot());

        ProfileRevisionManifest candidate = new ProfileBuildService(profilesRoot,
                activeProfilesRoot, toolchain).build(APPLICATION_ID, null,
                ProfileRecipeRegistry.RECIPE_A, null);
        Path generatedHeader = profilesRoot.resolve("generated").resolve(created.getProfileId())
                .resolve(candidate.getRevisionId()).resolve("cpp/profile_config.h");

        Assert.assertTrue("生成器契约变化后必须重新生成 Profile 头文件",
                Files.readString(generatedHeader, StandardCharsets.UTF_8)
                        .contains("#define DPT_PROFILE_JUNK_CLASS"));
        Assert.assertNotEquals("新候选不得继续使用旧缓存命名空间",
                legacyKey, candidate.getCacheKey());
    }

    private static String legacyCacheKey(ProfileIdentityLock identity, String identityLockSha256,
                                         ProfileRecipe recipe, String overlaySha256,
                                         String toolchainVersion) throws Exception {
        String canonical = "profile-cache-key-v3\n"
                + cacheField("profileId", identity.getProfileId())
                + cacheField("applicationId", identity.getApplicationId())
                + cacheField("identityLockSha256", identityLockSha256)
                + cacheField("recipeId", recipe.getRecipeId())
                + cacheField("recipeProtocolVersion", Integer.toString(recipe.getProtocolVersion()))
                + cacheField("javaCompositionId", recipe.getJavaCompositionId())
                + cacheField("nativeCompositionId", recipe.getNativeCompositionId())
                + cacheField("recipeVerificationStatus", recipe.getVerificationStatus().name())
                + cacheField("overlaySha256", overlaySha256)
                + cacheField("generatorVersion", ProfileCodeGenerator.GENERATOR_VERSION)
                + cacheField("coreVersion", ProfileCodeGenerator.IMMUTABLE_CORE_VERSION)
                + cacheField("toolchainVersion", toolchainVersion)
                + cacheField("abi", ProfileCodeGenerator.PROFILE_ABI);
        return MessageDigestUtils.sha256(canonical.getBytes(StandardCharsets.UTF_8));
    }

    private static String cacheField(String name, String value) {
        return name + "=" + value.length() + ":" + value + "\n";
    }

    private static void seedLegacyCache(Path profilesRoot, ProfileIdentityLock identity,
                                        String identityLockSha256, ProfileRecipe recipe,
                                        String overlaySha256, String toolchainVersion,
                                        String key, Path sourceRoot) throws Exception {
        Path entryRoot = profilesRoot.resolve("cache/candidate-cache")
                .resolve(identity.getProfileId()).resolve(key);
        Path generatedRoot = entryRoot.resolve("generated");
        copyTree(sourceRoot, generatedRoot);
        Snapshot snapshot = snapshot(generatedRoot);

        ProfileCandidateCache.CacheManifest manifest = new ProfileCandidateCache.CacheManifest();
        manifest.setKey(key);
        manifest.setProfileId(identity.getProfileId());
        manifest.setApplicationId(identity.getApplicationId());
        manifest.setIdentityLockSha256(identityLockSha256);
        manifest.setRecipeId(recipe.getRecipeId());
        manifest.setRecipeProtocolVersion(recipe.getProtocolVersion());
        manifest.setJavaCompositionId(recipe.getJavaCompositionId());
        manifest.setNativeCompositionId(recipe.getNativeCompositionId());
        manifest.setRecipeVerificationStatus(recipe.getVerificationStatus().name());
        manifest.setOverlaySha256(overlaySha256);
        manifest.setGeneratorVersion(ProfileCodeGenerator.GENERATOR_VERSION);
        manifest.setCoreVersion(ProfileCodeGenerator.IMMUTABLE_CORE_VERSION);
        manifest.setToolchainVersion(toolchainVersion);
        manifest.setGeneratedSourceSha256(snapshot.generatedSourceSha256);
        manifest.setFileHashes(snapshot.fileHashes);
        Files.writeString(entryRoot.resolve("cache.json"), JSON.toJSONString(manifest),
                StandardCharsets.UTF_8);
        Files.writeString(entryRoot.resolve("complete"), key + "\n", StandardCharsets.UTF_8);
    }

    private static void copyTree(Path sourceRoot, Path targetRoot) throws Exception {
        try (java.util.stream.Stream<Path> paths = Files.walk(sourceRoot)) {
            for (Path source : paths.sorted().collect(java.util.stream.Collectors.toList())) {
                Path relative = sourceRoot.relativize(source);
                Path target = targetRoot.resolve(relative);
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else if (Files.isRegularFile(source)) {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target);
                }
            }
        }
    }

    private static Snapshot snapshot(Path root) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        Map<String, String> fileHashes = new TreeMap<>();
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            for (Path file : paths.filter(Files::isRegularFile).sorted()
                    .collect(java.util.stream.Collectors.toList())) {
                String relative = root.relativize(file).toString().replace(
                        file.getFileSystem().getSeparator(), "/");
                byte[] content = Files.readAllBytes(file);
                fileHashes.put(relative, MessageDigestUtils.sha256(content));
                digest.update(relative.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(content);
                digest.update((byte) 0);
            }
        }
        return new Snapshot(HexUtils.toHexString(digest.digest()), fileHashes);
    }

    private static final class Snapshot {
        private final String generatedSourceSha256;
        private final Map<String, String> fileHashes;

        private Snapshot(String generatedSourceSha256, Map<String, String> fileHashes) {
            this.generatedSourceSha256 = generatedSourceSha256;
            this.fileHashes = fileHashes;
        }
    }

    private ProfileIdentityLock createIdentity(String applicationId) throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("identity-"
                + applicationId.substring(applicationId.lastIndexOf('.') + 1)).toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot).create(applicationId);
        return new ProfileIdentityStore(profilesRoot).loadExisting(applicationId,
                created.getProfileId());
    }

    private static String readTree(Path root) throws Exception {
        StringBuilder result = new StringBuilder();
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile).sorted().forEach(path -> {
                try {
                    result.append(root.relativize(path)).append('=')
                            .append(Files.readString(path)).append('\n');
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
        return result.toString();
    }
}
