package com.luoye.dpt.profile.gate.performance;

import com.luoye.dpt.profile.gate.baseline.ProfileBaselineReport;
import com.luoye.dpt.profile.gate.baseline.ProfileBaselineReportCodec;

import java.util.ArrayList;
import java.util.List;

public final class ProfileApkProtectionPerformanceGate {
    public static final int REQUIRED_RUN_COUNT = 10;

    private ProfileApkProtectionPerformanceGate() {
    }

    public static ProfileApkProtectionPerformanceGateResult evaluate(
            ProfileBaselineReport report, String expectedProfileId,
            String expectedRevisionId, String expectedCandidateApkSha256,
            ProfileApkProtectionBenchmark baseline,
            ProfileApkProtectionBenchmark candidate) {
        ProfileBaselineReportCodec.validate(report);
        requireText("期望 Profile ID", expectedProfileId);
        requireText("期望 Revision ID", expectedRevisionId);
        requireSha256(expectedCandidateApkSha256);
        if (baseline == null || candidate == null) {
            throw new IllegalArgumentException("APK 加固基线和候选证据不能为空");
        }
        List<String> failures = new ArrayList<>();
        if (baseline.getKind() != ProfileApkProtectionBenchmark.Kind.LEGACY_DPT) {
            failures.add("APK 加固基线必须来自旧 DPT");
        }
        if (candidate.getKind() != ProfileApkProtectionBenchmark.Kind.ACTIVE_PROFILE) {
            failures.add("APK 加固候选必须来自已有活动 Profile");
        }
        requireEqual(failures, "输入 APK SHA-256", report.getInputApk().getSha256(),
                baseline.getInputApkSha256());
        requireEqual(failures, "候选输入 APK SHA-256", report.getInputApk().getSha256(),
                candidate.getInputApkSha256());
        requireEqual(failures, "旧 DPT APK SHA-256",
                report.getLegacyProtectedApk().getSha256(),
                baseline.getProtectedApkSha256());
        requireEqual(failures, "候选 APK SHA-256", expectedCandidateApkSha256,
                candidate.getProtectedApkSha256());
        requireEqual(failures, "候选 Profile ID", expectedProfileId,
                candidate.getProfileId());
        requireEqual(failures, "候选 Revision ID", expectedRevisionId,
                candidate.getRevisionId());
        String expectedEnvironment = report.getCiEnvironment().getId();
        requireEqual(failures, "旧 DPT APK 加固环境", expectedEnvironment,
                baseline.getExecutionEnvironmentId());
        requireEqual(failures, "候选 APK 加固环境", expectedEnvironment,
                candidate.getExecutionEnvironmentId());
        if (baseline.getSamplesMillis().size() != REQUIRED_RUN_COUNT
                || candidate.getSamplesMillis().size() != REQUIRED_RUN_COUNT) {
            failures.add("APK 加固样本必须恰好为 10 次：baseline="
                    + baseline.getSamplesMillis().size() + "，candidate="
                    + candidate.getSamplesMillis().size());
        }

        long baselineP50 = percentileOrZero(baseline.getSamplesMillis(), 50);
        long baselineP95 = percentileOrZero(baseline.getSamplesMillis(), 95);
        long candidateP50 = percentileOrZero(candidate.getSamplesMillis(), 50);
        long candidateP95 = percentileOrZero(candidate.getSamplesMillis(), 95);
        long p50Limit = baselineP50 == 0L ? 0L
                : ProfileDurationStatistics.increasedLimit(baselineP50, 10);
        long p95Limit = baselineP95 == 0L ? 0L
                : ProfileDurationStatistics.increasedLimit(baselineP95, 15);
        boolean p50Passed = candidateP50 > 0L && candidateP50 <= p50Limit;
        boolean p95Passed = candidateP95 > 0L && candidateP95 <= p95Limit;
        if (!p50Passed) {
            failures.add("APK 加固 P50 超过旧 DPT 基线 +10%：baseline="
                    + baselineP50 + "，limit=" + p50Limit + "，actual="
                    + candidateP50);
        }
        if (!p95Passed) {
            failures.add("APK 加固 P95 超过旧 DPT 基线 +15%：baseline="
                    + baselineP95 + "，limit=" + p95Limit + "，actual="
                    + candidateP95);
        }
        return new ProfileApkProtectionPerformanceGateResult(
                baselineP50, baselineP95, candidateP50, candidateP95,
                p50Limit, p95Limit, p50Passed, p95Passed,
                baseline.getSamplesMillis(), candidate.getSamplesMillis(), failures);
    }

    private static long percentileOrZero(List<Long> samples, int percentile) {
        return samples.isEmpty() ? 0L : ProfileDurationStatistics.percentile(samples, percentile);
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

    private static void requireSha256(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("候选 APK SHA-256 格式无效");
        }
    }
}
