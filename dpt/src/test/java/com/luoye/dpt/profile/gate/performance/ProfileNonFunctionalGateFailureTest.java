package com.luoye.dpt.profile.gate.performance;

import com.luoye.dpt.profile.gate.baseline.ProfileBaselineReport;
import com.luoye.dpt.profile.gate.size.ProfileApkSizeGate;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProfileNonFunctionalGateFailureTest {
    private static final String INPUT_SHA256 = repeat('a');
    private static final String LEGACY_SHA256 = repeat('b');
    private static final String CANDIDATE_SHA256 = repeat('c');

    @Test
    public void missingBaselineReportFailsClosed() {
        try {
            ProfileApkSizeGate.evaluate(null, CANDIDATE_SHA256, 100L);
            Assert.fail("Expected missing baseline failure");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("基线报告"));
        }
    }

    @Test
    public void coldStartRejectsEnvironmentSampleAndBothApkShaMismatches() {
        ProfileColdStartBenchmark baseline = new ProfileColdStartBenchmark(
                repeat('e'), "wrong-device", repeated(29, 1_000L), 0, 0);
        ProfileColdStartBenchmark candidate = new ProfileColdStartBenchmark(
                repeat('f'), "wrong-device", repeated(29, 1_000L), 0, 0);

        ProfileColdStartPerformanceGateResult result =
                ProfileColdStartPerformanceGate.evaluate(report(), CANDIDATE_SHA256,
                        baseline, candidate);

        Assert.assertFalse(result.isPassed());
        String failures = result.getFailureReasons().toString();
        Assert.assertTrue(failures, failures.contains("旧 DPT APK SHA-256"));
        Assert.assertTrue(failures, failures.contains("候选 APK SHA-256"));
        Assert.assertTrue(failures, failures.contains("设备环境"));
        Assert.assertTrue(failures, failures.contains("样本不足"));
    }

    @Test
    public void protectionRejectsEnvironmentSampleAndShaMismatches() {
        ProfileApkProtectionBenchmark baseline = ProfileApkProtectionBenchmark.legacy(
                repeat('e'), repeat('f'), "wrong-ci", repeated(9, 1_000L));
        ProfileApkProtectionBenchmark candidate = ProfileApkProtectionBenchmark.profile(
                repeat('e'), repeat('f'), "wrong-ci",
                "prf_abcdefghijklmnopqrstuvwxyz", "r000001", repeated(9, 1_000L));

        ProfileApkProtectionPerformanceGateResult result =
                ProfileApkProtectionPerformanceGate.evaluate(report(),
                        "prf_abcdefghijklmnopqrstuvwxyz", "r000001", CANDIDATE_SHA256,
                        baseline, candidate);

        Assert.assertFalse(result.isPassed());
        String failures = result.getFailureReasons().toString();
        Assert.assertTrue(failures, failures.contains("SHA-256"));
        Assert.assertTrue(failures, failures.contains("加固环境"));
        Assert.assertTrue(failures, failures.contains("恰好为 10 次"));
    }

    @Test
    public void buildRejectsEnvironmentToolchainCacheAndThresholdMismatches() {
        ProfileArm64BuildBenchmark baseline = ProfileArm64BuildBenchmark.legacy(
                "wrong-ci", Collections.singletonMap("cmake", "old"), 1_000L);
        ProfileArm64BuildBenchmark candidate = ProfileArm64BuildBenchmark.profile(
                "wrong-ci", Collections.singletonMap("cmake", "old"),
                "prf_abcdefghijklmnopqrstuvwxyz", "r000001", true, 1_251L);

        ProfileArm64BuildPerformanceGateResult result =
                ProfileArm64BuildPerformanceGate.evaluate(report(),
                        "prf_abcdefghijklmnopqrstuvwxyz", "r000001", baseline, candidate);

        Assert.assertFalse(result.isPassed());
        String failures = result.getFailureReasons().toString();
        Assert.assertTrue(failures, failures.contains("CI 环境"));
        Assert.assertTrue(failures, failures.contains("工具链"));
        Assert.assertTrue(failures, failures.contains("首次构建"));
        Assert.assertTrue(failures, failures.contains("+25%"));
    }

    private static ProfileBaselineReport report() {
        ProfileBaselineReport report = new ProfileBaselineReport();
        report.setGeneratedAtEpochMillis(1L);
        report.setApplicationId("com.example.failure");
        report.setAbi("arm64-v8a");
        report.setSigningMethod("same-signing");
        report.setSkipSigning(true);
        report.setToolchain(toolchain());
        report.setDeviceEnvironment(environment("fixed-device", "abi", "arm64-v8a"));
        report.setCiEnvironment(environment("fixed-ci", "runner", "fixed"));
        report.setInputApk(artifact("input-apk", "input.apk", INPUT_SHA256, 100L));
        report.setLegacyProtectedApk(artifact("legacy-protected-apk", "legacy.apk",
                LEGACY_SHA256, 120L));
        report.setLegacyArm64So(artifact("legacy-arm64-so", "legacy.so", repeat('d'), 10L));
        return report;
    }

    private static Map<String, String> toolchain() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("cmake", "3.31.1");
        values.put("ndk", "27.0.12077973");
        return values;
    }

    private static ProfileBaselineReport.Environment environment(String id, String key,
                                                                  String value) {
        ProfileBaselineReport.Environment environment = new ProfileBaselineReport.Environment();
        environment.setId(id);
        environment.setAttributes(Collections.singletonMap(key, value));
        return environment;
    }

    private static ProfileBaselineReport.Artifact artifact(String logicalName, String fileName,
                                                            String sha256, long bytes) {
        ProfileBaselineReport.Artifact artifact = new ProfileBaselineReport.Artifact();
        artifact.setLogicalName(logicalName);
        artifact.setFileName(fileName);
        artifact.setSha256(sha256);
        artifact.setBytes(bytes);
        return artifact;
    }

    private static List<Long> repeated(int count, long value) {
        return new ArrayList<>(Collections.nCopies(count, value));
    }

    private static String repeat(char value) {
        return String.join("", Collections.nCopies(64, String.valueOf(value)));
    }
}
