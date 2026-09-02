package com.luoye.dpt.profile.gate.baseline;

import com.luoye.dpt.util.MessageDigestUtils;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class ProfileBaselineReportCodecTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void captureBindsArtifactsSigningOptionArm64ToolchainAndEnvironments()
            throws Exception {
        Path inputApk = write("input.apk", new byte[]{1, 2, 3});
        Path legacyApk = write("legacy-protected.apk", new byte[]{4, 5, 6, 7});
        Path legacySo = write("legacy-arm64.so", new byte[]{8, 9});
        Map<String, String> toolchain = map("dpt", "1.0.0", "ndk", "27.0.12077973",
                "cmake", "3.31.1");
        ProfileBaselineReport.Environment device = environment("pixel-arm64-api35",
                map("abi", "arm64-v8a", "api", "35", "fingerprint", "test/device"));
        ProfileBaselineReport.Environment ci = environment("mac-ci-01",
                map("os", "macOS", "arch", "arm64", "runner", "fixed"));

        ProfileBaselineReport report = ProfileBaselineReportCodec.capture(
                "com.example.baseline", inputApk, legacyApk, legacySo,
                "project-signing-v1-v2", true, toolchain, device, ci);
        byte[] serialized = ProfileBaselineReportCodec.serialize(report);
        ProfileBaselineReport parsed = ProfileBaselineReportCodec.deserialize(serialized);

        Assert.assertEquals("arm64-v8a", parsed.getAbi());
        Assert.assertTrue(parsed.isSkipSigning());
        Assert.assertEquals("project-signing-v1-v2", parsed.getSigningMethod());
        Assert.assertEquals(toolchain, parsed.getToolchain());
        Assert.assertEquals(device.getAttributes(), parsed.getDeviceEnvironment().getAttributes());
        Assert.assertEquals(ci.getId(), parsed.getCiEnvironment().getId());
        Assert.assertEquals(Files.size(inputApk), parsed.getInputApk().getBytes());
        Assert.assertEquals(MessageDigestUtils.sha256(Files.readAllBytes(inputApk)),
                parsed.getInputApk().getSha256());
        Assert.assertEquals(MessageDigestUtils.sha256(Files.readAllBytes(legacyApk)),
                parsed.getLegacyProtectedApk().getSha256());
        Assert.assertEquals(MessageDigestUtils.sha256(Files.readAllBytes(legacySo)),
                parsed.getLegacyArm64So().getSha256());
    }

    @Test
    public void incompatibleAbiOrMissingEnvironmentIsRejected() throws Exception {
        Path inputApk = write("invalid-input.apk", new byte[]{1});
        Path legacyApk = write("invalid-legacy.apk", new byte[]{2});
        Path legacySo = write("invalid-legacy.so", new byte[]{3});
        ProfileBaselineReport report = ProfileBaselineReportCodec.capture(
                "com.example.invalid", inputApk, legacyApk, legacySo, "same-signing",
                true, map("dpt", "1"), environment("device", map("abi", "arm64-v8a")),
                environment("ci", map("runner", "fixed")));
        report.setAbi("armeabi-v7a");

        assertInvalid(report, "arm64-v8a");
        report.setAbi("arm64-v8a");
        report.setCiEnvironment(null);
        assertInvalid(report, "CI");
    }

    private static void assertInvalid(ProfileBaselineReport report, String message) {
        try {
            ProfileBaselineReportCodec.serialize(report);
            Assert.fail("Expected invalid baseline report");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(message));
        }
    }

    private Path write(String name, byte[] content) throws Exception {
        Path path = temporaryFolder.newFile(name).toPath();
        Files.write(path, content);
        return path;
    }

    private static ProfileBaselineReport.Environment environment(String id,
                                                                  Map<String, String> attributes) {
        ProfileBaselineReport.Environment environment = new ProfileBaselineReport.Environment();
        environment.setId(id);
        environment.setAttributes(attributes);
        return environment;
    }

    private static Map<String, String> map(String... values) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put(values[index], values[index + 1]);
        }
        return result;
    }
}
