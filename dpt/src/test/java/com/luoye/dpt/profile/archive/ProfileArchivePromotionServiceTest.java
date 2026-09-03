package com.luoye.dpt.profile.archive;

import com.alibaba.fastjson2.JSON;
import com.luoye.dpt.profile.ProfileTestArtifactFactory;
import com.luoye.dpt.profile.gate.ProfileUnifiedGate;
import com.luoye.dpt.profile.gate.scan.ProfileScanReport;
import com.luoye.dpt.profile.gate.scan.ProfileScanReportCodec;
import com.luoye.dpt.profile.generation.ProfileCodeGenerator;
import com.luoye.dpt.profile.model.ProfileGateReport;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.storage.ProfileBuildService;
import com.luoye.dpt.profile.storage.ProfileBundle;
import com.luoye.dpt.profile.storage.ProfileCreationResult;
import com.luoye.dpt.profile.storage.ProfileCreationService;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;

public class ProfileArchivePromotionServiceTest {
    private static final String APPLICATION_ID = "com.example.archivepromote";
    private static final String APK_SHA256 = "c".repeat(64);

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void promotesPassingCandidateAndEmbedsReports() throws Exception {
        Fixture fixture = fixture();
        Path candidate = temporaryFolder.newFolder("candidate").toPath()
                .resolve("profile.candidate.dptprofile");
        new ProfileArchiveWriter().packCandidate(fixture.bundle.getRoot(), candidate);
        Path reports = reports(fixture, "reports");
        Path output = temporaryFolder.newFolder("verified").toPath().resolve("profile.dptprofile");

        new ProfileArchivePromotionService().promote(candidate, reports, output);

        try (ProfileArchiveBundle verified = new ProfileArchiveReader().readVerified(output)) {
            Assert.assertEquals(ProfileArchiveStatus.VERIFIED, verified.getStatus());
            Assert.assertEquals(fixture.revision.getRevisionId(), verified.getRevisionId());
            Assert.assertTrue(Files.isRegularFile(verified.getRoot().resolve("reports/gate-report.json")));
            Assert.assertTrue(Files.isRegularFile(verified.getRoot().resolve("reports/scan-baseline.json")));
            Assert.assertTrue(Files.isRegularFile(verified.getRoot().resolve("reports/scan-candidate.json")));
        }
    }

