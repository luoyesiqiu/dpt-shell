package com.luoye.dpt.profile;

import com.alibaba.fastjson2.JSON;
import com.luoye.dpt.profile.generation.ProfileCodeGenerator;
import com.luoye.dpt.profile.model.ProfileIdentityLock;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.storage.ProfileActiveRevisionException;
import com.luoye.dpt.profile.storage.ProfileActiveRevisionStore;
import com.luoye.dpt.profile.storage.ProfileIdentityStore;
import com.luoye.dpt.profile.storage.ProfileRegistry;
import com.luoye.dpt.profile.storage.ProfilePublishedBundleStore;
import com.luoye.dpt.profile.storage.ProfileRevisionStore;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;

public class ProfileLifecycleRegressionTest {
    private static final String APPLICATION_ID = "com.example.alpha";
    private static final String PROFILE_ID = "pf_0123456789abcdef";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void preservesBindingAndIdentityAcrossUpgradeFailureAndRollback() throws IOException {
        Path profilesRoot = temporaryFolder.newFolder("profiles").toPath();
        ProfileRegistry registry = new ProfileRegistry(profilesRoot);
        ProfileIdentityStore identities = new ProfileIdentityStore(profilesRoot);
        ProfileRevisionStore revisions = new ProfileRevisionStore(profilesRoot);
        Path activeProfilesRoot = temporaryFolder.newFolder("active-profiles").toPath();
        ProfileActiveRevisionStore active = new ProfileActiveRevisionStore(profilesRoot,
                activeProfilesRoot, revisions);

        registry.register(APPLICATION_ID, PROFILE_ID);
        ProfileIdentityLock identity = identities.createIfAbsent(APPLICATION_ID, PROFILE_ID);
        String identitySnapshot = JSON.toJSONString(identity);

        ProfileRevisionManifest first = verify(profilesRoot, activeProfilesRoot, revisions,
                "recipe-a", temporaryFolder.newFolder("first-artifacts").toPath());
        active.activate(PROFILE_ID, first.getRevisionId());
        ProfileRevisionManifest second = verify(profilesRoot, activeProfilesRoot, revisions,
                "recipe-b", temporaryFolder.newFolder("second-artifacts").toPath());
        active.activate(PROFILE_ID, second.getRevisionId());

        ProfileRevisionManifest failed = revisions.createCandidate(revisionInput("recipe-c"));
        revisions.markRejected(PROFILE_ID, failed.getRevisionId());
        try {
            active.activate(PROFILE_ID, failed.getRevisionId());
            Assert.fail("Expected rejected revision activation failure");
        } catch (ProfileActiveRevisionException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("REJECTED"));
        }

        Assert.assertEquals(second.getRevisionId(), active.loadActive(PROFILE_ID).getRevisionId());
        Assert.assertEquals(first.getRevisionId(), active.rollback(PROFILE_ID, first.getRevisionId())
                .getRevisionId());
        Assert.assertEquals(PROFILE_ID, registry.findProfileId(APPLICATION_ID).orElse(null));
        Assert.assertEquals(identitySnapshot, JSON.toJSONString(
                identities.loadExisting(APPLICATION_ID, PROFILE_ID)));
    }

    private static ProfileRevisionManifest verify(Path profilesRoot, Path activeProfilesRoot,
                                                  ProfileRevisionStore store, String recipeId,
                                                  Path inputRoot) throws IOException {
        ProfileRevisionManifest candidate = store.createCandidate(revisionInput(recipeId));
        ProfileTestArtifactFactory.assemble(profilesRoot, APPLICATION_ID, candidate, inputRoot);
        new ProfilePublishedBundleStore(profilesRoot, activeProfilesRoot).publishCandidate(
                APPLICATION_ID, PROFILE_ID, candidate.getRevisionId());
        return store.markVerified(PROFILE_ID, candidate.getRevisionId());
    }

    private static ProfileRevisionManifest revisionInput(String recipeId) {
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
}
