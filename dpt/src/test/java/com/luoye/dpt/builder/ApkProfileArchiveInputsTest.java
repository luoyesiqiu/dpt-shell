package com.luoye.dpt.builder;

import com.luoye.dpt.profile.ProfileTestArtifactFactory;
import com.luoye.dpt.profile.archive.ProfileArchivePromotionService;
import com.luoye.dpt.profile.archive.ProfileArchiveWriter;
import com.luoye.dpt.profile.gate.ProfileUnifiedGate;
import com.luoye.dpt.profile.gate.scan.ProfileScanReport;
import com.luoye.dpt.profile.gate.scan.ProfileScanReportCodec;
import com.luoye.dpt.profile.model.ProfileContract;
import com.luoye.dpt.profile.model.ProfileGateReport;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.storage.ProfileBuildService;
import com.luoye.dpt.profile.storage.ProfileBundle;
import com.luoye.dpt.profile.storage.ProfileBundleValidator;
import com.luoye.dpt.profile.storage.ProfileCreationResult;
import com.luoye.dpt.profile.storage.ProfileCreationService;
import com.luoye.dpt.Dpt;
import com.luoye.dpt.profile.error.ProfileCommandException;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;

import pxb.android.axml.AxmlWriter;
import pxb.android.axml.NodeVisitor;

public class ApkProfileArchiveInputsTest {
    private static final String APPLICATION_ID = "com.example.archiveinputs";
    private static final String APK_SHA256 = "e".repeat(64);

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void explicitVerifiedArchiveIsUsedWithoutRegistryAndExposesAbiRoot() throws Exception {
        Path archive = verifiedArchive();
        Path unpacked = temporaryFolder.newFolder("unpacked").toPath();
        writeManifest(unpacked, APPLICATION_ID);
        Path builderProfiles = temporaryFolder.newFolder("unused-profiles").toPath();
        Path builderActive = temporaryFolder.newFolder("unused-active").toPath();
        InspectableApk apk = new InspectableApk(new Apk.Builder()
                .profileRoots(builderProfiles, builderActive)
                .profileArchive(archive));

        apk.resolveProfileInputs(unpacked);

        ProfileContract contract = apk.profileContract();
        Assert.assertEquals(contract.getProfileId(), apk.profileContract().getProfileId());
        Assert.assertTrue(Files.isRegularFile(apk.proxyDexPath()));
        Assert.assertTrue(Files.isRegularFile(apk.shellNativeLibsRoot()
                .resolve("arm64-v8a").resolve(contract.getSoName())));
        Assert.assertFalse("explicit archive must not create registry state",
                Files.exists(builderProfiles.resolve("index.json")));
        Assert.assertTrue(apk.shellNativeLibsRoot().endsWith("shell-files/libs"));
    }

    @Test
    public void explicitArchiveMapsCanonicalAbiToRuntimeAbiDirectory() throws Exception {
        Path archive = verifiedArchive();
        Path unpacked = temporaryFolder.newFolder("runtime-abi-unpacked").toPath();
        Files.createDirectories(unpacked.resolve("assets"));
        writeManifest(unpacked, APPLICATION_ID);
        InspectableApk apk = new InspectableApk(new Apk.Builder().profileArchive(archive));

        apk.resolveProfileInputs(unpacked);
        apk.copyNativeLibs(unpacked.toString());

        ProfileContract contract = apk.profileContract();
        Path runtimeSo = unpacked.resolve("assets")
                .resolve(contract.getNativeLibAssetDirName())
                .resolve("arm64").resolve(contract.getSoName());
        Assert.assertTrue("runtime loader expects VMRuntime's arm64 directory",
                Files.isRegularFile(runtimeSo));
        Assert.assertFalse("canonical archive ABI must not leak into runtime asset path",
                Files.exists(unpacked.resolve("assets")
                        .resolve(contract.getNativeLibAssetDirName())
                        .resolve("arm64-v8a")));
    }

