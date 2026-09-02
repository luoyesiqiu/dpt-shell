package com.luoye.dpt.profile;

import com.luoye.dpt.profile.model.ActiveRevisionPointer;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.model.RevisionStatus;
import com.luoye.dpt.profile.generation.ProfileCodeGenerator;
import com.luoye.dpt.profile.storage.ProfileActiveRevisionException;
import com.luoye.dpt.profile.storage.ProfileActiveRevisionStore;
import com.luoye.dpt.profile.storage.ProfileIdentityStore;
import com.luoye.dpt.profile.storage.ProfilePublishedBundleStore;
import com.luoye.dpt.profile.storage.ProfileRegistry;
import com.luoye.dpt.profile.storage.ProfileRevisionStore;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ProfileActiveRevisionStoreTest {
    private static final String APPLICATION_ID = "com.example.alpha";
    private static final String PROFILE_ID = "pf_0123456789abcdef";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void atomicallyActivatesOnlyVerifiedRevisionAndRecordsManifestHash() throws IOException {
        Path profilesRoot = temporaryFolder.newFolder("profiles").toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("active-profiles").toPath();
        createProfile(profilesRoot);
        ProfileRevisionStore revisions = new ProfileRevisionStore(profilesRoot);
        ProfileActiveRevisionStore active = new ProfileActiveRevisionStore(profilesRoot,
                activeProfilesRoot, revisions);
        ProfileRevisionManifest candidate = revisions.createCandidate(candidateInput("recipe-a"));
        publishBundle(profilesRoot, activeProfilesRoot, candidate,
                temporaryFolder.newFolder("first-artifacts").toPath());
        revisions.markVerified(PROFILE_ID, candidate.getRevisionId());

        ActiveRevisionPointer pointer = active.activate(PROFILE_ID, candidate.getRevisionId());

        Assert.assertEquals(PROFILE_ID, pointer.getProfileId());
        Assert.assertEquals(candidate.getRevisionId(), pointer.getRevisionId());
        Assert.assertEquals(revisions.getManifestSha256(PROFILE_ID, candidate.getRevisionId()),
                pointer.getProfileManifestSha256());
        Assert.assertTrue(Files.isRegularFile(active.getActivePointerPath(PROFILE_ID)));
        Assert.assertEquals(RevisionStatus.ACTIVE,
                revisions.load(PROFILE_ID, candidate.getRevisionId()).getStatus());
    }

    @Test
    public void rollsBackOnlyToHistoricalActiveRevision() throws IOException {
        Path profilesRoot = temporaryFolder.newFolder("profiles").toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("active-profiles").toPath();
        createProfile(profilesRoot);
        ProfileRevisionStore revisions = new ProfileRevisionStore(profilesRoot);
        ProfileActiveRevisionStore active = new ProfileActiveRevisionStore(profilesRoot,
                activeProfilesRoot, revisions);
        ProfileRevisionManifest first = verified(profilesRoot, activeProfilesRoot, revisions,
                "recipe-a", temporaryFolder.newFolder("first-artifacts").toPath());
        active.activate(PROFILE_ID, first.getRevisionId());
        ProfileRevisionManifest second = verified(profilesRoot, activeProfilesRoot, revisions,
                "recipe-b", temporaryFolder.newFolder("second-artifacts").toPath());
        active.activate(PROFILE_ID, second.getRevisionId());

        ActiveRevisionPointer rolledBack = active.rollback(PROFILE_ID, first.getRevisionId());

        Assert.assertEquals(first.getRevisionId(), rolledBack.getRevisionId());
        Assert.assertEquals(first.getRevisionId(), active.loadActive(PROFILE_ID).getRevisionId());
    }

    @Test
    public void preservesOldPointerWhenCandidateIsNotEligibleOrManifestIsTampered() throws IOException {
        Path profilesRoot = temporaryFolder.newFolder("profiles").toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("active-profiles").toPath();
        createProfile(profilesRoot);
        ProfileRevisionStore revisions = new ProfileRevisionStore(profilesRoot);
        ProfileActiveRevisionStore active = new ProfileActiveRevisionStore(profilesRoot,
                activeProfilesRoot, revisions);
        ProfileRevisionManifest activeRevision = verified(profilesRoot, activeProfilesRoot,
                revisions, "recipe-a",
                temporaryFolder.newFolder("active-artifacts").toPath());
        active.activate(PROFILE_ID, activeRevision.getRevisionId());
        ProfileRevisionManifest candidate = revisions.createCandidate(candidateInput("recipe-b"));

        assertActivationFails(() -> active.activate(PROFILE_ID, candidate.getRevisionId()), "CANDIDATE");
        Assert.assertEquals(activeRevision.getRevisionId(), active.loadActive(PROFILE_ID).getRevisionId());

        Files.writeString(revisions.getRevisionManifestPath(PROFILE_ID, activeRevision.getRevisionId()), "{}\n");
        assertActivationFails(() -> active.loadActive(PROFILE_ID), "sha256");
    }

    private static ProfileRevisionManifest verified(Path profilesRoot, Path activeProfilesRoot,
                                                    ProfileRevisionStore store, String recipeId,
                                                    Path inputRoot) throws IOException {
        ProfileRevisionManifest candidate = store.createCandidate(candidateInput(recipeId));
        publishBundle(profilesRoot, activeProfilesRoot, candidate, inputRoot);
        return store.markVerified(PROFILE_ID, candidate.getRevisionId());
    }

    private static void createProfile(Path profilesRoot) throws IOException {
        new ProfileRegistry(profilesRoot).register(APPLICATION_ID, PROFILE_ID);
        new ProfileIdentityStore(profilesRoot).createIfAbsent(APPLICATION_ID, PROFILE_ID);
    }

    private static void publishBundle(Path profilesRoot, Path activeProfilesRoot,
                                      ProfileRevisionManifest candidate, Path inputRoot)
            throws IOException {
        ProfileTestArtifactFactory.assemble(profilesRoot, APPLICATION_ID, candidate, inputRoot);
        new ProfilePublishedBundleStore(profilesRoot, activeProfilesRoot).publishCandidate(
                APPLICATION_ID, PROFILE_ID, candidate.getRevisionId());
    }

    private static ProfileRevisionManifest candidateInput(String recipeId) {
        ProfileRevisionManifest value = new ProfileRevisionManifest();
        value.setProfileId(PROFILE_ID);
        value.setApplicationId(APPLICATION_ID);
        value.setRecipeId(recipeId);
        value.setOverlaySha256("a".repeat(64));
        value.setGeneratorVersion(ProfileCodeGenerator.GENERATOR_VERSION);
        value.setCoreVersion(ProfileCodeGenerator.IMMUTABLE_CORE_VERSION);
        value.setToolchainVersion("agp-8.10.0");
        value.setGeneratedSourceSha256("b".repeat(64));
        return value;
    }

    private static void assertActivationFails(ThrowingRunnable runnable, String expectedMessage)
            throws IOException {
        try {
            runnable.run();
            Assert.fail("Expected ProfileActiveRevisionException");
        } catch (ProfileActiveRevisionException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(expectedMessage));
        }
    }

    private interface ThrowingRunnable {
        void run() throws IOException;
    }
}
