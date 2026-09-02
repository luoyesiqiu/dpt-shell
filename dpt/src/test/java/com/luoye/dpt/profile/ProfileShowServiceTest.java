package com.luoye.dpt.profile;

import com.alibaba.fastjson2.JSON;
import com.luoye.dpt.profile.cli.ProfileCommandMain;
import com.luoye.dpt.profile.generation.ProfileCodeGenerator;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.storage.ProfileActiveRevisionStore;
import com.luoye.dpt.profile.storage.ProfileCreationResult;
import com.luoye.dpt.profile.storage.ProfileCreationService;
import com.luoye.dpt.profile.storage.ProfileIdentityStore;
import com.luoye.dpt.profile.storage.ProfileRegistry;
import com.luoye.dpt.profile.storage.ProfilePublishedBundleStore;
import com.luoye.dpt.profile.storage.ProfileRevisionStore;
import com.luoye.dpt.profile.storage.ProfileShowResult;
import com.luoye.dpt.profile.storage.ProfileShowService;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class ProfileShowServiceTest {
    private static final String APPLICATION_ID = "com.example.alpha";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void reportsBindingIdentitySummaryRevisionHistoryAbiAndGateStatusWithoutWriting()
            throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("profiles").toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("active").toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot).create(APPLICATION_ID);
        ProfileRevisionStore revisionStore = new ProfileRevisionStore(profilesRoot);
        ProfileRevisionManifest activeRevision = revisionStore.createCandidate(revision(created));
        ProfileTestArtifactFactory.assemble(profilesRoot, APPLICATION_ID, activeRevision,
                temporaryFolder.newFolder("active-artifacts").toPath());
        new ProfilePublishedBundleStore(profilesRoot, activeProfilesRoot).publishCandidate(
                APPLICATION_ID, created.getProfileId(), activeRevision.getRevisionId());
        revisionStore.markVerified(created.getProfileId(), activeRevision.getRevisionId());
        new ProfileActiveRevisionStore(profilesRoot, activeProfilesRoot, revisionStore)
                .activate(created.getProfileId(), activeRevision.getRevisionId());
        ProfileRevisionManifest candidateRevision = revisionStore.createCandidate(revision(created));

        ProfileRegistry registry = new ProfileRegistry(profilesRoot);
        Path sourceConfigPath = profilesRoot.resolve("source").resolve(created.getProfileId())
                .resolve("profile.yaml");
        Path activePointerPath = activeProfilesRoot.resolve(created.getProfileId()).resolve("active.json");
        String indexBefore = Files.readString(registry.getIndexPath());
        String identityBefore = Files.readString(new ProfileIdentityStore(profilesRoot)
                .getIdentityLockPath(created.getProfileId()));
        String sourceConfigBefore = Files.readString(sourceConfigPath);
        String activePointerBefore = Files.readString(activePointerPath);
        String activeRevisionBefore = Files.readString(revisionStore.getRevisionManifestPath(
                created.getProfileId(), activeRevision.getRevisionId()));
        String candidateRevisionBefore = Files.readString(revisionStore.getRevisionManifestPath(
                created.getProfileId(), candidateRevision.getRevisionId()));

        ProfileShowResult result = new ProfileShowService(profilesRoot, activeProfilesRoot)
                .showByApplicationId(APPLICATION_ID);

        Assert.assertEquals(APPLICATION_ID, result.getApplicationId());
        Assert.assertEquals(created.getProfileId(), result.getProfileId());
        Assert.assertEquals(Arrays.asList("arm64-v8a"), result.getAbis());
        Assert.assertEquals(activeRevision.getRevisionId(), result.getActiveRevisionId());
        Assert.assertEquals(created.getIdentity().getShellPackageName(),
                result.getIdentity().getShellPackageName());
        Assert.assertEquals(created.getIdentity().getJniMethods().size(),
                result.getIdentity().getJniSemanticIds().size());
        Assert.assertEquals(2, result.getRevisions().size());
        Assert.assertEquals(activeRevision.getRevisionId(), result.getRevisions().get(0).getRevisionId());
        Assert.assertEquals("ACTIVE", result.getRevisions().get(0).getStatus());
        Assert.assertEquals("PASSED", result.getRevisions().get(0).getGateStatus());
        Assert.assertEquals(candidateRevision.getRevisionId(), result.getRevisions().get(1).getRevisionId());
        Assert.assertEquals("CANDIDATE", result.getRevisions().get(1).getStatus());
        Assert.assertEquals("PENDING", result.getRevisions().get(1).getGateStatus());

        String serialized = JSON.toJSONString(result);
        Assert.assertFalse(serialized.contains(created.getIdentity().getIdentitySeed()));
        Assert.assertFalse(serialized.contains(created.getIdentity().getBuildKey()));
        Assert.assertEquals(indexBefore, Files.readString(registry.getIndexPath()));
        Assert.assertEquals(identityBefore, Files.readString(new ProfileIdentityStore(profilesRoot)
                .getIdentityLockPath(created.getProfileId())));
        Assert.assertEquals(sourceConfigBefore, Files.readString(sourceConfigPath));
        Assert.assertEquals(activePointerBefore, Files.readString(activePointerPath));
        Assert.assertEquals(activeRevisionBefore, Files.readString(revisionStore.getRevisionManifestPath(
                created.getProfileId(), activeRevision.getRevisionId())));
        Assert.assertEquals(candidateRevisionBefore, Files.readString(revisionStore.getRevisionManifestPath(
                created.getProfileId(), candidateRevision.getRevisionId())));
    }

    @Test
    public void showCommandPrintsStructuredResultForAnExistingProfile() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("profiles").toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("active").toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot).create(APPLICATION_ID);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream previousOutput = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            ProfileCommandMain.main(new String[]{"show", "--profiles-root", profilesRoot.toString(),
                    "--active-profiles-root", activeProfilesRoot.toString(), "--application-id",
                    APPLICATION_ID});
        } finally {
            System.setOut(previousOutput);
        }

        String text = output.toString(StandardCharsets.UTF_8);
        Assert.assertTrue(text, text.startsWith("PROFILE_SHOW_RESULT "));
        Assert.assertTrue(text, text.contains("\"profileId\":\"" + created.getProfileId() + "\""));
        Assert.assertTrue(text, text.contains("\"activeRevisionId\":null"));
    }

    private static ProfileRevisionManifest revision(ProfileCreationResult created) {
        ProfileRevisionManifest value = new ProfileRevisionManifest();
        value.setProfileId(created.getProfileId());
        value.setApplicationId(APPLICATION_ID);
        value.setRecipeId("recipe-a");
        value.setOverlaySha256("a".repeat(64));
        value.setGeneratorVersion(ProfileCodeGenerator.GENERATOR_VERSION);
        value.setCoreVersion(ProfileCodeGenerator.IMMUTABLE_CORE_VERSION);
        value.setToolchainVersion("toolchain-1");
        value.setGeneratedSourceSha256("b".repeat(64));
        return value;
    }
}
