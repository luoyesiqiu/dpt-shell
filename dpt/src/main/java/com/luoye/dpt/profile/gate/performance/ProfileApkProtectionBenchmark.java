package com.luoye.dpt.profile.gate.performance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ProfileApkProtectionBenchmark {
    public enum Kind {
        LEGACY_DPT,
        ACTIVE_PROFILE
    }

    private final Kind kind;
    private final String inputApkSha256;
    private final String protectedApkSha256;
    private final String executionEnvironmentId;
    private final String profileId;
    private final String revisionId;
    private final List<Long> samplesMillis;

    private ProfileApkProtectionBenchmark(Kind kind, String inputApkSha256,
                                          String protectedApkSha256,
                                          String executionEnvironmentId,
                                          String profileId, String revisionId,
                                          List<Long> samplesMillis) {
        requireSha256("输入 APK", inputApkSha256);
        requireSha256("加固 APK", protectedApkSha256);
        requireText("执行环境 ID", executionEnvironmentId);
        if (kind == null || samplesMillis == null) {
            throw new IllegalArgumentException("APK 加固基准类型和样本不能为空");
        }
        if (kind == Kind.ACTIVE_PROFILE) {
            requireText("Profile ID", profileId);
            requireText("Revision ID", revisionId);
        } else if (profileId != null || revisionId != null) {
            throw new IllegalArgumentException("旧 DPT 基准不得声明 Profile Revision");
        }
        List<Long> copy = new ArrayList<>(samplesMillis.size());
        for (Long sample : samplesMillis) {
            if (sample == null || sample <= 0L) {
                throw new IllegalArgumentException("APK 加固耗时样本必须大于 0");
            }
            copy.add(sample);
        }
        this.kind = kind;
        this.inputApkSha256 = inputApkSha256;
        this.protectedApkSha256 = protectedApkSha256;
        this.executionEnvironmentId = executionEnvironmentId.trim();
        this.profileId = profileId;
        this.revisionId = revisionId;
        this.samplesMillis = Collections.unmodifiableList(copy);
    }

    public static ProfileApkProtectionBenchmark legacy(
            String inputApkSha256, String protectedApkSha256,
            String executionEnvironmentId, List<Long> samplesMillis) {
        return new ProfileApkProtectionBenchmark(Kind.LEGACY_DPT, inputApkSha256,
                protectedApkSha256, executionEnvironmentId, null, null, samplesMillis);
    }

    public static ProfileApkProtectionBenchmark profile(
            String inputApkSha256, String protectedApkSha256,
            String executionEnvironmentId, String profileId, String revisionId,
            List<Long> samplesMillis) {
        return new ProfileApkProtectionBenchmark(Kind.ACTIVE_PROFILE, inputApkSha256,
                protectedApkSha256, executionEnvironmentId, profileId, revisionId,
                samplesMillis);
    }

    public Kind getKind() {
        return kind;
    }

    public String getInputApkSha256() {
        return inputApkSha256;
    }

    public String getProtectedApkSha256() {
        return protectedApkSha256;
    }

    public String getExecutionEnvironmentId() {
        return executionEnvironmentId;
    }

    public String getProfileId() {
        return profileId;
    }

    public String getRevisionId() {
        return revisionId;
    }

    public List<Long> getSamplesMillis() {
        return samplesMillis;
    }

    private static void requireSha256(String label, String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(label + " SHA-256 格式无效");
        }
    }

    private static void requireText(String label, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
    }
}
