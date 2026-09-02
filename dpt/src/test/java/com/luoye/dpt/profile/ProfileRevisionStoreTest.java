package com.luoye.dpt.profile;

import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.model.RevisionStatus;
import com.luoye.dpt.profile.storage.ProfileRevisionException;
import com.luoye.dpt.profile.storage.ProfileRevisionStore;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ProfileRevisionStoreTest {
    private static final String APPLICATION_ID = "com.example.alpha";
    private static final String PROFILE_ID = "pf_0123456789abcdef";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void createsMonotonicCandidateRevisionsWithoutMutatingPersistedInputs() throws IOException {
        ProfileRevisionStore store = new ProfileRevisionStore(
                temporaryFolder.newFolder("profiles").toPath());
        ProfileRevisionManifest firstInput = candidateInput("recipe-a");

        ProfileRevisionManifest first = store.createCandidate(firstInput);
        firstInput.setRecipeId("mutated-after-write");
        ProfileRevisionManifest second = store.createCandidate(candidateInput("recipe-b"));

        Assert.assertEquals("r000001", first.getRevisionId());
        Assert.assertEquals("r000002", second.getRevisionId());
        Assert.assertEquals(RevisionStatus.CANDIDATE, first.getStatus());
        Assert.assertEquals("recipe-a", store.load(PROFILE_ID, first.getRevisionId()).getRecipeId());
        Assert.assertTrue(Files.isRegularFile(store.getRevisionManifestPath(PROFILE_ID, "r000001")));
    }

    @Test
    public void allowsOnlyForwardVerifiedActiveOrRejectedStateTransitions() throws IOException {
        ProfileRevisionStore store = new ProfileRevisionStore(
                temporaryFolder.newFolder("profiles").toPath());
        ProfileRevisionManifest verified = store.createCandidate(candidateInput("recipe-a"));

        assertStatus(RevisionStatus.VERIFIED,
                store.markVerified(PROFILE_ID, verified.getRevisionId()));
        assertStatus(RevisionStatus.ACTIVE,
                store.markActive(PROFILE_ID, verified.getRevisionId()));
        assertTransitionFails(() -> store.markRejected(PROFILE_ID, verified.getRevisionId()), "ACTIVE");

        ProfileRevisionManifest rejected = store.createCandidate(candidateInput("recipe-b"));
        assertStatus(RevisionStatus.REJECTED,
                store.markRejected(PROFILE_ID, rejected.getRevisionId()));
        assertTransitionFails(() -> store.markVerified(PROFILE_ID, rejected.getRevisionId()), "REJECTED");
        assertTransitionFails(() -> store.markActive(PROFILE_ID, rejected.getRevisionId()), "REJECTED");
    }

    @Test
    public void rejectsActivationBeforeVerificationAndKeepsCandidateImmutable() throws IOException {
        ProfileRevisionStore store = new ProfileRevisionStore(
                temporaryFolder.newFolder("profiles").toPath());
        ProfileRevisionManifest candidate = store.createCandidate(candidateInput("recipe-a"));

        assertTransitionFails(() -> store.markActive(PROFILE_ID, candidate.getRevisionId()), "CANDIDATE");
        ProfileRevisionManifest reloaded = store.load(PROFILE_ID, candidate.getRevisionId());
        Assert.assertEquals(RevisionStatus.CANDIDATE, reloaded.getStatus());
        Assert.assertEquals("recipe-a", reloaded.getRecipeId());
    }

    @Test
    public void rejectsLoadingRevisionWhoseManifestNoLongerMatchesRecordedHash() throws IOException {
        ProfileRevisionStore store = new ProfileRevisionStore(
                temporaryFolder.newFolder("profiles").toPath());
        ProfileRevisionManifest candidate = store.createCandidate(candidateInput("recipe-a"));
        Files.writeString(store.getRevisionManifestPath(PROFILE_ID, candidate.getRevisionId()), "{}\n");

        try {
            store.load(PROFILE_ID, candidate.getRevisionId());
            Assert.fail("Expected revision manifest hash mismatch");
        } catch (ProfileRevisionException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("sha256"));
        }
    }

    @Test
    public void recordsGeneratedSourceHashOnlyWhileRevisionIsCandidate() throws IOException {
        ProfileRevisionStore store = new ProfileRevisionStore(
                temporaryFolder.newFolder("profiles").toPath());
        ProfileRevisionManifest candidate = store.createCandidate(candidateInput("recipe-a"));
        String generatedHash = "c".repeat(64);

        ProfileRevisionManifest updated = store.updateGeneratedSourceSha256(PROFILE_ID,
                candidate.getRevisionId(), generatedHash);

        Assert.assertEquals(generatedHash, updated.getGeneratedSourceSha256());
        Assert.assertEquals(generatedHash, store.load(PROFILE_ID, candidate.getRevisionId())
                .getGeneratedSourceSha256());
        store.markVerified(PROFILE_ID, candidate.getRevisionId());
        assertTransitionFails(() -> store.updateGeneratedSourceSha256(PROFILE_ID,
                candidate.getRevisionId(), "d".repeat(64)), "VERIFIED");
    }

    private static ProfileRevisionManifest candidateInput(String recipeId) {
        ProfileRevisionManifest value = new ProfileRevisionManifest();
        value.setProfileId(PROFILE_ID);
        value.setApplicationId(APPLICATION_ID);
        value.setRecipeId(recipeId);
        value.setOverlaySha256("a".repeat(64));
        value.setGeneratorVersion("1");
        value.setCoreVersion("2.20.0");
        value.setToolchainVersion("agp-8.10.0");
        value.setGeneratedSourceSha256("b".repeat(64));
        return value;
    }

    private static void assertStatus(RevisionStatus expected, ProfileRevisionManifest actual) {
        Assert.assertEquals(expected, actual.getStatus());
    }

    private static void assertTransitionFails(ThrowingRunnable runnable, String currentStatus)
            throws IOException {
        try {
            runnable.run();
            Assert.fail("Expected ProfileRevisionException");
        } catch (ProfileRevisionException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(currentStatus));
        }
    }

    private interface ThrowingRunnable {
        void run() throws IOException;
    }
}
