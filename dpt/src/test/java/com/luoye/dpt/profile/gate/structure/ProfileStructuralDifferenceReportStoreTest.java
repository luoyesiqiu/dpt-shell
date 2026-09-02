package com.luoye.dpt.profile.gate.structure;

import com.luoye.dpt.profile.ProfileTestArtifactFactory;
import com.luoye.dpt.profile.gate.ProfileArtifactUniquenessChecker;
import com.luoye.dpt.profile.gate.ProfileArtifactUniquenessReport;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.model.RevisionStatus;
import com.luoye.dpt.profile.storage.ProfileBuildService;
import com.luoye.dpt.profile.storage.ProfileBundle;
import com.luoye.dpt.profile.storage.ProfileCreationResult;
import com.luoye.dpt.profile.storage.ProfileCreationService;
import com.luoye.dpt.profile.storage.ProfileRevisionStore;
import com.luoye.dpt.util.MessageDigestUtils;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ProfileStructuralDifferenceReportStoreTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void persistsFullComparisonAndFeaturesThenBindsSummaryToRevision() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("profiles").toPath();
        Path activeRoot = temporaryFolder.newFolder("active").toPath();
        ProfileStructuralDifferenceReportStore store =
                new ProfileStructuralDifferenceReportStore(profilesRoot);
        Fixture history = fixture(profilesRoot, activeRoot, "history",
                "com.example.report.history");
        ProfileStructuralRevision historyCandidate = structural(history.revision,
                RevisionStatus.CANDIDATE, features("shared", "java"),
                features("shared", "native"));
        store.write(new ProfileStructuralComparisonGate().evaluate(historyCandidate,
                Collections.emptyList()), Collections.emptyList());
        ProfileRevisionStore revisions = new ProfileRevisionStore(profilesRoot);
        revisions.markVerified(history.profileId, history.revision.getRevisionId());
        revisions.markActive(history.profileId, history.revision.getRevisionId());

        List<ProfileStructuralRevision> activeHistory = store.loadActiveHistory();
        Assert.assertEquals(1, activeHistory.size());
        Assert.assertEquals(historyCandidate.getJavaFeatures().getFeatures(),
                activeHistory.get(0).getJavaFeatures().getFeatures());
        Assert.assertEquals(RevisionStatus.ACTIVE, activeHistory.get(0).getStatus());

        Fixture candidate = fixture(profilesRoot, activeRoot, "candidate",
                "com.example.report.candidate");
        ProfileStructuralRevision candidateRevision = structural(candidate.revision,
                RevisionStatus.CANDIDATE, features("shared", "java"),
                features("shared", "native"));
        ProfileStructuralComparisonResult comparison = new ProfileStructuralComparisonGate()
                .evaluate(candidateRevision, activeHistory);
        ProfileArtifactUniquenessReport artifactReport =
                new ProfileArtifactUniquenessChecker().compare(candidate.bundle, history.bundle);

        ProfileStructuralDifferenceReportStore.StoredReport stored = store.write(comparison,
                Arrays.asList(artifactReport));
        ProfileStructuralDifferenceReport report = store.load(candidate.profileId,
                candidate.revision.getRevisionId());

        Assert.assertFalse(report.isPassed());
        Assert.assertEquals("com.example.report.candidate",
                report.getCandidate().getApplicationId());
        Assert.assertEquals("java-variable", report.getCandidate().getJavaFeatures().getScope());
        Assert.assertEquals(1, report.getComparisons().size());
        ProfileStructuralDifferenceReport.Comparison recorded = report.getComparisons().get(0);
        Assert.assertEquals("com.example.report.history",
                recorded.getTarget().getApplicationId());
        Assert.assertEquals(1.0d, recorded.getJavaSimilarity(), 0.0d);
        Assert.assertEquals(1.0d, recorded.getNativeSimilarity(), 0.0d);
        Assert.assertEquals(ProfileStructuralComparisonGate.JAVA_SIMILARITY_LIMIT,
                recorded.getJavaLimit(), 0.0d);
        Assert.assertFalse(recorded.isPassed());
        Assert.assertFalse(recorded.getArtifactUniqueness().getArtifactHashes()
                .get("shell-dex").isIdentical());
        Assert.assertTrue(report.getFailureReasons().toString(),
                report.getFailureReasons().stream().anyMatch(reason -> reason.contains("Java")));
        Assert.assertTrue(report.getFailureReasons().toString(),
                report.getFailureReasons().stream().anyMatch(reason -> reason.contains("Native")));
        Assert.assertEquals(MessageDigestUtils.sha256(Files.readAllBytes(stored.getPath())),
                stored.getSha256());
        Assert.assertEquals(stored.getSha256(), revisions.load(candidate.profileId,
                candidate.revision.getRevisionId()).getStructuralDifferenceReportSha256());
    }

    private Fixture fixture(Path profilesRoot, Path activeRoot, String label,
                            String applicationId) throws Exception {
        ProfileCreationResult created = new ProfileCreationService(profilesRoot)
                .create(applicationId);
        ProfileRevisionManifest revision = new ProfileBuildService(profilesRoot, activeRoot)
                .build(applicationId, null, "recipe-a", null);
        ProfileBundle bundle = ProfileTestArtifactFactory.assemble(profilesRoot, applicationId,
                revision, temporaryFolder.newFolder(label + "-bundle-input").toPath());
        return new Fixture(created.getProfileId(), revision, bundle);
    }

    private static ProfileStructuralRevision structural(ProfileRevisionManifest revision,
                                                         RevisionStatus status,
                                                         Set<String> javaFeatures,
                                                         Set<String> nativeFeatures) {
        return new ProfileStructuralRevision(revision.getApplicationId(), revision.getProfileId(),
                revision.getRevisionId(), status,
                new StructuralFeatureSet("java", "java-variable", javaFeatures),
                new StructuralFeatureSet("native", "native-variable", nativeFeatures));
    }

    private static Set<String> features(String common, String language) {
        return new LinkedHashSet<>(Arrays.asList(common, language + "-shape"));
    }

    private static final class Fixture {
        private final String profileId;
        private final ProfileRevisionManifest revision;
        private final ProfileBundle bundle;

        private Fixture(String profileId, ProfileRevisionManifest revision,
                        ProfileBundle bundle) {
            this.profileId = profileId;
            this.revision = revision;
            this.bundle = bundle;
        }
    }
}
