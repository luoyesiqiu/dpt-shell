package com.luoye.dpt.profile;

import com.alibaba.fastjson2.JSON;
import com.luoye.dpt.profile.contract.ProfileContractCodec;
import com.luoye.dpt.profile.model.ArtifactHashManifest;
import com.luoye.dpt.profile.model.ProfileContract;
import com.luoye.dpt.profile.model.ProfileIdentityLock;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.storage.ProfileBundle;
import com.luoye.dpt.profile.storage.ProfileBundleAssembler;
import com.luoye.dpt.profile.storage.ProfileBuildService;
import com.luoye.dpt.profile.storage.ProfileCreationResult;
import com.luoye.dpt.profile.storage.ProfileCreationService;
import com.luoye.dpt.profile.storage.ProfileIdentityStore;
import com.luoye.dpt.profile.storage.ProfileRevisionStore;
import com.luoye.dpt.util.MessageDigestUtils;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class ProfileBundleAssemblerTest {
    private static final String APPLICATION_ID = "com.example.bundle";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void assemblesCompleteBundleWithContractAndAllArtifactHashes() throws Exception {
        Fixture fixture = fixture();
        ProfileBundle bundle = new ProfileBundleAssembler(fixture.profilesRoot)
                .assemble(fixture.identity, fixture.revision, fixture.shellDex,
                        fixture.nativeDirectory);

        Assert.assertTrue(Files.isDirectory(bundle.getRoot()));
        Assert.assertTrue(Files.isRegularFile(bundle.getRevisionManifestPath()));
        Assert.assertTrue(Files.isRegularFile(bundle.getProfileContractPath()));
        Assert.assertTrue(Files.isRegularFile(bundle.getArtifactHashManifestPath()));
        Assert.assertTrue(Files.isRegularFile(bundle.getBuildKeyPath()));
        Assert.assertTrue(Files.isRegularFile(bundle.getShellDexPath()));
        Assert.assertTrue(Files.isRegularFile(bundle.getArm64SoPath()));

        ProfileContract contract = ProfileContractCodec.deserialize(
                Files.readAllBytes(bundle.getProfileContractPath()));
        Assert.assertEquals(fixture.identity.getProfileId(), contract.getProfileId());
        Assert.assertEquals(fixture.revision.getRevisionId(), contract.getRevisionId());
        Assert.assertEquals(fixture.identity.getApplicationId(), contract.getApplicationId());
        Assert.assertEquals(fixture.identity.getSoName(), contract.getSoName());
        Assert.assertEquals(fixture.identity.getBuildKey(), contract.getBuildKey());

        ArtifactHashManifest hashes = JSON.parseObject(
                Files.readString(bundle.getArtifactHashManifestPath()), ArtifactHashManifest.class);
        Set<String> expectedPaths = new LinkedHashSet<>(Arrays.asList(
                "revision.json",
                "profile.json",
                "shell-files/build-key",
                "shell-files/dex/classes.dex",
                "shell-files/libs/arm64/" + fixture.identity.getSoName()));
        Assert.assertEquals(expectedPaths, hashes.getSha256().keySet());
        Assert.assertEquals(fixture.identity.getProfileId(), hashes.getProfileId());
        Assert.assertEquals(fixture.revision.getRevisionId(), hashes.getRevisionId());
        for (Map.Entry<String, String> entry : hashes.getSha256().entrySet()) {
            Assert.assertEquals(entry.getKey(), entry.getValue(),
                    MessageDigestUtils.sha256(Files.readAllBytes(bundle.getRoot()
                            .resolve(entry.getKey()))));
        }
        Assert.assertEquals(fixture.identity.getBuildKey() + "\n",
                Files.readString(bundle.getBuildKeyPath()));
    }

    @Test
    public void rejectsMissingDexOrNonUniqueArm64SoWithoutPartialBundle() throws Exception {
        Fixture fixture = fixture();
        Files.delete(fixture.shellDex);
        assertAssemblyFails(fixture);
        Assert.assertFalse(Files.exists(fixture.bundleRoot()));

        fixture = fixture();
        Files.writeString(fixture.nativeDirectory.resolve("second.so"), "other",
                StandardCharsets.UTF_8);
        assertAssemblyFails(fixture);
        Assert.assertFalse(Files.exists(fixture.bundleRoot()));
    }

    @Test
    public void rejectsBuildKeySourceThatDoesNotMatchIdentity() throws Exception {
        Fixture fixture = fixture();
        Path wrongBuildKey = fixture.nativeDirectory.getParent().resolve("wrong-build-key");
        Files.writeString(wrongBuildKey, "wrong\n", StandardCharsets.UTF_8);

        try {
            new ProfileBundleAssembler(fixture.profilesRoot).assemble(fixture.identity,
                    fixture.revision, fixture.shellDex, fixture.nativeDirectory, wrongBuildKey);
            Assert.fail("Expected build-key mismatch");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("build-key"));
        }
        Assert.assertFalse(Files.exists(fixture.bundleRoot()));
    }

    @Test
    public void shellFilesInputRequiresBuildKeyAndOneArm64So() throws Exception {
        Fixture fixture = fixture();
        Path shellFiles = fixture.nativeDirectory.getParent().resolve("shell-files");
        Files.createDirectories(shellFiles.resolve("dex"));
        Files.copy(fixture.shellDex, shellFiles.resolve("dex/classes.dex"));
        Files.createDirectories(shellFiles.resolve("libs/arm64"));
        Files.copy(fixture.nativeDirectory.resolve(fixture.identity.getSoName()),
                shellFiles.resolve("libs/arm64").resolve(fixture.identity.getSoName()));

        try {
            new ProfileBundleAssembler(fixture.profilesRoot).assembleFromShellFiles(
                    fixture.identity, fixture.revision, shellFiles);
            Assert.fail("Expected missing build-key failure");
        } catch (java.io.IOException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("build-key"));
        }
        Assert.assertFalse(Files.exists(fixture.bundleRoot()));
    }

    @Test
    public void rejectsMultipleDexInputsAndNeverOverwritesAnExistingBundle() throws Exception {
        Fixture fixture = fixture();
        Path dexDirectory = fixture.shellDex.getParent().resolve("dex-input");
        Files.createDirectories(dexDirectory);
        Files.writeString(dexDirectory.resolve("classes.dex"), "one", StandardCharsets.UTF_8);
        Files.writeString(dexDirectory.resolve("classes2.dex"), "two", StandardCharsets.UTF_8);
        try {
            new ProfileBundleAssembler(fixture.profilesRoot).assemble(fixture.identity,
                    fixture.revision, dexDirectory, fixture.nativeDirectory);
            Assert.fail("Expected duplicate dex failure");
        } catch (java.io.IOException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("classes.dex"));
        }

        ProfileBundle first = new ProfileBundleAssembler(fixture.profilesRoot).assemble(
                fixture.identity, fixture.revision, fixture.shellDex, fixture.nativeDirectory);
        byte[] before = Files.readAllBytes(first.getShellDexPath());
        try {
            new ProfileBundleAssembler(fixture.profilesRoot).assemble(fixture.identity,
                    fixture.revision, fixture.shellDex, fixture.nativeDirectory);
            Assert.fail("Expected existing Bundle rejection");
        } catch (java.io.IOException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("拒绝覆盖"));
        }
        Assert.assertArrayEquals(before, Files.readAllBytes(first.getShellDexPath()));
    }

    private void assertAssemblyFails(Fixture fixture) throws Exception {
        try {
            new ProfileBundleAssembler(fixture.profilesRoot).assemble(fixture.identity,
                    fixture.revision, fixture.shellDex, fixture.nativeDirectory);
            Assert.fail("Expected strict Bundle assembly failure");
        } catch (java.io.IOException expected) {
            // The exact missing artifact is part of the diagnostic, but no partial output is allowed.
            Assert.assertFalse(expected.getMessage(), expected.getMessage().isEmpty());
        }
    }

    private Fixture fixture() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("profiles-" + System.nanoTime()).toPath();
        Path activeRoot = temporaryFolder.newFolder("active-" + System.nanoTime()).toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot).create(APPLICATION_ID);
        ProfileIdentityLock identity = new ProfileIdentityStore(profilesRoot).loadExisting(
                APPLICATION_ID, created.getProfileId());
        ProfileRevisionManifest revision = new ProfileBuildService(profilesRoot, activeRoot)
                .build(APPLICATION_ID, null, "recipe-a", null);
        Path inputRoot = temporaryFolder.newFolder("bundle-input-" + System.nanoTime()).toPath();
        Path shellDex = inputRoot.resolve("classes.dex");
        Files.writeString(shellDex, "dex-contents", StandardCharsets.UTF_8);
        Path nativeDirectory = inputRoot.resolve("native");
        Files.createDirectories(nativeDirectory);
        Files.writeString(nativeDirectory.resolve(identity.getSoName()), "so-contents",
                StandardCharsets.UTF_8);
        return new Fixture(profilesRoot, identity, revision, shellDex, nativeDirectory);
    }

    private static final class Fixture {
        private final Path profilesRoot;
        private final ProfileIdentityLock identity;
        private final ProfileRevisionManifest revision;
        private final Path shellDex;
        private final Path nativeDirectory;

        private Fixture(Path profilesRoot, ProfileIdentityLock identity,
                        ProfileRevisionManifest revision, Path shellDex, Path nativeDirectory) {
            this.profilesRoot = profilesRoot;
            this.identity = identity;
            this.revision = revision;
            this.shellDex = shellDex;
            this.nativeDirectory = nativeDirectory;
        }

        private Path bundleRoot() {
            return profilesRoot.resolve("candidates").resolve(identity.getProfileId())
                    .resolve(revision.getRevisionId()).resolve("bundle");
        }
    }
}