    @Test
    public void failedPromotionDoesNotCreateOrReplaceOutput() throws Exception {
        Fixture fixture = fixture();
        Path candidate = temporaryFolder.newFolder("candidate-fail").toPath()
                .resolve("profile.candidate.dptprofile");
        new ProfileArchiveWriter().packCandidate(fixture.bundle.getRoot(), candidate);
        Path reports = reports(fixture, "reports-fail");
        Files.writeString(reports.resolve("gate-report.json"),
                JSON.toJSONString(passingReport(fixture)), StandardCharsets.UTF_8);
        ProfileGateReport failed = passingReport(fixture);
        failed.getResults().get("contract").setStatus(ProfileGateReport.Status.FAILED);
        Files.writeString(reports.resolve("gate-report.json"), JSON.toJSONString(failed),
                StandardCharsets.UTF_8);
        Path output = temporaryFolder.newFolder("verified-fail").toPath().resolve("profile.dptprofile");

        try {
            new ProfileArchivePromotionService().promote(candidate, reports, output);
            Assert.fail("failed gate must reject promotion");
        } catch (Exception expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("门禁"));
        }
        Assert.assertFalse(Files.exists(output));
    }

    @Test
    public void rejectsExistingVerifiedOutputBeforeWriting() throws Exception {
        Fixture fixture = fixture();
        Path candidate = temporaryFolder.newFolder("candidate-existing").toPath()
                .resolve("profile.candidate.dptprofile");
        new ProfileArchiveWriter().packCandidate(fixture.bundle.getRoot(), candidate);
        Path reports = reports(fixture, "reports-existing");
        Path output = temporaryFolder.newFolder("verified-existing").toPath().resolve("profile.dptprofile");
        Files.writeString(output, "old", StandardCharsets.UTF_8);

        try {
            new ProfileArchivePromotionService().promote(candidate, reports, output);
            Assert.fail("existing output must not be replaced");
        } catch (Exception expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("已存在"));
        }
        Assert.assertEquals("old", Files.readString(output, StandardCharsets.UTF_8));
    }

    @Test
    public void verifiedReaderRejectsVerifiedContentWithCandidateFilename() throws Exception {
        Fixture fixture = fixture();
        Path candidate = temporaryFolder.newFolder("candidate-suffix").toPath()
                .resolve("profile.candidate.dptprofile");
        new ProfileArchiveWriter().packCandidate(fixture.bundle.getRoot(), candidate);
        Path verified = temporaryFolder.newFolder("verified-suffix").toPath()
                .resolve("profile.dptprofile");
        new ProfileArchivePromotionService().promote(candidate, reports(fixture, "reports-suffix"),
                verified);
        Path renamed = temporaryFolder.newFolder("renamed-suffix").toPath()
                .resolve("profile.candidate.dptprofile");
        Files.copy(verified, renamed);

        try (ProfileArchiveBundle ignored = new ProfileArchiveReader().readVerified(renamed)) {
            Assert.fail("verified content with candidate filename must be rejected");
        } catch (Exception expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("文件名"));
        }
    }

    private Fixture fixture() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("profiles-" + System.nanoTime()).toPath();
        Path activeRoot = temporaryFolder.newFolder("active-" + System.nanoTime()).toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot).create(APPLICATION_ID);
        ProfileRevisionManifest revision = new ProfileBuildService(profilesRoot, activeRoot)
                .build(APPLICATION_ID, null, "recipe-a", null);
        ProfileBundle bundle = ProfileTestArtifactFactory.assemble(profilesRoot, APPLICATION_ID,
                revision, temporaryFolder.newFolder("artifacts-" + System.nanoTime()).toPath());
        return new Fixture(created, revision, bundle);
    }

    private Path reports(Fixture fixture, String name) throws Exception {
        Path root = temporaryFolder.newFolder(name).toPath();
        Files.writeString(root.resolve("gate-report.json"), JSON.toJSONString(passingReport(fixture)),
                StandardCharsets.UTF_8);
        ProfileScanReport baseline = scanReport();
        ProfileScanReport candidate = scanReport(baseline);
        ProfileScanReportCodec.write(root.resolve("scan-baseline.json"), baseline);
        ProfileScanReportCodec.write(root.resolve("scan-candidate.json"), candidate);
        return root;
    }

    private static ProfileGateReport passingReport(Fixture fixture) {
        ProfileGateReport report = new ProfileGateReport();
        report.setProfileId(fixture.created.getProfileId());
        report.setRevisionId(fixture.revision.getRevisionId());
        report.setCandidateApkSha256(APK_SHA256);
        report.setOverallStatus(ProfileGateReport.Status.PASSED);
        report.setGeneratedAtEpochMillis(System.currentTimeMillis());
        LinkedHashMap<String, ProfileGateReport.Result> results = new LinkedHashMap<>();
        for (String gate : ProfileUnifiedGate.REQUIRED_GATE_IDS) {
            ProfileGateReport.Result result = new ProfileGateReport.Result();
            result.setStatus(ProfileGateReport.Status.PASSED);
            results.put(gate, result);
        }
        report.setResults(results);
        return report;
    }

    private static ProfileScanReport scanReport() {
        return scanReport(null);
    }

    private static ProfileScanReport scanReport(ProfileScanReport template) {
        ProfileScanReport report = new ProfileScanReport();
        report.setApplicationId(APPLICATION_ID);
        report.setApkSha256(APK_SHA256);
        long now = template == null ? System.currentTimeMillis()
                : template.getGeneratedAtEpochMillis();
        report.setGeneratedAtEpochMillis(now);
        report.setWindowId(template == null ? "window-1" : template.getWindowId());
        report.setWindowStartEpochMillis(template == null
                ? now - 1000 : template.getWindowStartEpochMillis());
        report.setWindowEndEpochMillis(template == null
                ? now + 1000 : template.getWindowEndEpochMillis());
        LinkedHashMap<String, String> scanners = new LinkedHashMap<>();
        scanners.put("scanner-a", "1");
        report.setScannerSet(scanners);
        report.setDetections(new java.util.ArrayList<>());
        return report;
    }

    private static final class Fixture {
        private final ProfileCreationResult created;
        private final ProfileRevisionManifest revision;
        private final ProfileBundle bundle;

        private Fixture(ProfileCreationResult created, ProfileRevisionManifest revision,
                        ProfileBundle bundle) {
            this.created = created;
            this.revision = revision;
            this.bundle = bundle;
        }
    }
}
