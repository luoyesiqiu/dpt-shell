package com.luoye.dpt.profile.gate.size;

import com.luoye.dpt.profile.gate.baseline.ProfileBaselineReport;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class ProfileArm64SoSizeGateTest {
    private static final String LEGACY_SO_SHA256 = repeat('d');
    private static final String CANDIDATE_SO_SHA256 = repeat('e');

    @Test
    public void acceptsArm64SoAtOneHundredTenPercentBoundary() {
        ProfileArm64SoSizeGateResult result = ProfileArm64SoSizeGate.evaluate(
                baseline(719_152L), CANDIDATE_SO_SHA256, 791_067L);

        Assert.assertTrue(result.isPassed());
        Assert.assertEquals(719_152L, result.getLegacyBytes());
        Assert.assertEquals(791_067L, result.getLimitBytes());
        Assert.assertEquals(791_067L, result.getCandidateBytes());
        Assert.assertEquals(LEGACY_SO_SHA256, result.getLegacyArm64SoSha256());
        Assert.assertEquals(CANDIDATE_SO_SHA256, result.getCandidateArm64SoSha256());
        Assert.assertTrue(result.getMessage().contains("baseline=719152"));
        Assert.assertTrue(result.getMessage().contains("limit=791067"));
        Assert.assertTrue(result.getMessage().contains("actual=791067"));
    }

    @Test
    public void rejectsArm64SoAboveOneHundredTenPercentBoundary() {
        ProfileArm64SoSizeGateResult result = ProfileArm64SoSizeGate.evaluate(
                baseline(719_152L), CANDIDATE_SO_SHA256, 791_068L);

        Assert.assertFalse(result.isPassed());
        Assert.assertTrue(result.getMessage().contains("超过旧壳 SO 的 110%"));
        Assert.assertTrue(result.getMessage().contains("actual=791068"));
    }

    private static ProfileBaselineReport baseline(long legacySoBytes) {
        ProfileBaselineReport report = new ProfileBaselineReport();
        report.setGeneratedAtEpochMillis(1L);
        report.setApplicationId("com.example.so");
        report.setAbi("arm64-v8a");
        report.setSigningMethod("same-signing");
        report.setSkipSigning(true);
        report.setToolchain(Collections.singletonMap("dpt", "1"));
        report.setDeviceEnvironment(environment("device", "abi", "arm64-v8a"));
        report.setCiEnvironment(environment("ci", "runner", "fixed"));
        report.setInputApk(artifact("input-apk", "input.apk", repeat('a'), 100L));
        report.setLegacyProtectedApk(artifact("legacy-protected-apk", "legacy.apk",
                repeat('b'), 120L));
        report.setLegacyArm64So(artifact("legacy-arm64-so", "legacy.so",
                LEGACY_SO_SHA256, legacySoBytes));
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
