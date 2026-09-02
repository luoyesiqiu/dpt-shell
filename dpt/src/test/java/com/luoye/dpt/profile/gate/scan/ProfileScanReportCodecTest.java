package com.luoye.dpt.profile.gate.scan;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class ProfileScanReportCodecTest {
    @Test
    public void roundTripsManualReportAndKeepsDetectionMetadata() throws Exception {
        ProfileScanReport report = report("a".repeat(64), "com.example.app");
        report.getDetections().add(detection("engine-a", "Trojan.Foo", "Trojan"));

        byte[] encoded = ProfileScanReportCodec.serialize(report);
        ProfileScanReport decoded = ProfileScanReportCodec.deserialize(encoded);

        Assert.assertEquals(report.getApkSha256(), decoded.getApkSha256());
        Assert.assertEquals(report.getScannerSet(), decoded.getScannerSet());
        Assert.assertEquals(1, decoded.getDetections().size());
        Assert.assertEquals("Trojan", decoded.getDetections().get(0).getCategory());
    }

    @Test
    public void rejectsMissingHashOrNonComparableWindow() {
        ProfileScanReport report = report("b".repeat(64), "com.example.app");
        report.setApkSha256(null);
        try {
            ProfileScanReportCodec.validate(report);
            Assert.fail("Expected missing APK hash rejection");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("SHA-256"));
        }

        ProfileScanReport baseline = report("a".repeat(64), "com.example.app");
        ProfileScanReport candidate = report("b".repeat(64), "com.example.app");
        candidate.setWindowEndEpochMillis(candidate.getWindowEndEpochMillis() + 1L);
        ProfileScanGateResult result = ProfileScanGate.evaluate(baseline, candidate);
        Assert.assertFalse(result.isComparable());
        Assert.assertFalse(result.isPassed());
        Assert.assertTrue(result.getFailureReasons().toString().contains("时间窗口"));
    }

    @Test
    public void blocksNewHighRiskFamilyAndMoreDetections() {
        ProfileScanReport baseline = report("a".repeat(64), "com.example.app");
        baseline.getDetections().add(detection("engine-a", "PUA.Tool", "PUA"));
        ProfileScanReport candidate = report("b".repeat(64), "com.example.app");
        candidate.getDetections().add(detection("engine-a", "PUA.Tool", "PUA"));
        candidate.getDetections().add(detection("engine-b", "Banker.Foo", "Banker"));

        ProfileScanGateResult result = ProfileScanGate.evaluate(
                "b".repeat(64), baseline, candidate);

        Assert.assertFalse(result.getFailureReasons().toString(), result.isPassed());
        Assert.assertTrue(result.getNewHighRiskFamilies().contains("banker"));
        Assert.assertTrue(result.getFailureReasons().toString().contains("检测数"));
        Assert.assertTrue(result.getFailureReasons().toString().contains("banker"));
    }

    @Test
    public void rejectsReportsBoundToAnotherApplication() {
        ProfileScanReport baseline = report("a".repeat(64), "com.example.other");
        ProfileScanReport candidate = report("b".repeat(64), "com.example.other");

        ProfileScanGateResult result = ProfileScanGate.evaluate("com.example.app",
                "b".repeat(64), baseline, candidate);

        Assert.assertFalse(result.isComparable());
        Assert.assertFalse(result.isPassed());
        Assert.assertTrue(result.getFailureReasons().toString().contains("目标"));
    }

    @Test
    public void missingManualReportFileFailsWithoutUploadingAnything() throws Exception {
        Path directory = Files.createTempDirectory("profile-scan-report-test");
        try {
            try {
                ProfileScanReportCodec.read(directory.resolve("missing.json"));
                Assert.fail("Expected missing report rejection");
            } catch (java.io.IOException expected) {
                Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("扫描报告"));
            }
        } finally {
            Files.deleteIfExists(directory);
        }
    }

    @Test
    public void rejectsReportGeneratedOutsideDeclaredWindow() {
        ProfileScanReport report = report("a".repeat(64), "com.example.app");
        report.setGeneratedAtEpochMillis(report.getWindowEndEpochMillis() + 1L);

        try {
            ProfileScanReportCodec.validate(report);
            Assert.fail("Expected scan report window rejection");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("生成时间"));
        }
    }

    private static ProfileScanReport report(String sha256, String applicationId) {
        ProfileScanReport report = new ProfileScanReport();
        report.setApplicationId(applicationId);
        report.setApkSha256(sha256);
        report.setGeneratedAtEpochMillis(1_700_000_000_000L);
        report.setWindowId("window-1");
        report.setWindowStartEpochMillis(1_700_000_000_000L);
        report.setWindowEndEpochMillis(1_700_000_360_000L);
        Map<String, String> scanners = new LinkedHashMap<>();
        scanners.put("engine-a", "1.0");
        scanners.put("engine-b", "2.0");
        report.setScannerSet(scanners);
        return report;
    }

    private static ProfileScanReport.Detection detection(String scanner, String family,
                                                          String category) {
        ProfileScanReport.Detection detection = new ProfileScanReport.Detection();
        detection.setScannerId(scanner);
        detection.setSignature(family);
        detection.setFamily(family);
        detection.setCategory(category);
        detection.setSeverity("high");
        return detection;
    }
}
