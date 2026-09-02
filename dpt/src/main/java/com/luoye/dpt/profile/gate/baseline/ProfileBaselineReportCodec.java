package com.luoye.dpt.profile.gate.baseline;

import com.alibaba.fastjson2.JSON;
import com.luoye.dpt.profile.model.ProfileSchemaVersions;
import com.luoye.dpt.profile.validation.ProfileModelValidator;
import com.luoye.dpt.util.MessageDigestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ProfileBaselineReportCodec {
    public static final String REQUIRED_ABI = "arm64-v8a";

    private ProfileBaselineReportCodec() {
    }

    public static ProfileBaselineReport capture(String applicationId, Path inputApk,
                                                Path legacyProtectedApk,
                                                Path legacyArm64So,
                                                String signingMethod,
                                                boolean skipSigning,
                                                Map<String, String> toolchain,
                                                ProfileBaselineReport.Environment device,
                                                ProfileBaselineReport.Environment ci)
            throws IOException {
        ProfileBaselineReport report = new ProfileBaselineReport();
        report.setGeneratedAtEpochMillis(System.currentTimeMillis());
        report.setApplicationId(applicationId);
        report.setAbi(REQUIRED_ABI);
        report.setSigningMethod(signingMethod);
        report.setSkipSigning(skipSigning);
        report.setToolchain(copyMap(toolchain));
        report.setDeviceEnvironment(copyEnvironment(device));
        report.setCiEnvironment(copyEnvironment(ci));
        report.setInputApk(artifact("input-apk", inputApk));
        report.setLegacyProtectedApk(artifact("legacy-protected-apk", legacyProtectedApk));
        report.setLegacyArm64So(artifact("legacy-arm64-so", legacyArm64So));
        validate(report);
        return report;
    }

    public static byte[] serialize(ProfileBaselineReport report) {
        validate(report);
        return JSON.toJSONBytes(report);
    }

    public static ProfileBaselineReport deserialize(byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("基线报告内容不能为空");
        }
        try {
            ProfileBaselineReport report = JSON.parseObject(content, ProfileBaselineReport.class);
            validate(report);
            return report;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("基线报告无法解析", exception);
        }
    }

    public static void validate(ProfileBaselineReport report) {
        if (report == null) {
            throw new IllegalArgumentException("基线报告不能为空");
        }
        if (report.getSchemaVersion() != ProfileSchemaVersions.BASELINE_REPORT) {
            throw new IllegalArgumentException("基线报告版本不支持："
                    + report.getSchemaVersion());
        }
        if (report.getGeneratedAtEpochMillis() <= 0L) {
            throw new IllegalArgumentException("基线报告生成时间无效");
        }
        ProfileModelValidator.validateApplicationId(report.getApplicationId());
        if (!REQUIRED_ABI.equals(report.getAbi())) {
            throw new IllegalArgumentException("基线 ABI 必须为 arm64-v8a");
        }
        requireText("签名方式", report.getSigningMethod());
        requireMap("工具链", report.getToolchain());
        validateEnvironment("设备", report.getDeviceEnvironment());
        validateEnvironment("CI", report.getCiEnvironment());
        validateArtifact("input-apk", report.getInputApk());
        validateArtifact("legacy-protected-apk", report.getLegacyProtectedApk());
        validateArtifact("legacy-arm64-so", report.getLegacyArm64So());
    }

    private static ProfileBaselineReport.Artifact artifact(String logicalName, Path path)
            throws IOException {
        if (path == null || Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || Files.size(path) <= 0L) {
            throw new IOException("基线制品无效：" + logicalName + "=" + path);
        }
        ProfileBaselineReport.Artifact artifact = new ProfileBaselineReport.Artifact();
        artifact.setLogicalName(logicalName);
        artifact.setFileName(path.getFileName().toString());
        artifact.setBytes(Files.size(path));
        artifact.setSha256(MessageDigestUtils.sha256(Files.readAllBytes(path)));
        return artifact;
    }

    private static void validateArtifact(String expectedName,
                                         ProfileBaselineReport.Artifact artifact) {
        if (artifact == null || !expectedName.equals(artifact.getLogicalName())) {
            throw new IllegalArgumentException("基线制品缺失或类型不匹配：" + expectedName);
        }
        requireText(expectedName + " 文件名", artifact.getFileName());
        if (artifact.getBytes() <= 0L) {
            throw new IllegalArgumentException("基线制品大小无效：" + expectedName);
        }
        requireSha256(expectedName + " SHA-256", artifact.getSha256());
    }

    private static void validateEnvironment(String label,
                                            ProfileBaselineReport.Environment environment) {
        if (environment == null) {
            throw new IllegalArgumentException(label + " 环境不能为空");
        }
        requireText(label + " 环境 ID", environment.getId());
        requireMap(label + " 环境属性", environment.getAttributes());
    }

    private static ProfileBaselineReport.Environment copyEnvironment(
            ProfileBaselineReport.Environment source) {
        if (source == null) {
            return null;
        }
        ProfileBaselineReport.Environment copy = new ProfileBaselineReport.Environment();
        copy.setId(source.getId());
        copy.setAttributes(copyMap(source.getAttributes()));
        return copy;
    }

    private static Map<String, String> copyMap(Map<String, String> values) {
        return values == null ? null : new LinkedHashMap<>(values);
    }

    private static void requireMap(String label, Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            requireText(label + "键", entry.getKey());
            requireText(label + "值", entry.getValue());
        }
    }

    private static void requireText(String label, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
    }

    private static void requireSha256(String label, String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(label + "格式无效");
        }
    }
}
