package com.luoye.dpt.profile;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.luoye.dpt.profile.generation.ProfileCodeGenerator;
import com.luoye.dpt.profile.gate.scan.ProfileScanReport;
import com.luoye.dpt.profile.gate.scan.ProfileScanReportCodec;
import com.luoye.dpt.profile.model.ActiveRevisionPointer;
import com.luoye.dpt.profile.model.ProfileGateReport;
import com.luoye.dpt.profile.model.ProfileIdentityLock;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.model.RevisionStatus;
import com.luoye.dpt.profile.storage.ProfileActiveRevisionStore;
import com.luoye.dpt.profile.storage.ProfileCreationResult;
import com.luoye.dpt.profile.storage.ProfileCreationService;
import com.luoye.dpt.profile.storage.ProfileIdentityStore;
import com.luoye.dpt.profile.storage.ProfilePublishService;
import com.luoye.dpt.profile.storage.ProfileRevisionStore;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProfileMigrationLifecycleEvidenceTest {
    private static final List<String> REQUIRED_GATES = Arrays.asList(
            "contract", "arm64", "static-identifiers", "structural-difference", "size",
            "permissions-components", "performance", "behavior", "scan");

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void twoApplicationsHaveIndependentLifecycleAndRollbackProtection() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("profiles").toPath();
        Path activeRoot = temporaryFolder.newFolder("active").toPath();
        ProfileCreationService creation = new ProfileCreationService(profilesRoot);
        ProfileCreationResult alpha = creation.create("com.example.migration.alpha");
        ProfileCreationResult beta = creation.create("com.example.migration.beta");
        Assert.assertNotEquals(alpha.getProfileId(), beta.getProfileId());

        ProfileIdentityLock alphaIdentity = new ProfileIdentityStore(profilesRoot).loadExisting(
                "com.example.migration.alpha", alpha.getProfileId());
        ProfileIdentityLock betaIdentity = new ProfileIdentityStore(profilesRoot).loadExisting(
                "com.example.migration.beta", beta.getProfileId());
        Assert.assertNotEquals(alphaIdentity.getShellPackageName(), betaIdentity.getShellPackageName());
        Assert.assertNotEquals(alphaIdentity.getSoName(), betaIdentity.getSoName());
        Assert.assertNotEquals(alphaIdentity.getBuildKey(), betaIdentity.getBuildKey());

        ProfileRevisionStore revisions = new ProfileRevisionStore(profilesRoot);
        ProfilePublishService publish = new ProfilePublishService(profilesRoot, activeRoot);

        ProfileRevisionManifest alphaFirst = candidate(alpha, "com.example.migration.alpha",
                "recipe-a");
        alphaFirst = revisions.createCandidate(alphaFirst);
        ProfileTestArtifactFactory.assemble(profilesRoot, "com.example.migration.alpha", alphaFirst,
                temporaryFolder.newFolder("alpha-first-artifacts").toPath());
        publish.publishStrict("com.example.migration.alpha", null, alphaFirst.getRevisionId(),
                reportDirectory("alpha-first-report", alpha, alphaFirst,
                        "com.example.migration.alpha", false));

        ProfileRevisionManifest betaFirst = revisions.createCandidate(candidate(beta,
                "com.example.migration.beta", "recipe-b"));
        ProfileTestArtifactFactory.assemble(profilesRoot, "com.example.migration.beta", betaFirst,
                temporaryFolder.newFolder("beta-first-artifacts").toPath());
        publish.publishStrict("com.example.migration.beta", null, betaFirst.getRevisionId(),
                reportDirectory("beta-first-report", beta, betaFirst,
                        "com.example.migration.beta", false));

        ActiveRevisionPointer alphaBeforeFailure = new ProfileActiveRevisionStore(profilesRoot,
                activeRoot, revisions).loadActive(alpha.getProfileId());
        ProfileRevisionManifest alphaFailed = revisions.createCandidate(candidate(alpha,
                "com.example.migration.alpha", "recipe-b"));
        ProfileTestArtifactFactory.assemble(profilesRoot, "com.example.migration.alpha", alphaFailed,
                temporaryFolder.newFolder("alpha-failed-artifacts").toPath());
        try {
            publish.publishStrict("com.example.migration.alpha", null,
                    alphaFailed.getRevisionId(),
                    reportDirectory("alpha-failed-report", alpha, alphaFailed,
                            "com.example.migration.alpha", true));
            Assert.fail("Expected failed upgrade to be rejected");
        } catch (java.io.IOException expected) {
            // The service may expose a command error or an I/O wrapper for a failed gate.
        }
        Assert.assertEquals(RevisionStatus.REJECTED,
                revisions.load(alpha.getProfileId(), alphaFailed.getRevisionId()).getStatus());
        Assert.assertEquals(alphaBeforeFailure.getRevisionId(), new ProfileActiveRevisionStore(
                profilesRoot, activeRoot, revisions).loadActive(alpha.getProfileId()).getRevisionId());

        ProfileRevisionManifest alphaSecond = revisions.createCandidate(candidate(alpha,
                "com.example.migration.alpha", "recipe-b"));
        ProfileTestArtifactFactory.assemble(profilesRoot, "com.example.migration.alpha", alphaSecond,
                temporaryFolder.newFolder("alpha-second-artifacts").toPath());
        publish.publishStrict("com.example.migration.alpha", null, alphaSecond.getRevisionId(),
                reportDirectory("alpha-second-report", alpha, alphaSecond,
                        "com.example.migration.alpha", false));
        ActiveRevisionPointer alphaSecondActive = new ProfileActiveRevisionStore(profilesRoot,
                activeRoot, revisions).loadActive(alpha.getProfileId());
        Assert.assertEquals(alphaSecond.getRevisionId(), alphaSecondActive.getRevisionId());

        ActiveRevisionPointer rolledBack = publish.rollback("com.example.migration.alpha", null,
                alphaFirst.getRevisionId());
        Assert.assertEquals(alphaFirst.getRevisionId(), rolledBack.getRevisionId());
        Assert.assertEquals(betaFirst.getRevisionId(), new ProfileActiveRevisionStore(profilesRoot,
                activeRoot, revisions).loadActive(beta.getProfileId()).getRevisionId());

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("status", "PASSED");
        evidence.put("applications", Arrays.asList(
                lifecycle(alpha, "com.example.migration.alpha", alphaFirst, alphaFailed,
                        alphaSecond, alphaBeforeFailure, rolledBack),
                lifecycle(beta, "com.example.migration.beta", betaFirst, null, null,
                        new ProfileActiveRevisionStore(profilesRoot, activeRoot, revisions)
                                .loadActive(beta.getProfileId()),
                        null)));
        Path output = findProjectRoot().resolve("build/dpt-profile-baseline/profile-lifecycle.generated.json");
        Files.writeString(output, JSON.toJSONString(evidence, JSONWriter.Feature.PrettyFormat),
                StandardCharsets.UTF_8);
    }

    private static Map<String, Object> lifecycle(ProfileCreationResult profile, String applicationId,
                                                  ProfileRevisionManifest first,
                                                  ProfileRevisionManifest failed,
                                                  ProfileRevisionManifest successful,
                                                  ActiveRevisionPointer beforeFailure,
                                                  ActiveRevisionPointer rolledBack) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("applicationId", applicationId);
        result.put("profileId", profile.getProfileId());
        result.put("firstRevision", first.getRevisionId());
        result.put("failedRevision", failed == null ? null : failed.getRevisionId());
        result.put("successfulRevision", successful == null ? null : successful.getRevisionId());
        result.put("activeBeforeFailedUpgrade", beforeFailure.getRevisionId());
        result.put("rollbackRevision", rolledBack == null ? null : rolledBack.getRevisionId());
        result.put("identitiesAreProfileSpecific", true);
        return result;
    }

    private Path reportDirectory(String name, ProfileCreationResult profile,
                                 ProfileRevisionManifest revision, String applicationId,
                                 boolean failed) throws Exception {
        Path directory = temporaryFolder.newFolder(name).toPath();
        ProfileGateReport gate = new ProfileGateReport();
        gate.setProfileId(profile.getProfileId());
        gate.setRevisionId(revision.getRevisionId());
        gate.setCandidateApkSha256("c".repeat(64));
        gate.setOverallStatus(failed ? ProfileGateReport.Status.FAILED
                : ProfileGateReport.Status.PASSED);
        gate.setGeneratedAtEpochMillis(System.currentTimeMillis());
        Map<String, ProfileGateReport.Result> results = new LinkedHashMap<>();
        for (String gateId : REQUIRED_GATES) {
            ProfileGateReport.Result result = new ProfileGateReport.Result();
            result.setStatus(failed && "performance".equals(gateId)
                    ? ProfileGateReport.Status.FAILED : ProfileGateReport.Status.PASSED);
            results.put(gateId, result);
        }
        gate.setResults(results);
        Files.writeString(directory.resolve("gate-report.json"), JSON.toJSONString(gate),
                StandardCharsets.UTF_8);
        writeScan(directory.resolve("scan-baseline.json"), applicationId, "a".repeat(64));
        writeScan(directory.resolve("scan-candidate.json"), applicationId,
                gate.getCandidateApkSha256());
        return directory;
    }

    private static void writeScan(Path path, String applicationId, String apkSha256) throws Exception {
        ProfileScanReport report = new ProfileScanReport();
        report.setApplicationId(applicationId);
        report.setApkSha256(apkSha256);
        report.setGeneratedAtEpochMillis(1_700_000_000_000L);
        report.setWindowId("migration-window");
        report.setWindowStartEpochMillis(1_700_000_000_000L);
        report.setWindowEndEpochMillis(1_700_000_360_000L);
        Map<String, String> scanners = new LinkedHashMap<>();
        scanners.put("engine", "1");
        report.setScannerSet(scanners);
        ProfileScanReportCodec.write(path, report);
    }

    private static ProfileRevisionManifest candidate(ProfileCreationResult profile,
                                                      String applicationId, String recipeId) {
        ProfileRevisionManifest value = new ProfileRevisionManifest();
        value.setProfileId(profile.getProfileId());
        value.setApplicationId(applicationId);
        value.setRecipeId(recipeId);
        value.setOverlaySha256("a".repeat(64));
        value.setGeneratorVersion(ProfileCodeGenerator.GENERATOR_VERSION);
        value.setCoreVersion(ProfileCodeGenerator.IMMUTABLE_CORE_VERSION);
        value.setToolchainVersion("toolchain-1");
        value.setGeneratedSourceSha256("b".repeat(64));
        return value;
    }

    private static Path findProjectRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("settings.gradle"))
                    && Files.isRegularFile(current.resolve("gradlew"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("dpt-shell project root not found");
    }
}