    @Test
    public void candidateArchiveIsRejectedBeforeManifestWrites() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("profiles-candidate").toPath();
        Path activeRoot = temporaryFolder.newFolder("active-candidate").toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot).create(APPLICATION_ID);
        ProfileRevisionManifest revision = new ProfileBuildService(profilesRoot, activeRoot)
                .build(APPLICATION_ID, null, "recipe-a", null);
        ProfileBundle bundle = ProfileTestArtifactFactory.assemble(profilesRoot, APPLICATION_ID,
                revision, temporaryFolder.newFolder("candidate-artifacts").toPath());
        Path candidate = temporaryFolder.newFolder("candidate-archive").toPath()
                .resolve("profile.candidate.dptprofile");
        new ProfileArchiveWriter().packCandidate(bundle.getRoot(), candidate);
        Path unpacked = temporaryFolder.newFolder("candidate-unpacked").toPath();
        writeManifest(unpacked, APPLICATION_ID);
        RecordingApk apk = new RecordingApk(new Apk.Builder().profileArchive(candidate));

        try {
            apk.resolveProfileInputs(unpacked);
            Assert.fail("candidate archive must be rejected");
        } catch (Exception expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("candidate"));
        }
        Assert.assertFalse(apk.manifestWriteAttempted);
    }

    @Test
    public void dptProfileOptionIsForwardedToApkBuilder() throws Exception {
        Path profile = temporaryFolder.newFile("forwarded.dptprofile").toPath();
        Method parse = Dpt.class.getDeclaredMethod("parseOptions", String[].class);
        parse.setAccessible(true);
        Object value = parse.invoke(null, (Object) new String[]{
                "--package-file", "input.apk", "--profile", profile.toString()});
        Field archive = Apk.class.getDeclaredField("profileArchive");
        archive.setAccessible(true);
        Assert.assertEquals(profile.toAbsolutePath().normalize(), archive.get(value));
    }

    @Test
    public void protectCandidateArchiveFailsBeforeWritesAndCleansWorkspace() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("profiles-protect-candidate").toPath();
        Path activeRoot = temporaryFolder.newFolder("active-protect-candidate").toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot).create(APPLICATION_ID);
        ProfileRevisionManifest revision = new ProfileBuildService(profilesRoot, activeRoot)
                .build(APPLICATION_ID, null, "recipe-a", null);
        ProfileBundle bundle = ProfileTestArtifactFactory.assemble(profilesRoot, APPLICATION_ID,
                revision, temporaryFolder.newFolder("protect-candidate-artifacts").toPath());
        Path candidate = temporaryFolder.newFolder("protect-candidate-archive").toPath()
                .resolve("profile.candidate.dptprofile");
        new ProfileArchiveWriter().packCandidate(bundle.getRoot(), candidate);
        Path input = temporaryFolder.newFile("candidate-input.apk").toPath();
        writeApk(input, APPLICATION_ID);
        Apk.Builder candidateBuilder = new Apk.Builder();
        candidateBuilder.filePath(input.toString()).profileArchive(candidate);
        RecordingApk apk = new RecordingApk(candidateBuilder);
        Path runRoot = apk.runRoot();

        try {
            apk.protect();
            Assert.fail("candidate archive must fail before protection");
        } catch (ProfileCommandException expected) {
            Assert.assertEquals(com.luoye.dpt.profile.error.ProfileErrorCode.ARCHIVE_CANDIDATE_REQUIRED,
                    expected.getErrorCode());
        }
        Assert.assertFalse(apk.manifestWriteAttempted);
        Assert.assertFalse("failed explicit Profile run must clean workspace",
                Files.exists(runRoot));
    }

    private Path verifiedArchive() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("profiles-verified").toPath();
        Path activeRoot = temporaryFolder.newFolder("active-verified").toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot).create(APPLICATION_ID);
        ProfileRevisionManifest revision = new ProfileBuildService(profilesRoot, activeRoot)
                .build(APPLICATION_ID, null, "recipe-a", null);
        ProfileBundle bundle = ProfileTestArtifactFactory.assemble(profilesRoot, APPLICATION_ID,
                revision, temporaryFolder.newFolder("verified-artifacts").toPath());
        Path candidate = temporaryFolder.newFolder("verified-candidate").toPath()
                .resolve("profile.candidate.dptprofile");
        new ProfileArchiveWriter().packCandidate(bundle.getRoot(), candidate);
        Path reports = temporaryFolder.newFolder("verified-reports").toPath();
        ProfileGateReport gate = new ProfileGateReport();
        gate.setProfileId(created.getProfileId());
        gate.setRevisionId(revision.getRevisionId());
        gate.setCandidateApkSha256(APK_SHA256);
        gate.setOverallStatus(ProfileGateReport.Status.PASSED);
        gate.setGeneratedAtEpochMillis(System.currentTimeMillis());
        LinkedHashMap<String, ProfileGateReport.Result> results = new LinkedHashMap<>();
        for (String id : ProfileUnifiedGate.REQUIRED_GATE_IDS) {
            ProfileGateReport.Result result = new ProfileGateReport.Result();
            result.setStatus(ProfileGateReport.Status.PASSED);
            results.put(id, result);
        }
        gate.setResults(results);
        Files.writeString(reports.resolve("gate-report.json"),
                com.alibaba.fastjson2.JSON.toJSONString(gate), StandardCharsets.UTF_8);
        ProfileScanReport scan = scanReport();
        ProfileScanReportCodec.write(reports.resolve("scan-baseline.json"), scan);
        ProfileScanReportCodec.write(reports.resolve("scan-candidate.json"), scan);
        Path verified = temporaryFolder.newFolder("verified-output").toPath().resolve("profile.dptprofile");
        new ProfileArchivePromotionService().promote(candidate, reports, verified);
        return verified;
    }

    private static ProfileScanReport scanReport() {
        ProfileScanReport report = new ProfileScanReport();
        report.setApplicationId(APPLICATION_ID);
        report.setApkSha256(APK_SHA256);
        long now = System.currentTimeMillis();
        report.setGeneratedAtEpochMillis(now);
        report.setWindowId("window-archive-input");
        report.setWindowStartEpochMillis(now - 1000);
        report.setWindowEndEpochMillis(now + 1000);
        LinkedHashMap<String, String> scanners = new LinkedHashMap<>();
        scanners.put("scanner-a", "1");
        report.setScannerSet(scanners);
        report.setDetections(new java.util.ArrayList<>());
        return report;
    }

    private static void writeManifest(Path root, String applicationId) throws Exception {
        AxmlWriter writer = new AxmlWriter();
        writer.ns("android", "http://schemas.android.com/apk/res/android", 1);
        NodeVisitor manifest = writer.child(null, "manifest");
        manifest.attr(null, "package", -1, NodeVisitor.TYPE_STRING, applicationId);
        NodeVisitor application = manifest.child(null, "application");
        application.end();
        manifest.end();
        writer.end();
        Files.write(root.resolve("AndroidManifest.xml"), writer.toByteArray());
    }

    private static void writeApk(Path apk, String applicationId) throws Exception {
        Path root = Files.createTempDirectory("apk-manifest-");
        try {
            writeManifest(root, applicationId);
            try (java.util.zip.ZipOutputStream output = new java.util.zip.ZipOutputStream(
                    Files.newOutputStream(apk))) {
                output.putNextEntry(new java.util.zip.ZipEntry("AndroidManifest.xml"));
                output.write(Files.readAllBytes(root.resolve("AndroidManifest.xml")));
                output.closeEntry();
            }
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                    }
                });
            }
        }
    }

    private static class InspectableApk extends Apk {
        private InspectableApk(Builder builder) {
            super(builder);
        }

        private Path proxyDexPath() {
            return Path.of(getProxyDexPath());
        }

        private Path shellNativeLibsRoot() {
            return getShellNativeLibsRoot().toPath();
        }

        Path runRoot() {
            return Path.of(getWorkspaceDir().toURI()).getParent();
        }

        private ProfileContract profileContract() {
            return getProfileContract();
        }
    }

    private static final class RecordingApk extends InspectableApk {
        private boolean manifestWriteAttempted;

        private RecordingApk(Builder builder) {
            super(builder);
        }

        @Override
        public void writeProxyAppName(String manifestDir) {
            manifestWriteAttempted = true;
        }
    }
}
