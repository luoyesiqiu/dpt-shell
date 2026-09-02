package com.luoye.dpt.profile.gate.performance;

import com.luoye.dpt.profile.gate.baseline.ProfileBaselineReport;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProfileColdStartPerformanceGateTest {
    private static final String LEGACY_APK_SHA256 = repeat('b');
    private static final String CANDIDATE_APK_SHA256 = repeat('c');

    @Test
    public void acceptsThirtyColdStartsAtP50AndP95Boundaries() {
        ProfileColdStartBenchmark baseline = benchmark(LEGACY_APK_SHA256,
                repeated(30, 1_000L), 0, 0);
        List<Long> candidateSamples = new ArrayList<>();
        candidateSamples.addAll(repeated(28, 1_050L));
        candidateSamples.addAll(repeated(2, 1_100L));
        ProfileColdStartBenchmark candidate = benchmark(CANDIDATE_APK_SHA256,
                candidateSamples, 0, 0);

        ProfileColdStartPerformanceGateResult result =
                ProfileColdStartPerformanceGate.evaluate(report(), CANDIDATE_APK_SHA256,
                        baseline, candidate);

        Assert.assertTrue(result.getFailureReasons().toString(), result.isPassed());
        Assert.assertEquals(1_000L, result.getBaselineP50Millis());
        Assert.assertEquals(1_000L, result.getBaselineP95Millis());
        Assert.assertEquals(1_050L, result.getCandidateP50Millis());
        Assert.assertEquals(1_100L, result.getCandidateP95Millis());
        Assert.assertEquals(1_050L, result.getP50LimitMillis());
        Assert.assertEquals(1_100L, result.getP95LimitMillis());
        Assert.assertEquals(30, result.getCandidateSamplesMillis().size());
    }

    @Test
    public void rejectsThresholdOverrunCrashAndAnr() {
        ProfileColdStartBenchmark baseline = benchmark(LEGACY_APK_SHA256,
                repeated(30, 1_000L), 0, 0);
        ProfileColdStartBenchmark candidate = benchmark(CANDIDATE_APK_SHA256,
                repeated(30, 1_101L), 1, 1);

        ProfileColdStartPerformanceGateResult result =
                ProfileColdStartPerformanceGate.evaluate(report(), CANDIDATE_APK_SHA256,
                        baseline, candidate);

        Assert.assertFalse(result.isPassed());
        Assert.assertFalse(result.isP50Passed());
        Assert.assertFalse(result.isP95Passed());
        Assert.assertTrue(result.getFailureReasons().toString().contains("崩溃"));
        Assert.assertTrue(result.getFailureReasons().toString().contains("ANR"));
    }

    private static ProfileColdStartBenchmark benchmark(String apkSha256, List<Long> samples,
                                                       int crashes, int anrs) {
        return new ProfileColdStartBenchmark(apkSha256, "pixel-arm64-api35", samples,
                crashes, anrs);
    }

    private static ProfileBaselineReport report() {
        ProfileBaselineReport report = new ProfileBaselineReport();
        report.setGeneratedAtEpochMillis(1L);
        report.setApplicationId("com.example.startup");
        report.setAbi("arm64-v8a");
        report.setSigningMethod("same-signing");
        report.setSkipSigning(true);
        report.setToolchain(Collections.singletonMap("dpt", "1"));
        report.setDeviceEnvironment(environment("pixel-arm64-api35", "abi", "arm64-v8a"));
        report.setCiEnvironment(environment("ci", "runner", "fixed"));
        report.setInputApk(artifact("input-apk", "input.apk", repeat('a'), 100L));
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
