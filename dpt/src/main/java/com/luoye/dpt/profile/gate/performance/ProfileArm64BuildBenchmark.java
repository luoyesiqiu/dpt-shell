package com.luoye.dpt.profile.gate.performance;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ProfileArm64BuildBenchmark {
    public enum Kind {
        LEGACY_SHELL,
        PROFILE_REVISION
    }

    public static final String ABI = "arm64-v8a";

    private final Kind kind;
    private final String ciEnvironmentId;
    private final Map<String, String> toolchain;
    private final String profileId;
    private final String revisionId;
    private final boolean restoredFromCache;
    private final long durationMillis;

    private ProfileArm64BuildBenchmark(Kind kind, String ciEnvironmentId,
                                       Map<String, String> toolchain,
                                       String profileId, String revisionId,
                                       boolean restoredFromCache, long durationMillis) {
        if (kind == null) {
            throw new IllegalArgumentException("arm64 构建基准类型不能为空");
        }
        requireText("CI 环境 ID", ciEnvironmentId);
        if (toolchain == null || toolchain.isEmpty()) {
            throw new IllegalArgumentException("arm64 构建工具链不能为空");
        }
        LinkedHashMap<String, String> toolchainCopy = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : toolchain.entrySet()) {
            requireText("工具链名称", entry.getKey());
            requireText("工具链版本", entry.getValue());
            toolchainCopy.put(entry.getKey(), entry.getValue());
        }
        if (kind == Kind.PROFILE_REVISION) {
            requireText("Profile ID", profileId);
            requireText("Revision ID", revisionId);
        } else if (profileId != null || revisionId != null || restoredFromCache) {
            throw new IllegalArgumentException("旧壳构建基准不得声明 Profile 或缓存命中");
        }
        if (durationMillis <= 0L) {
            throw new IllegalArgumentException("arm64 构建耗时必须大于 0");
        }
        this.kind = kind;
        this.ciEnvironmentId = ciEnvironmentId.trim();
        this.toolchain = Collections.unmodifiableMap(toolchainCopy);
        this.profileId = profileId;
        this.revisionId = revisionId;
        this.restoredFromCache = restoredFromCache;
        this.durationMillis = durationMillis;
    }

    public static ProfileArm64BuildBenchmark legacy(String ciEnvironmentId,
                                                     Map<String, String> toolchain,
                                                     long durationMillis) {
        return new ProfileArm64BuildBenchmark(Kind.LEGACY_SHELL, ciEnvironmentId,
                toolchain, null, null, false, durationMillis);
    }

    public static ProfileArm64BuildBenchmark profile(String ciEnvironmentId,
                                                      Map<String, String> toolchain,
                                                      String profileId, String revisionId,
                                                      boolean restoredFromCache,
                                                      long durationMillis) {
        return new ProfileArm64BuildBenchmark(Kind.PROFILE_REVISION, ciEnvironmentId,
                toolchain, profileId, revisionId, restoredFromCache, durationMillis);
    }

    public Kind getKind() {
        return kind;
    }

    public String getCiEnvironmentId() {
        return ciEnvironmentId;
    }

    public Map<String, String> getToolchain() {
        return toolchain;
    }

    public String getProfileId() {
        return profileId;
    }

    public String getRevisionId() {
        return revisionId;
    }

    public boolean isRestoredFromCache() {
        return restoredFromCache;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    private static void requireText(String label, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
    }
}
