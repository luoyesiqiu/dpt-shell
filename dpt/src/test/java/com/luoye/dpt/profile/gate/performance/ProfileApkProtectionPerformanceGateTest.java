package com.luoye.dpt.profile.gate.performance;

import com.luoye.dpt.profile.gate.baseline.ProfileBaselineReport;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ProfileApkProtectionPerformanceGateTest {
    private static final String INPUT_APK_SHA256 = repeat('a');
    private static final String LEGACY_APK_SHA256 = repeat('b');
    private static final String CANDIDATE_APK_SHA256 = repeat('c');

    @Test
    public void measuresTenRunsAndAcceptsP50AndP95Boundaries() throws Exception {
        AtomicInteger invocations = new AtomicInteger();
        AtomicInteger ticks = new AtomicInteger();
        List<Long> measured = ProfileApkProtectionBenchmarkRunner.measureTen(
                iteration -> invocations.incrementAndGet(),
                () -> ticks.getAndAdd(1_100_000_000));
        ProfileApkProtectionBenchmark baseline = ProfileApkProtectionBenchmark.legacy(
                INPUT_APK_SHA256, LEGACY_APK_SHA256, "mac-ci-01",
                repeated(10, 1_000L));
        List<Long> candidateSamples = new ArrayList<>(Collections.nCopies(9, 1_100L));
        candidateSamples.add(1_150L);
        ProfileApkProtectionBenchmark candidate = ProfileApkProtectionBenchmark.profile(
                INPUT_APK_SHA256, CANDIDATE_APK_SHA256, "mac-ci-01",
                "prf_abcdefghijklmnopqrstuvwxyz", "r000001", candidateSamples);

        ProfileApkProtectionPerformanceGateResult result =
                ProfileApkProtectionPerformanceGate.evaluate(report(),
                        "prf_abcdefghijklmnopqrstuvwxyz", "r000001",
                        CANDIDATE_APK_SHA256, baseline, candidate);

        Assert.assertEquals(10, invocations.get());
        Assert.assertEquals(repeated(10, 1_100L), measured);
        Assert.assertTrue(result.getFailureReasons().toString(), result.isPassed());
        Assert.assertEquals(1_100L, result.getP50LimitMillis());
        Assert.assertEquals(1_150L, result.getP95LimitMillis());
        Assert.assertEquals(1_100L, result.getCandidateP50Millis());
        Assert.assertEquals(1_150L, result.getCandidateP95Millis());
    }

    @Test
    public void rejectsP50OrP95AboveLimits() {
        ProfileApkProtectionBenchmark baseline = ProfileApkProtectionBenchmark.legacy(
                INPUT_APK_SHA256, LEGACY_APK_SHA256, "mac-ci-01",
                repeated(10, 1_000L));
        ProfileApkProtectionBenchmark candidate = ProfileApkProtectionBenchmark.profile(
                INPUT_APK_SHA256, CANDIDATE_APK_SHA256, "mac-ci-01",
                "prf_abcdefghijklmnopqrstuvwxyz", "r000001",
                repeated(10, 1_151L));

        ProfileApkProtectionPerformanceGateResult result =
                ProfileApkProtectionPerformanceGate.evaluate(report(),
                        "prf_abcdefghijklmnopqrstuvwxyz", "r000001",
                        CANDIDATE_APK_SHA256, baseline, candidate);

        Assert.assertFalse(result.isPassed());
        Assert.assertFalse(result.isP50Passed());
        Assert.assertFalse(result.isP95Passed());
        Assert.assertTrue(result.getFailureReasons().toString().contains("+10%"));
        Assert.assertTrue(result.getFailureReasons().toString().contains("+15%"));
    }

    private static ProfileBaselineReport report() {
        ProfileBaselineReport report = new ProfileBaselineReport();
        report.setGeneratedAtEpochMillis(1L);
        report.setApplicationId("com.example.protect");
        report.setAbi("arm64-v8a");
        report.setSigningMethod("same-signing");
        report.setSkipSigning(true);
        report.setToolchain(Collections.singletonMap("dpt", "1"));
        report.setDeviceEnvironment(environment("device", "abi", "arm64-v8a"));
        report.setCiEnvironment(environment("mac-ci-01", "runner", "fixed"));
        report.setInputApk(artifact("input-apk", "input.apk", INPUT_APK_SHA256, 100L));
        report.setLegacyProtectedApk(artifact("legacy-protected-apk", "legacy.apk",
                LEGACY_APK_SHA256, 120L));
        report.setLegacyArm64So(artifact("legacy-arm64-so", "legacy.so", repeat('d'), 10L));
        return report;
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
