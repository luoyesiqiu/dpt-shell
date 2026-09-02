package com.luoye.dpt.profile;

import com.luoye.dpt.profile.error.ProfileCommandException;
import com.luoye.dpt.profile.error.ProfileErrorCode;
import com.luoye.dpt.profile.generation.ProfileCodeGenerator;
import com.luoye.dpt.profile.generation.overlay.ProfileOverlayProcessor;
import com.luoye.dpt.profile.model.ProfileIdentityLock;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.model.RevisionStatus;
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

public class ProfileBuildServiceTest {
    private static final String APPLICATION_ID = "com.example.alpha";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void loadsSourceConfigAndCreatesMonotonicCandidateWithoutActivation() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("profiles").toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("active").toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot).create(APPLICATION_ID);
        ProfileIdentityLock identity = new ProfileIdentityStore(profilesRoot).loadExisting(
                APPLICATION_ID, created.getProfileId());
        Path profileSourceRoot = profilesRoot.resolve("source").resolve(created.getProfileId());
        Path overlayRoot = profileSourceRoot.resolve("overlay");
        Path helperSource = overlayRoot.resolve("java/profile-private")
                .resolve(identity.getShellPackageName().replace('.', '/')).resolve("Helper.java");
        Files.createDirectories(helperSource.getParent());
        Files.writeString(helperSource, "package " + identity.getShellPackageName()
                + ";\nfinal class Helper {}\n", StandardCharsets.UTF_8);
        Path sourceConfigPath = profileSourceRoot.resolve("profile.yaml");
        Files.writeString(sourceConfigPath, "schemaVersion: 1\n"
                + "applicationId: com.example.alpha\n"
                + "recipeId: recipe-a\n"
                + "overlayDir: overlay\n"
                + "abis:\n"
                + "  - arm64-v8a\n", StandardCharsets.UTF_8);
        String sourceConfigBefore = Files.readString(sourceConfigPath);
        String identityBefore = Files.readString(new ProfileIdentityStore(profilesRoot)
                .getIdentityLockPath(created.getProfileId()));
        ProfileBuildService service = new ProfileBuildService(profilesRoot, activeProfilesRoot);

        ProfileRevisionManifest first = service.build(APPLICATION_ID, null, null, null);
        ProfileRevisionManifest second = service.build(null, created.getProfileId(),
                "recipe-b", overlayRoot);

        Assert.assertEquals("r000001", first.getRevisionId());
        Assert.assertEquals("r000002", second.getRevisionId());
        Assert.assertEquals(RevisionStatus.CANDIDATE, first.getStatus());
        Assert.assertEquals(RevisionStatus.CANDIDATE, second.getStatus());
        Assert.assertEquals("recipe-a", first.getRecipeId());
        Assert.assertEquals("recipe-b", second.getRecipeId());
        Assert.assertEquals(ProfileCodeGenerator.GENERATOR_VERSION,
                first.getGeneratorVersion());
        Assert.assertEquals(ProfileCodeGenerator.IMMUTABLE_CORE_VERSION,
                first.getCoreVersion());
        Assert.assertEquals(first.getOverlaySha256(), second.getOverlaySha256());
        Assert.assertTrue(first.getOverlaySha256().matches("[0-9a-f]{64}"));
        Assert.assertTrue(first.getGeneratedSourceSha256().matches("[0-9a-f]{64}"));
        Assert.assertNotEquals(MessageDigestUtils.sha256(new byte[0]),
                first.getGeneratedSourceSha256());
        Assert.assertTrue(Files.isDirectory(profilesRoot.resolve("generated")
                .resolve(created.getProfileId()).resolve(first.getRevisionId()).resolve("java")));
        Assert.assertTrue(Files.isDirectory(profilesRoot.resolve("generated")
                .resolve(created.getProfileId()).resolve(second.getRevisionId()).resolve("cpp")));
        Assert.assertTrue(Files.isRegularFile(profilesRoot.resolve("generated")
                .resolve(created.getProfileId()).resolve(first.getRevisionId()).resolve("cpp")
                .resolve("profile_config.h")));
        Assert.assertTrue(Files.isRegularFile(profilesRoot.resolve("generated")
                .resolve(created.getProfileId()).resolve(first.getRevisionId()).resolve("cmake")
                .resolve("profile-sources.cmake")));
        ProfileRevisionStore revisions = new ProfileRevisionStore(profilesRoot);
        Assert.assertTrue(Files.isRegularFile(revisions.getRevisionManifestPath(
                created.getProfileId(), first.getRevisionId())));
        Assert.assertTrue(Files.isRegularFile(revisions.getRevisionManifestPath(
                created.getProfileId(), second.getRevisionId())));
        Assert.assertEquals(first.getGeneratedSourceSha256(), revisions.load(
                created.getProfileId(), first.getRevisionId()).getGeneratedSourceSha256());
        Assert.assertFalse(Files.exists(activeProfilesRoot.resolve(created.getProfileId())
                .resolve("active.json")));
        Assert.assertEquals(sourceConfigBefore, Files.readString(sourceConfigPath));
        Assert.assertEquals(identityBefore, Files.readString(new ProfileIdentityStore(profilesRoot)
                .getIdentityLockPath(created.getProfileId())));
    }

    @Test
    public void automaticallySelectsRecipeWhenSourceAndCommandDoNotSpecifyOne() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("profiles").toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("active").toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot).create(APPLICATION_ID);

        ProfileRevisionManifest candidate = new ProfileBuildService(profilesRoot, activeProfilesRoot)
                .build(APPLICATION_ID, null, null, null);

        Assert.assertEquals("recipe-a", candidate.getRecipeId());
        Assert.assertEquals(RevisionStatus.CANDIDATE, candidate.getStatus());
        Assert.assertTrue(Files.isDirectory(profilesRoot.resolve("generated")
                .resolve(created.getProfileId()).resolve(candidate.getRevisionId())));
        Assert.assertFalse(Files.exists(activeProfilesRoot.resolve(created.getProfileId())
                .resolve("active.json")));
    }

    @Test
    public void automaticSelectionUsesOtherApplicationsActiveRecipeHistory() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("profiles").toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("active").toPath();
        ProfileCreationResult firstProfile = new ProfileCreationService(profilesRoot)
                .create(APPLICATION_ID);
        ProfileBuildService service = new ProfileBuildService(profilesRoot, activeProfilesRoot);
        ProfileRevisionManifest first = service.build(APPLICATION_ID, null, "recipe-a", null);
        ProfileRevisionStore revisions = new ProfileRevisionStore(profilesRoot);
        revisions.markVerified(firstProfile.getProfileId(), first.getRevisionId());
        revisions.markActive(firstProfile.getProfileId(), first.getRevisionId());
        String secondApplicationId = "com.example.beta";
        new ProfileCreationService(profilesRoot).create(secondApplicationId);

        ProfileRevisionManifest selected = service.build(secondApplicationId, null, null, null);

        Assert.assertEquals("recipe-b", selected.getRecipeId());
    }

    @Test
    public void automaticSelectionFailsStrictlyWhenNoRecipeMeetsThreshold() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("profiles").toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("active").toPath();
        ProfileBuildService service = new ProfileBuildService(profilesRoot, activeProfilesRoot);
        ProfileRevisionStore revisions = new ProfileRevisionStore(profilesRoot);
        activateRecipe(profilesRoot, service, revisions, "com.example.alpha", "recipe-a");
        activateRecipe(profilesRoot, service, revisions, "com.example.beta", "recipe-b");
        ProfileCreationResult current = new ProfileCreationService(profilesRoot)
                .create("com.example.gamma");

        try {
            service.build("com.example.gamma", null, null, null);
            Assert.fail("Expected no eligible recipe failure");
        } catch (ProfileCommandException expected) {
            Assert.assertEquals(ProfileErrorCode.INVALID_CANDIDATE, expected.getErrorCode());
            Assert.assertTrue(expected.getMessage(), expected.getMessage()
                    .contains("没有候选 Recipe"));
        }

        Assert.assertTrue(new ProfileRevisionStore(profilesRoot)
                .list(current.getProfileId()).isEmpty());
        Assert.assertFalse(Files.exists(profilesRoot.resolve("generated")
                .resolve(current.getProfileId())));
    }

    @Test
    public void rejectsUnknownRecipeBeforeCreatingCandidateOrGeneratedSources() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("profiles").toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("active").toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot).create(APPLICATION_ID);

        try {
            new ProfileBuildService(profilesRoot, activeProfilesRoot)
                    .build(APPLICATION_ID, null, "unknown-recipe", null);
            Assert.fail("Expected unknown recipe failure");
        } catch (ProfileCommandException expected) {
            Assert.assertEquals(ProfileErrorCode.INVALID_CANDIDATE, expected.getErrorCode());
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("Recipe"));
        }

        Assert.assertFalse(Files.exists(profilesRoot.resolve("revisions")));
        Assert.assertFalse(Files.exists(profilesRoot.resolve("generated")
                .resolve(created.getProfileId())));
    }

    @Test
    public void copiesLegalOverlayIntoGeneratedSourcesAndPersistsExactDigest() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("overlay-profiles").toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("overlay-active").toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot)
                .create(APPLICATION_ID);
        ProfileIdentityLock identity = new ProfileIdentityStore(profilesRoot).loadExisting(
                APPLICATION_ID, created.getProfileId());
        Path overlayRoot = temporaryFolder.newFolder("legal-overlay").toPath();
        Path relativeJava = Path.of(identity.getShellPackageName().replace('.', '/'))
                .resolve("OverlayHelper.java");
        Path source = overlayRoot.resolve("java/profile-private").resolve(relativeJava);
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package " + identity.getShellPackageName() + ";\n"
                + "final class OverlayHelper { int value() { return 7; } }\n",
                StandardCharsets.UTF_8);
        String expectedOverlaySha256 = new ProfileOverlayProcessor().prepare(overlayRoot, identity)
                .getSha256();

        ProfileRevisionManifest revision = new ProfileBuildService(profilesRoot, activeProfilesRoot)
                .build(APPLICATION_ID, null, "recipe-a", overlayRoot);

        Assert.assertEquals(expectedOverlaySha256, revision.getOverlaySha256());
        Assert.assertTrue(Files.isRegularFile(profilesRoot.resolve("generated")
                .resolve(created.getProfileId()).resolve(revision.getRevisionId()).resolve("java")
                .resolve(relativeJava)));
        Assert.assertEquals(expectedOverlaySha256, new ProfileRevisionStore(profilesRoot)
                .load(created.getProfileId(), revision.getRevisionId()).getOverlaySha256());
    }

    @Test
    public void rejectsUnsafeOverlayBeforeCreatingCandidate() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("unsafe-profiles").toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("unsafe-active").toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot)
                .create(APPLICATION_ID);
        ProfileIdentityLock identity = new ProfileIdentityStore(profilesRoot).loadExisting(
                APPLICATION_ID, created.getProfileId());
        Path overlayRoot = temporaryFolder.newFolder("unsafe-overlay").toPath();
        Path source = overlayRoot.resolve("java/profile-private")
                .resolve(identity.getShellPackageName().replace('.', '/')).resolve("Remote.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package " + identity.getShellPackageName() + ";\n"
                + "import java.net.URL;\nfinal class Remote { URL endpoint; }\n",
                StandardCharsets.UTF_8);

        try {
            new ProfileBuildService(profilesRoot, activeProfilesRoot)
                    .build(APPLICATION_ID, null, "recipe-a", overlayRoot);
            Assert.fail("Expected unsafe Overlay failure");
        } catch (ProfileCommandException expected) {
            Assert.assertEquals(ProfileErrorCode.INVALID_CANDIDATE, expected.getErrorCode());
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("网络"));
        }

        Assert.assertTrue(new ProfileRevisionStore(profilesRoot)
                .list(created.getProfileId()).isEmpty());
        Assert.assertFalse(Files.exists(profilesRoot.resolve("generated")
                .resolve(created.getProfileId())));
    }

    @Test
    public void marksCandidateRejectedWhenOverlayConflictsWithGeneratedSource() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("conflict-profiles").toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("conflict-active").toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot)
                .create(APPLICATION_ID);
        Path overlayRoot = temporaryFolder.newFolder("generated-conflict-overlay").toPath();
        Path source = overlayRoot.resolve("cpp/profile-private/profile_config.h");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "#pragma once\n", StandardCharsets.UTF_8);

        try {
            new ProfileBuildService(profilesRoot, activeProfilesRoot)
                    .build(APPLICATION_ID, null, "recipe-a", overlayRoot);
            Assert.fail("Expected generated source conflict");
        } catch (ProfileCommandException expected) {
            Assert.assertEquals(ProfileErrorCode.INVALID_CANDIDATE, expected.getErrorCode());
            Assert.assertTrue(expected.getMessage(), expected.getMessage()
                    .contains("生成文件冲突"));
        }

        ProfileRevisionManifest rejected = new ProfileRevisionStore(profilesRoot)
                .load(created.getProfileId(), "r000001");
        Assert.assertEquals(RevisionStatus.REJECTED, rejected.getStatus());
        Assert.assertFalse(Files.exists(activeProfilesRoot.resolve(created.getProfileId())
                .resolve("active.json")));
    }

    private static void activateRecipe(Path profilesRoot, ProfileBuildService service,
                                       ProfileRevisionStore revisions, String applicationId,
                                       String recipeId) throws Exception {
        ProfileCreationResult profile = new ProfileCreationService(profilesRoot).create(applicationId);
        ProfileRevisionManifest revision = service.build(applicationId, null, recipeId, null);
        revisions.markVerified(profile.getProfileId(), revision.getRevisionId());
        revisions.markActive(profile.getProfileId(), revision.getRevisionId());
    }
}
