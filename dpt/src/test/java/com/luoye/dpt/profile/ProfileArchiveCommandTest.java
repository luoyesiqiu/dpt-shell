package com.luoye.dpt.profile;

import com.alibaba.fastjson2.JSON;
import com.luoye.dpt.profile.archive.ProfileArchiveStatus;
import com.luoye.dpt.profile.archive.ProfileArchiveWriter;
import com.luoye.dpt.profile.cli.ProfileCommandMain;
import com.luoye.dpt.profile.error.ProfileCommandException;
import com.luoye.dpt.profile.error.ProfileErrorCode;
import com.luoye.dpt.profile.gate.ProfileUnifiedGate;
import com.luoye.dpt.profile.gate.scan.ProfileScanReport;
import com.luoye.dpt.profile.gate.scan.ProfileScanReportCodec;
import com.luoye.dpt.profile.model.ProfileGateReport;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.storage.ProfileBuildService;
import com.luoye.dpt.profile.storage.ProfileBundle;
import com.luoye.dpt.profile.storage.ProfileBundleValidator;
import com.luoye.dpt.profile.storage.ProfileCreationResult;
import com.luoye.dpt.profile.storage.ProfileCreationService;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import pxb.android.axml.AxmlWriter;
import pxb.android.axml.NodeVisitor;

public class ProfileArchiveCommandTest {
    private static final String APPLICATION_ID = "com.example.archivecommand";
    private static final String APK_SHA256 = "d".repeat(64);

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void packAndValidateWorkWithoutProfilesRoot() throws Exception {
        Fixture fixture = fixture();
        Path output = temporaryFolder.newFolder("packed").toPath()
                .resolve("profile.candidate.dptprofile");
        String sourceBefore = Files.readString(fixture.bundle.getRoot().resolve("revision.json"));

        String packOutput = invoke("pack", "--bundle", fixture.bundle.getRoot().toString(),
                "--out", output.toString());

        Assert.assertTrue(packOutput, packOutput.startsWith("PROFILE_PACK_RESULT "));
        Assert.assertTrue(Files.isRegularFile(output));
        Assert.assertEquals(sourceBefore,
                Files.readString(fixture.bundle.getRoot().resolve("revision.json")));

        try {
            invoke("validate", "--profile", output.toString());
            Assert.fail("candidate must not validate as a consumable profile");
        } catch (Exception expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("candidate"));
        }
    }

    @Test
    public void promoteAndValidateVerifiedArchiveThroughCli() throws Exception {
        Fixture fixture = fixture();
        Path candidate = temporaryFolder.newFolder("candidate").toPath()
                .resolve("profile.candidate.dptprofile");
        new ProfileArchiveWriter().packCandidate(fixture.bundle.getRoot(), candidate);
        Path reports = reports(fixture, "reports");
        Path output = temporaryFolder.newFolder("verified").toPath().resolve("profile.dptprofile");

        String promoteOutput = invoke("promote", "--candidate", candidate.toString(), "--reports",
                reports.toString(), "--out", output.toString());
        Assert.assertTrue(promoteOutput, promoteOutput.startsWith("PROFILE_PROMOTE_RESULT "));

        String validateOutput = invoke("validate", "--profile", output.toString());
        Assert.assertTrue(validateOutput, validateOutput.startsWith("PROFILE_VALIDATE_RESULT "));
        Assert.assertTrue(validateOutput, validateOutput.contains("\"status\":\"VERIFIED\""));
    }

    @Test
    public void validateWithApkChecksApplicationIdentityAndBusinessAbi() throws Exception {
        Fixture fixture = fixture();
        Path candidate = temporaryFolder.newFolder("candidate-apk").toPath()
                .resolve("profile.candidate.dptprofile");
        new ProfileArchiveWriter().packCandidate(fixture.bundle.getRoot(), candidate);
        Path reports = reports(fixture, "reports-apk");
        Path verified = temporaryFolder.newFolder("verified-apk").toPath().resolve("profile.dptprofile");
        new com.luoye.dpt.profile.archive.ProfileArchivePromotionService()
                .promote(candidate, reports, verified);
        Path apk = temporaryFolder.newFile("input.apk").toPath();
        writeApk(apk, APPLICATION_ID, "x86_64");

        try {
            invoke("validate", "--profile", verified.toString(), "--apk", apk.toString());
            Assert.fail("APK without arm64 business ABI must fail");
        } catch (ProfileCommandException expected) {
            Assert.assertEquals(ProfileErrorCode.APK_ABI_INCOMPATIBLE, expected.getErrorCode());
        }
    }

    private String invoke(String... args) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream previous = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            ProfileCommandMain.main(args);
        } finally {
            System.setOut(previous);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private Fixture fixture() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("profiles-" + System.nanoTime()).toPath();
        Path activeRoot = temporaryFolder.newFolder("active-" + System.nanoTime()).toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot).create(APPLICATION_ID);
        ProfileRevisionManifest revision = new ProfileBuildService(profilesRoot, activeRoot)
                .build(APPLICATION_ID, null, "recipe-a", null);
        ProfileTestArtifactFactory.assemble(profilesRoot, APPLICATION_ID, revision,
                temporaryFolder.newFolder("artifacts-" + System.nanoTime()).toPath());
        ProfileBundle bundle = new ProfileBundleValidator(profilesRoot)
                .validateCandidate(APPLICATION_ID, created.getProfileId(), revision.getRevisionId());
        return new Fixture(created, revision, bundle);
    }

    private Path reports(Fixture fixture, String name) throws Exception {
        Path root = temporaryFolder.newFolder(name).toPath();
        Files.writeString(root.resolve("gate-report.json"), JSON.toJSONString(gateReport(fixture)),
                StandardCharsets.UTF_8);
        ProfileScanReport baseline = scanReport();
        ProfileScanReport candidate = scanReport(baseline);
        ProfileScanReportCodec.write(root.resolve("scan-baseline.json"), baseline);
        ProfileScanReportCodec.write(root.resolve("scan-candidate.json"), candidate);
        return root;
    }

    private static ProfileGateReport gateReport(Fixture fixture) {
        ProfileGateReport report = new ProfileGateReport();
        report.setProfileId(fixture.created.getProfileId());
        report.setRevisionId(fixture.revision.getRevisionId());
        report.setCandidateApkSha256(APK_SHA256);
        report.setOverallStatus(ProfileGateReport.Status.PASSED);
        report.setGeneratedAtEpochMillis(System.currentTimeMillis());
        LinkedHashMap<String, ProfileGateReport.Result> results = new LinkedHashMap<>();
        for (String id : ProfileUnifiedGate.REQUIRED_GATE_IDS) {
            ProfileGateReport.Result result = new ProfileGateReport.Result();
            result.setStatus(ProfileGateReport.Status.PASSED);
            results.put(id, result);
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

    private static void writeApk(Path apk, String applicationId, String abi) throws Exception {
        AxmlWriter writer = new AxmlWriter();
        writer.ns("android", "http://schemas.android.com/apk/res/android", 1);
        NodeVisitor manifest = writer.child(null, "manifest");
        manifest.attr(null, "package", -1, NodeVisitor.TYPE_STRING, applicationId);
        NodeVisitor application = manifest.child(null, "application");
        application.end();
        manifest.end();
        writer.end();
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(apk.toFile()))) {
            output.putNextEntry(new ZipEntry("AndroidManifest.xml"));
            output.write(writer.toByteArray());
            output.closeEntry();
            output.putNextEntry(new ZipEntry("lib/" + abi + "/libbusiness.so"));
            output.write(new byte[] {1});
            output.closeEntry();
        }
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
