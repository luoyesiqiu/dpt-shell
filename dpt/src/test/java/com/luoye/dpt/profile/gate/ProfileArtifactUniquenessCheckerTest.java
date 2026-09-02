package com.luoye.dpt.profile.gate;

import com.luoye.dpt.profile.ProfileTestArtifactFactory;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.storage.ProfileBuildService;
import com.luoye.dpt.profile.storage.ProfileBundle;
import com.luoye.dpt.profile.storage.ProfileCreationService;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;

public class ProfileArtifactUniquenessCheckerTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void independentProfileBundlesHaveDisjointIdentifiersAndArtifactHashes()
            throws Exception {
        ProfileBundle alpha = bundle("alpha", "com.example.unique.alpha");
        ProfileBundle beta = bundle("beta", "com.example.unique.beta");

        ProfileArtifactUniquenessReport report =
                new ProfileArtifactUniquenessChecker().compare(alpha, beta);

        Assert.assertTrue(report.getIdentifierConflicts().toString(), report.isPassed());
        Assert.assertEquals("com.example.unique.alpha", report.getLeft().getApplicationId());
        Assert.assertEquals(alpha.getProfileId(), report.getLeft().getProfileId());
        Assert.assertEquals(alpha.getRevisionId(), report.getLeft().getRevisionId());
        Assert.assertEquals("com.example.unique.beta", report.getRight().getApplicationId());
        Assert.assertFalse(report.getArtifactHashes().get("shell-dex").isIdentical());
        Assert.assertFalse(report.getArtifactHashes().get("arm64-so").isIdentical());
    }

    @Test
    public void reusedBundleReportsEveryIdentifierAndIdenticalArtifactHash() throws Exception {
        ProfileBundle bundle = bundle("reused", "com.example.unique.reused");

        ProfileArtifactUniquenessReport report =
                new ProfileArtifactUniquenessChecker().compare(bundle, bundle);

        Assert.assertFalse(report.isPassed());
        Assert.assertTrue(report.getIdentifierConflicts().stream()
                .anyMatch(conflict -> "shell-package".equals(conflict.getLeftField())));
        Assert.assertTrue(report.getIdentifierConflicts().stream()
                .anyMatch(conflict -> "proxy-application-class".equals(conflict.getLeftField())));
        Assert.assertTrue(report.getIdentifierConflicts().stream()
                .anyMatch(conflict -> conflict.getLeftField().startsWith("jni-method:")));
        Assert.assertTrue(report.getIdentifierConflicts().stream()
                .anyMatch(conflict -> "build-key".equals(conflict.getLeftField())));
        Assert.assertTrue(report.getArtifactHashes().get("shell-dex").isIdentical());
        Assert.assertTrue(report.getArtifactHashes().get("arm64-so").isIdentical());
    }

    private ProfileBundle bundle(String label, String applicationId) throws Exception {
        Path profilesRoot = temporaryFolder.newFolder(label + "-profiles").toPath();
        Path activeRoot = temporaryFolder.newFolder(label + "-active").toPath();
        new ProfileCreationService(profilesRoot).create(applicationId);
        ProfileRevisionManifest revision = new ProfileBuildService(profilesRoot, activeRoot)
                .build(applicationId, null, "recipe-a", null);
        return ProfileTestArtifactFactory.assemble(profilesRoot, applicationId, revision,
                temporaryFolder.newFolder(label + "-bundle-input").toPath());
    }
}
