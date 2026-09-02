package com.luoye.dpt.profile;

import com.alibaba.fastjson2.JSON;
import com.luoye.dpt.profile.error.ProfileCommandException;
import com.luoye.dpt.profile.error.ProfileErrorCode;
import com.luoye.dpt.profile.model.ActiveRevisionPointer;
import com.luoye.dpt.profile.model.ProfileGateReport;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.model.RevisionStatus;
import com.luoye.dpt.profile.generation.ProfileCodeGenerator;
import com.luoye.dpt.profile.gate.scan.ProfileScanReport;
import com.luoye.dpt.profile.gate.scan.ProfileScanReportCodec;
import com.luoye.dpt.profile.storage.ProfileActiveRevisionStore;
import com.luoye.dpt.profile.storage.ProfileActiveRevisionException;
import com.luoye.dpt.profile.storage.ProfileCreationResult;
import com.luoye.dpt.profile.storage.ProfileCreationService;
import com.luoye.dpt.profile.storage.ProfilePublishService;
import com.luoye.dpt.profile.storage.ProfileRevisionStore;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

public class ProfilePublishServiceTest {
    private static final String APPLICATION_ID = "com.example.alpha";
    private static final List<String> REQUIRED_GATES = Arrays.asList(
            "contract", "arm64", "static-identifiers", "structural-difference", "size",
            "permissions-components", "performance", "behavior", "scan");

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void publishesOnlyWhenEveryHardGatePassesAndRollsBackHistoricalActiveRevision()
            throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("profiles").toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("active").toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot).create(APPLICATION_ID);
        ProfileRevisionStore revisions = new ProfileRevisionStore(profilesRoot);
        ProfileRevisionManifest first = revisions.createCandidate(candidate(created, "recipe-a"));
        ProfileTestArtifactFactory.assemble(profilesRoot, APPLICATION_ID, first,
                temporaryFolder.newFolder("first-artifacts").toPath());
        Path firstReports = reportDirectory("first", passingReport(created, first));
        ProfilePublishService service = new ProfilePublishService(profilesRoot, activeProfilesRoot);

        ActiveRevisionPointer firstActive = service.publish(APPLICATION_ID, null,
                first.getRevisionId(), firstReports);

        Assert.assertEquals(first.getRevisionId(), firstActive.getRevisionId());
        ProfileRevisionManifest publishedFirst = revisions.load(created.getProfileId(),
                first.getRevisionId());
        Assert.assertEquals(RevisionStatus.ACTIVE, publishedFirst.getStatus());
        Assert.assertNotNull(publishedFirst.getGateReportSha256());
        Assert.assertTrue(Files.isRegularFile(revisions.getRevisionManifestPath(created.getProfileId(),
                first.getRevisionId()).getParent().resolve("reports/gate-report.json")));
        Path firstPublished = activeProfilesRoot.resolve(created.getProfileId())
                .resolve("revisions").resolve(first.getRevisionId());
        Assert.assertTrue(Files.isRegularFile(firstPublished.resolve("profile.json")));
        Assert.assertFalse(Files.getPosixFilePermissions(firstPublished.resolve("profile.json"))
                .contains(PosixFilePermission.OWNER_WRITE));

        ProfileRevisionManifest second = revisions.createCandidate(candidate(created, "recipe-b"));
        ProfileTestArtifactFactory.assemble(profilesRoot, APPLICATION_ID, second,
                temporaryFolder.newFolder("second-artifacts").toPath());
        service.publish(null, created.getProfileId(), second.getRevisionId(),
                reportDirectory("second", passingReport(created, second)));
        ActiveRevisionPointer rolledBack = service.rollback(APPLICATION_ID, created.getProfileId(),
                first.getRevisionId());

        Assert.assertEquals(first.getRevisionId(), rolledBack.getRevisionId());
        Assert.assertEquals(first.getRevisionId(), new ProfileActiveRevisionStore(profilesRoot,
                activeProfilesRoot, revisions).loadActive(created.getProfileId()).getRevisionId());
    }

    @Test
    public void failedGateReportRejectsCandidateAndPreservesOldActiveRevision() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("profiles").toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("active").toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot).create(APPLICATION_ID);
        ProfileRevisionStore revisions = new ProfileRevisionStore(profilesRoot);
        ProfilePublishService service = new ProfilePublishService(profilesRoot, activeProfilesRoot);
        ProfileRevisionManifest first = revisions.createCandidate(candidate(created, "recipe-a"));
        ProfileTestArtifactFactory.assemble(profilesRoot, APPLICATION_ID, first,
                temporaryFolder.newFolder("active-artifacts").toPath());
        service.publish(APPLICATION_ID, null, first.getRevisionId(),
                reportDirectory("first", passingReport(created, first)));
        ProfileRevisionManifest failed = revisions.createCandidate(candidate(created, "recipe-b"));
        ProfileTestArtifactFactory.assemble(profilesRoot, APPLICATION_ID, failed,
                temporaryFolder.newFolder("failed-artifacts").toPath());
        ProfileGateReport report = passingReport(created, failed);
        report.setOverallStatus(ProfileGateReport.Status.FAILED);
        report.getResults().get("performance").setStatus(ProfileGateReport.Status.FAILED);

        try {
            service.publish(APPLICATION_ID, null, failed.getRevisionId(),
                    reportDirectory("failed", report));
            Assert.fail("Expected failed hard gate rejection");
        } catch (IOException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("performance"));
        }

        Assert.assertEquals(RevisionStatus.REJECTED,
                revisions.load(created.getProfileId(), failed.getRevisionId()).getStatus());
        Assert.assertEquals(first.getRevisionId(), new ProfileActiveRevisionStore(profilesRoot,
                activeProfilesRoot, revisions).loadActive(created.getProfileId()).getRevisionId());
    }

    @Test
    public void missingGateReportKeepsCandidateAndOldPointerUnchanged() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("profiles").toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("active").toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot).create(APPLICATION_ID);
        ProfileRevisionStore revisions = new ProfileRevisionStore(profilesRoot);
        ProfileRevisionManifest candidate = revisions.createCandidate(candidate(created, "recipe-a"));
        ProfileTestArtifactFactory.assemble(profilesRoot, APPLICATION_ID, candidate,
                temporaryFolder.newFolder("missing-report-artifacts").toPath());

        try {
            new ProfilePublishService(profilesRoot, activeProfilesRoot).publish(APPLICATION_ID, null,
                    candidate.getRevisionId(), temporaryFolder.newFolder("missing").toPath());
            Assert.fail("Expected missing report failure");
        } catch (IOException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("gate-report.json"));
        }

        Assert.assertEquals(RevisionStatus.CANDIDATE,
                revisions.load(created.getProfileId(), candidate.getRevisionId()).getStatus());
        Assert.assertFalse(Files.exists(activeProfilesRoot.resolve(created.getProfileId())
                .resolve("active.json")));
    }

    @Test
    public void incomparableManualScanEvidenceRejectsCandidateAndPreservesActivePointer()
            throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("scan-profiles").toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("scan-active").toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot)
                .create(APPLICATION_ID);
        ProfileRevisionStore revisions = new ProfileRevisionStore(profilesRoot);
        ProfileRevisionManifest candidate = revisions.createCandidate(candidate(created,
                "recipe-a"));
        ProfileTestArtifactFactory.assemble(profilesRoot, APPLICATION_ID, candidate,
                temporaryFolder.newFolder("scan-artifacts").toPath());
        ProfileGateReport gateReport = passingReport(created, candidate);
        Path reportDirectory = reportDirectory("scan-report", gateReport);

        ProfileScanReport baseline = scanReport("a".repeat(64));
        ProfileScanReport scanCandidate = scanReport("d".repeat(64));
        scanCandidate.setWindowId("different-window");
        ProfileScanReportCodec.write(reportDirectory.resolve("scan-baseline.json"), baseline);
        ProfileScanReportCodec.write(reportDirectory.resolve("scan-candidate.json"),
                scanCandidate);

        try {
            new ProfilePublishService(profilesRoot, activeProfilesRoot).publish(APPLICATION_ID,
                    null, candidate.getRevisionId(), reportDirectory);
            Assert.fail("Expected incomparable scan evidence rejection");
        } catch (IOException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("统一发布门禁"));
        }
        Assert.assertEquals(RevisionStatus.REJECTED,
                revisions.load(created.getProfileId(), candidate.getRevisionId()).getStatus());
        Assert.assertFalse(Files.exists(activeProfilesRoot.resolve(created.getProfileId())
                .resolve("active.json")));
    }

    @Test
    public void strictPublishRejectsMissingManualScanPair() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("strict-scan-profiles").toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("strict-scan-active").toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot)
                .create(APPLICATION_ID);
        ProfileRevisionStore revisions = new ProfileRevisionStore(profilesRoot);
        ProfileRevisionManifest candidate = revisions.createCandidate(candidate(created,
                "recipe-a"));
        ProfileTestArtifactFactory.assemble(profilesRoot, APPLICATION_ID, candidate,
                temporaryFolder.newFolder("strict-scan-artifacts").toPath());
        Path reportDirectory = reportDirectory("strict-scan-report",
                passingReport(created, candidate));

        try {
            new ProfilePublishService(profilesRoot, activeProfilesRoot).publish(
                    APPLICATION_ID, null, candidate.getRevisionId(), reportDirectory, null, null);
            Assert.fail("Expected strict manual scan evidence rejection");
        } catch (IOException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("手工扫描"));
        }
        Assert.assertEquals(RevisionStatus.REJECTED,
                revisions.load(created.getProfileId(), candidate.getRevisionId()).getStatus());
        Assert.assertFalse(Files.exists(activeProfilesRoot.resolve(created.getProfileId())
                .resolve("active.json")));
    }

    @Test
    public void strictPublishKeepsOldActiveForEveryFailedGateAndThenActivatesFullSuccess()
            throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("strict-e2e-profiles").toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("strict-e2e-active").toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot)
                .create(APPLICATION_ID);
        ProfileRevisionStore revisions = new ProfileRevisionStore(profilesRoot);
        ProfilePublishService service = new ProfilePublishService(profilesRoot,
                activeProfilesRoot);

        ProfileRevisionManifest first = revisions.createCandidate(candidate(created,
                "recipe-a"));
        ProfileTestArtifactFactory.assemble(profilesRoot, APPLICATION_ID, first,
                temporaryFolder.newFolder("strict-e2e-first-artifacts").toPath());
        service.publishStrict(APPLICATION_ID, null, first.getRevisionId(),
                reportDirectoryWithScans("strict-e2e-first-report",
                        passingReport(created, first)));
        Path activePointer = activeProfilesRoot.resolve(created.getProfileId())
                .resolve("active.json");
        byte[] firstPointer = Files.readAllBytes(activePointer);

        for (String failedGate : REQUIRED_GATES) {
            ProfileRevisionManifest failed = revisions.createCandidate(candidate(created,
                    "recipe-b"));
            ProfileTestArtifactFactory.assemble(profilesRoot, APPLICATION_ID, failed,
                    temporaryFolder.newFolder("strict-e2e-" + failedGate + "-artifacts")
                            .toPath());
            ProfileGateReport failedReport = passingReport(created, failed);
            failedReport.setOverallStatus(ProfileGateReport.Status.FAILED);
            failedReport.getResults().get(failedGate)
                    .setStatus(ProfileGateReport.Status.FAILED);

            try {
                service.publishStrict(APPLICATION_ID, null, failed.getRevisionId(),
                        reportDirectoryWithScans("strict-e2e-" + failedGate + "-report",
                                failedReport));
                Assert.fail("Expected failed hard gate rejection: " + failedGate);
            } catch (IOException expected) {
                Assert.assertTrue(expected.getMessage(),
                        expected.getMessage().contains(failedGate));
            }

            Assert.assertEquals(RevisionStatus.REJECTED,
                    revisions.load(created.getProfileId(), failed.getRevisionId()).getStatus());
            Assert.assertArrayEquals(firstPointer, Files.readAllBytes(activePointer));
        }

        ProfileRevisionManifest successful = revisions.createCandidate(candidate(created,
                "recipe-b"));
        ProfileTestArtifactFactory.assemble(profilesRoot, APPLICATION_ID, successful,
                temporaryFolder.newFolder("strict-e2e-success-artifacts").toPath());
        ActiveRevisionPointer active = service.publishStrict(APPLICATION_ID, null,
                successful.getRevisionId(), reportDirectoryWithScans(
                        "strict-e2e-success-report", passingReport(created, successful)));

        Assert.assertEquals(successful.getRevisionId(), active.getRevisionId());
        Assert.assertEquals(successful.getRevisionId(), new ProfileActiveRevisionStore(
                profilesRoot, activeProfilesRoot, revisions)
                .loadActive(created.getProfileId()).getRevisionId());
    }

    @Test
    public void strictPublishFailsClosedForIncomparableReportsAndKeepsOldActive()
            throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("strict-incomparable-profiles").toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("strict-incomparable-active").toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot)
                .create(APPLICATION_ID);
        ProfileRevisionStore revisions = new ProfileRevisionStore(profilesRoot);
        ProfilePublishService service = new ProfilePublishService(profilesRoot,
                activeProfilesRoot);
        ProfileRevisionManifest first = revisions.createCandidate(candidate(created,
                "recipe-a"));
        ProfileTestArtifactFactory.assemble(profilesRoot, APPLICATION_ID, first,
                temporaryFolder.newFolder("strict-incomparable-first-artifacts").toPath());
        service.publishStrict(APPLICATION_ID, null, first.getRevisionId(),
                reportDirectoryWithScans("strict-incomparable-first-report",
                        passingReport(created, first)));
        Path activePointer = activeProfilesRoot.resolve(created.getProfileId())
                .resolve("active.json");
        byte[] pointerBefore = Files.readAllBytes(activePointer);

        ProfileRevisionManifest candidate = revisions.createCandidate(candidate(created,
                "recipe-b"));
        ProfileTestArtifactFactory.assemble(profilesRoot, APPLICATION_ID, candidate,
                temporaryFolder.newFolder("strict-incomparable-artifacts").toPath());
        Path reportDirectory = reportDirectory("strict-incomparable-report",
                passingReport(created, candidate));
        ProfileScanReport baseline = scanReport("a".repeat(64));
        ProfileScanReport scanCandidate = scanReport("c".repeat(64));
        scanCandidate.setWindowId("other-window");
        ProfileScanReportCodec.write(reportDirectory.resolve("scan-baseline.json"), baseline);
        ProfileScanReportCodec.write(reportDirectory.resolve("scan-candidate.json"),
                scanCandidate);

        try {
            service.publishStrict(APPLICATION_ID, null, candidate.getRevisionId(),
                    reportDirectory);
            Assert.fail("Expected incomparable scan report rejection");
        } catch (IOException expected) {
            Assert.assertTrue(expected.getMessage(),
                    expected.getMessage().contains("扫描")
                            || expected.getMessage().contains("scan"));
        }

        Assert.assertEquals(RevisionStatus.REJECTED,
                revisions.load(created.getProfileId(), candidate.getRevisionId()).getStatus());
        Assert.assertArrayEquals(pointerBefore, Files.readAllBytes(activePointer));
    }

    @Test
    public void passingGatesCannotPublishWithoutAValidatedCandidateBundle() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("missing-bundle-profiles").toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("missing-bundle-active").toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot)
                .create(APPLICATION_ID);
        ProfileRevisionManifest candidate = new ProfileRevisionStore(profilesRoot)
                .createCandidate(candidate(created, "recipe-a"));

        try {
            new ProfilePublishService(profilesRoot, activeProfilesRoot).publish(
                    APPLICATION_ID, null, candidate.getRevisionId(),
                    reportDirectory("missing-bundle-report", passingReport(created, candidate)));
            Assert.fail("Expected missing Bundle rejection");
        } catch (IOException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("Bundle"));
        }

        Assert.assertEquals(RevisionStatus.CANDIDATE,
                new ProfileRevisionStore(profilesRoot).load(created.getProfileId(),
                        candidate.getRevisionId()).getStatus());
        Assert.assertFalse(Files.exists(activeProfilesRoot.resolve(created.getProfileId())
                .resolve("revisions").resolve(candidate.getRevisionId())));
    }

    @Test
    public void activePointerRejectsPublishedBundleWhoseHashNoLongerMatches() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("tampered-profiles").toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("tampered-active").toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot)
                .create(APPLICATION_ID);
        ProfileRevisionStore revisions = new ProfileRevisionStore(profilesRoot);
        ProfileRevisionManifest candidate = revisions.createCandidate(candidate(created,
                "recipe-a"));
        ProfileTestArtifactFactory.assemble(profilesRoot, APPLICATION_ID, candidate,
                temporaryFolder.newFolder("tampered-artifacts").toPath());
        new ProfilePublishService(profilesRoot, activeProfilesRoot).publish(APPLICATION_ID,
                null, candidate.getRevisionId(),
                reportDirectory("tampered-report", passingReport(created, candidate)));

        Path publishedDex = activeProfilesRoot.resolve(created.getProfileId())
                .resolve("revisions").resolve(candidate.getRevisionId())
                .resolve("shell-files/dex/classes.dex");
        publishedDex.toFile().setWritable(true, false);
        Files.write(publishedDex, new byte[]{1, 2, 3});

        try {
            new ProfileActiveRevisionStore(profilesRoot, activeProfilesRoot, revisions)
                    .loadActive(created.getProfileId());
            Assert.fail("Expected active Bundle integrity failure");
        } catch (ProfileActiveRevisionException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("Bundle"));
        }
    }

    @Test
    public void rollbackRejectsDamagedHistoricalBundleAndKeepsCurrentPointerUnchanged()
            throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("rollback-profiles").toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("rollback-active").toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot)
                .create(APPLICATION_ID);
        ProfileRevisionStore revisions = new ProfileRevisionStore(profilesRoot);
        ProfilePublishService service = new ProfilePublishService(profilesRoot,
                activeProfilesRoot);

        ProfileRevisionManifest first = revisions.createCandidate(candidate(created,
                "recipe-a"));
        ProfileTestArtifactFactory.assemble(profilesRoot, APPLICATION_ID, first,
                temporaryFolder.newFolder("rollback-first-artifacts").toPath());
        service.publish(APPLICATION_ID, null, first.getRevisionId(),
                reportDirectory("rollback-first-report", passingReport(created, first)));
        ProfileRevisionManifest second = revisions.createCandidate(candidate(created,
                "recipe-b"));
        ProfileTestArtifactFactory.assemble(profilesRoot, APPLICATION_ID, second,
                temporaryFolder.newFolder("rollback-second-artifacts").toPath());
        service.publish(APPLICATION_ID, null, second.getRevisionId(),
                reportDirectory("rollback-second-report", passingReport(created, second)));

        Path pointer = activeProfilesRoot.resolve(created.getProfileId()).resolve("active.json");
        byte[] pointerBefore = Files.readAllBytes(pointer);
        Path historicalDex = activeProfilesRoot.resolve(created.getProfileId())
                .resolve("revisions").resolve(first.getRevisionId())
                .resolve("shell-files/dex/classes.dex");
        historicalDex.toFile().setWritable(true, false);
        Files.write(historicalDex, new byte[]{1, 2, 3});

        try {
            service.rollback(APPLICATION_ID, null, first.getRevisionId());
            Assert.fail("Expected damaged historical Bundle rejection");
        } catch (ProfileCommandException expected) {
            Assert.assertEquals(ProfileErrorCode.ILLEGAL_ROLLBACK,
                    expected.getErrorCode());
        }

        Assert.assertArrayEquals(pointerBefore, Files.readAllBytes(pointer));
        Assert.assertEquals(second.getRevisionId(), new ProfileActiveRevisionStore(
                profilesRoot, activeProfilesRoot, revisions)
                .loadActive(created.getProfileId()).getRevisionId());
    }

    private Path reportDirectory(String name, ProfileGateReport report) throws IOException {
        Path directory = temporaryFolder.newFolder(name).toPath();
        Files.writeString(directory.resolve("gate-report.json"), JSON.toJSONString(report),
                StandardCharsets.UTF_8);
        return directory;
    }

    private Path reportDirectoryWithScans(String name, ProfileGateReport report)
            throws IOException {
        Path directory = reportDirectory(name, report);
        ProfileScanReportCodec.write(directory.resolve("scan-baseline.json"),
                scanReport("a".repeat(64)));
        ProfileScanReportCodec.write(directory.resolve("scan-candidate.json"),
                scanReport(report.getCandidateApkSha256()));
        return directory;
    }

    private static ProfileGateReport passingReport(ProfileCreationResult created,
                                                   ProfileRevisionManifest revision) {
        ProfileGateReport report = new ProfileGateReport();
        report.setProfileId(created.getProfileId());
        report.setRevisionId(revision.getRevisionId());
        report.setCandidateApkSha256("c".repeat(64));
        report.setOverallStatus(ProfileGateReport.Status.PASSED);
        report.setGeneratedAtEpochMillis(System.currentTimeMillis());
        LinkedHashMap<String, ProfileGateReport.Result> results = new LinkedHashMap<>();
        for (String gate : REQUIRED_GATES) {
            ProfileGateReport.Result result = new ProfileGateReport.Result();
            result.setStatus(ProfileGateReport.Status.PASSED);
            results.put(gate, result);
        }
        report.setResults(results);
        return report;
    }

    private static ProfileRevisionManifest candidate(ProfileCreationResult created, String recipeId) {
        ProfileRevisionManifest value = new ProfileRevisionManifest();
        value.setProfileId(created.getProfileId());
        value.setApplicationId(APPLICATION_ID);
        value.setRecipeId(recipeId);
        value.setOverlaySha256("a".repeat(64));
        value.setGeneratorVersion(ProfileCodeGenerator.GENERATOR_VERSION);
        value.setCoreVersion(ProfileCodeGenerator.IMMUTABLE_CORE_VERSION);
        value.setToolchainVersion("toolchain-1");
        value.setGeneratedSourceSha256("b".repeat(64));
        return value;
    }

    private static ProfileScanReport scanReport(String apkSha256) {
        ProfileScanReport report = new ProfileScanReport();
        report.setApplicationId(APPLICATION_ID);
        report.setApkSha256(apkSha256);
        report.setGeneratedAtEpochMillis(1_700_000_000_000L);
        report.setWindowId("window-1");
        report.setWindowStartEpochMillis(1_700_000_000_000L);
        report.setWindowEndEpochMillis(1_700_000_360_000L);
        LinkedHashMap<String, String> scanners = new LinkedHashMap<>();
        scanners.put("engine", "1");
        report.setScannerSet(scanners);
        return report;
    }
}
