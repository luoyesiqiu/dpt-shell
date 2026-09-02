package com.luoye.dpt.profile.gate.size;

import com.luoye.dpt.profile.gate.baseline.ProfileBaselineReport;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class ProfileApkSizeGateTest {
    private static final String INPUT_SHA256 = repeat('a');
    private static final String LEGACY_SHA256 = repeat('b');
    private static final String CANDIDATE_SHA256 = repeat('c');

    @Test
    public void acceptsCandidateAtBothApkSizeBoundaries() {
        ProfileBaselineReport baseline = baseline(300L * 1024L * 1024L,
                320L * 1024L * 1024L);
        long absoluteLimit = baseline.getLegacyProtectedApk().getBytes()
                + 2L * 1024L * 1024L;

        ProfileApkSizeGateResult result = ProfileApkSizeGate.evaluate(
                baseline, CANDIDATE_SHA256, absoluteLimit);

        Assert.assertTrue(result.isPassed());
        Assert.assertEquals(absoluteLimit, result.getAbsoluteLimitBytes());
        Assert.assertEquals(323L * 1024L * 1024L,
                result.getRelativeIncreaseLimitBytes());
        Assert.assertEquals(absoluteLimit, result.getCandidateBytes());
        Assert.assertEquals(INPUT_SHA256, result.getInputApkSha256());
        Assert.assertEquals(LEGACY_SHA256, result.getLegacyProtectedApkSha256());
        Assert.assertEquals(CANDIDATE_SHA256, result.getCandidateApkSha256());
    }

    @Test
    public void rejectsCandidateAboveAbsoluteHeadroom() {
        ProfileBaselineReport baseline = baseline(300L * 1024L * 1024L,
                320L * 1024L * 1024L);
        long candidateBytes = baseline.getLegacyProtectedApk().getBytes()
                + 2L * 1024L * 1024L + 1L;

        ProfileApkSizeGateResult result = ProfileApkSizeGate.evaluate(
                baseline, CANDIDATE_SHA256, candidateBytes);

        Assert.assertFalse(result.isPassed());
        Assert.assertFalse(result.isAbsoluteLimitPassed());
        Assert.assertTrue(result.isRelativeIncreaseLimitPassed());
        Assert.assertTrue(result.getFailureReasons().get(0).contains("2 MiB"));
    }

    @Test
    public void rejectsCandidateAboveOnePercentagePointIncrease() {
        ProfileBaselineReport baseline = baseline(10_000L, 11_000L);

        ProfileApkSizeGateResult boundary = ProfileApkSizeGate.evaluate(
                baseline, CANDIDATE_SHA256, 11_100L);
        ProfileApkSizeGateResult exceeded = ProfileApkSizeGate.evaluate(
                baseline, CANDIDATE_SHA256, 11_101L);

        Assert.assertTrue(boundary.isPassed());
        Assert.assertFalse(exceeded.isPassed());
        Assert.assertTrue(exceeded.isAbsoluteLimitPassed());
        Assert.assertFalse(exceeded.isRelativeIncreaseLimitPassed());
        Assert.assertTrue(exceeded.getFailureReasons().get(0).contains("1 个百分点"));
    }

    private static ProfileBaselineReport baseline(long inputBytes, long legacyBytes) {
        ProfileBaselineReport report = new ProfileBaselineReport();
        report.setGeneratedAtEpochMillis(1L);
        report.setApplicationId("com.example.size");
        report.setAbi("arm64-v8a");
        report.setSigningMethod("same-signing");
        report.setSkipSigning(true);
        report.setToolchain(Collections.singletonMap("dpt", "1"));
        report.setDeviceEnvironment(environment("device", "abi", "arm64-v8a"));
        report.setCiEnvironment(environment("ci", "runner", "fixed"));
        report.setInputApk(artifact("input-apk", "input.apk", INPUT_SHA256, inputBytes));
        report.setLegacyProtectedApk(artifact("legacy-protected-apk", "legacy.apk",
                LEGACY_SHA256, legacyBytes));
        report.setLegacyArm64So(artifact("legacy-arm64-so", "legacy.so", repeat('d'), 1L));
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

    private static String repeat(char value) {
        return String.join("", Collections.nCopies(64, String.valueOf(value)));
    }
}
