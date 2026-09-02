package com.luoye.dpt.profile.gate.performance;

import com.luoye.dpt.profile.gate.baseline.ProfileBaselineReport;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ProfileArm64BuildPerformanceGateTest {
    @Test
    public void measuresFirstBuildAndAcceptsTwentyFivePercentBoundary() throws Exception {
        AtomicInteger invocations = new AtomicInteger();
        AtomicLong ticks = new AtomicLong();
        long measured = ProfileArm64BuildBenchmarkRunner.measureOnce(
                invocations::incrementAndGet,
                () -> ticks.getAndAdd(12_500_000_000L));
        ProfileArm64BuildBenchmark baseline = ProfileArm64BuildBenchmark.legacy(
                "mac-ci-01", toolchain(), 10_000L);
        ProfileArm64BuildBenchmark candidate = ProfileArm64BuildBenchmark.profile(
                "mac-ci-01", toolchain(), "prf_abcdefghijklmnopqrstuvwxyz",
                "r000001", false, measured);

        ProfileArm64BuildPerformanceGateResult result =
                ProfileArm64BuildPerformanceGate.evaluate(report(),
                        "prf_abcdefghijklmnopqrstuvwxyz", "r000001", baseline, candidate);

        Assert.assertEquals(1, invocations.get());
        Assert.assertTrue(result.getFailureReasons().toString(), result.isPassed());
        Assert.assertEquals(10_000L, result.getBaselineMillis());
        Assert.assertEquals(12_500L, result.getLimitMillis());
        Assert.assertEquals(12_500L, result.getCandidateMillis());
    }

    @Test
    public void rejectsBuildAboveLimitOrRestoredFromCache() {
        ProfileArm64BuildBenchmark baseline = ProfileArm64BuildBenchmark.legacy(
                "mac-ci-01", toolchain(), 10_000L);
        ProfileArm64BuildBenchmark candidate = ProfileArm64BuildBenchmark.profile(
                "mac-ci-01", toolchain(), "prf_abcdefghijklmnopqrstuvwxyz",
                "r000001", true, 12_501L);

        ProfileArm64BuildPerformanceGateResult result =
                ProfileArm64BuildPerformanceGate.evaluate(report(),
                        "prf_abcdefghijklmnopqrstuvwxyz", "r000001", baseline, candidate);

        Assert.assertFalse(result.isPassed());
        Assert.assertTrue(result.getFailureReasons().toString().contains("+25%"));
        Assert.assertTrue(result.getFailureReasons().toString().contains("首次构建"));
    }

    private static ProfileBaselineReport report() {
        ProfileBaselineReport report = new ProfileBaselineReport();
        report.setGeneratedAtEpochMillis(1L);
        report.setApplicationId("com.example.build");
        report.setAbi("arm64-v8a");
        report.setSigningMethod("same-signing");
        report.setSkipSigning(true);
        report.setToolchain(toolchain());
        report.setDeviceEnvironment(environment("device", "abi", "arm64-v8a"));
        report.setCiEnvironment(environment("mac-ci-01", "runner", "fixed"));
        report.setInputApk(artifact("input-apk", "input.apk", repeat('a'), 100L));
        report.setLegacyProtectedApk(artifact("legacy-protected-apk", "legacy.apk",
                repeat('b'), 120L));
        report.setLegacyArm64So(artifact("legacy-arm64-so", "legacy.so", repeat('d'), 10L));
        return report;
    }

    private static Map<String, String> toolchain() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("gradle", "8.14");
        result.put("cmake", "3.31.1");
        result.put("ndk", "27.0.12077973");
        return result;
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

    private static String repeat(char value) {
        return String.join("", Collections.nCopies(64, String.valueOf(value)));
    }
}
