package com.luoye.dpt.profile.gate.performance;

import com.luoye.dpt.profile.gate.baseline.ProfileBaselineReport;
import com.luoye.dpt.profile.gate.baseline.ProfileBaselineReportCodec;

import java.util.ArrayList;
import java.util.List;

public final class ProfileArm64BuildPerformanceGate {
    private ProfileArm64BuildPerformanceGate() {
    }

    public static ProfileArm64BuildPerformanceGateResult evaluate(
            ProfileBaselineReport report, String expectedProfileId,
            String expectedRevisionId, ProfileArm64BuildBenchmark baseline,
            ProfileArm64BuildBenchmark candidate) {
        ProfileBaselineReportCodec.validate(report);
        requireText("期望 Profile ID", expectedProfileId);
        requireText("期望 Revision ID", expectedRevisionId);
        if (baseline == null || candidate == null) {
            throw new IllegalArgumentException("arm64 构建基线和候选证据不能为空");
        }
        List<String> failures = new ArrayList<>();
        if (baseline.getKind() != ProfileArm64BuildBenchmark.Kind.LEGACY_SHELL) {
            failures.add("arm64 构建基线必须来自旧壳构建");
        }
        if (candidate.getKind() != ProfileArm64BuildBenchmark.Kind.PROFILE_REVISION) {
            failures.add("arm64 候选构建必须来自 Profile Revision");
        }
        String expectedEnvironment = report.getCiEnvironment().getId();
        requireEqual(failures, "旧壳构建 CI 环境", expectedEnvironment,
                baseline.getCiEnvironmentId());
        requireEqual(failures, "Profile 构建 CI 环境", expectedEnvironment,
                candidate.getCiEnvironmentId());
        if (!report.getToolchain().equals(baseline.getToolchain())
                || !report.getToolchain().equals(candidate.getToolchain())) {
            failures.add("arm64 构建工具链与版本化基线报告不一致");
        }
        requireEqual(failures, "候选 Profile ID", expectedProfileId,
                candidate.getProfileId());
        requireEqual(failures, "候选 Revision ID", expectedRevisionId,
                candidate.getRevisionId());
        if (candidate.isRestoredFromCache()) {
            failures.add("arm64 Profile 性能门槛必须测量首次构建，不能使用缓存结果");
        }
        long limit = ProfileDurationStatistics.increasedLimit(
                baseline.getDurationMillis(), 25);
        if (candidate.getDurationMillis() > limit) {
            failures.add("首次 arm64 Profile 构建超过旧壳基线 +25%：baseline="
                    + baseline.getDurationMillis() + "，limit=" + limit + "，actual="
                    + candidate.getDurationMillis());
        }
        return new ProfileArm64BuildPerformanceGateResult(
                baseline.getDurationMillis(), limit, candidate.getDurationMillis(), failures);
    }

    private static void requireEqual(List<String> failures, String label,
                                     String expected, String actual) {
        if (!expected.equals(actual)) {
            failures.add(label + " 不匹配：expected=" + expected + "，actual=" + actual);
        }
    }

    private static void requireText(String label, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
    }
}
