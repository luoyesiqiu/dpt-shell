package com.luoye.dpt.profile.gate.performance;

import com.luoye.dpt.profile.gate.baseline.ProfileBaselineReport;
import com.luoye.dpt.profile.gate.baseline.ProfileBaselineReportCodec;

import java.util.ArrayList;
import java.util.List;

public final class ProfileColdStartPerformanceGate {
    public static final int MINIMUM_SAMPLE_COUNT = 30;

    private ProfileColdStartPerformanceGate() {
    }

    public static ProfileColdStartPerformanceGateResult evaluate(
            ProfileBaselineReport report, String expectedCandidateApkSha256,
            ProfileColdStartBenchmark baseline,
            ProfileColdStartBenchmark candidate) {
        ProfileBaselineReportCodec.validate(report);
        requireSha256(expectedCandidateApkSha256);
        if (baseline == null || candidate == null) {
            throw new IllegalArgumentException("冷启动基线和候选证据不能为空");
        }
        List<String> failures = new ArrayList<>();
        if (!report.getLegacyProtectedApk().getSha256().equals(baseline.getApkSha256())) {
            failures.add("冷启动旧 DPT APK SHA-256 与基线报告不匹配");
        }
        if (!expectedCandidateApkSha256.equals(candidate.getApkSha256())) {
            failures.add("冷启动候选 APK SHA-256 与发布候选不匹配");
        }
        String expectedEnvironment = report.getDeviceEnvironment().getId();
        if (!expectedEnvironment.equals(baseline.getDeviceEnvironmentId())
                || !expectedEnvironment.equals(candidate.getDeviceEnvironmentId())) {
            failures.add("冷启动设备环境与基线报告不匹配：expected="
                    + expectedEnvironment + "，baseline=" + baseline.getDeviceEnvironmentId()
                    + "，candidate=" + candidate.getDeviceEnvironmentId());
        }
        if (baseline.getSamplesMillis().size() < MINIMUM_SAMPLE_COUNT
                || candidate.getSamplesMillis().size() < MINIMUM_SAMPLE_COUNT) {
            failures.add("冷启动样本不足 30 次：baseline="
                    + baseline.getSamplesMillis().size() + "，candidate="
                    + candidate.getSamplesMillis().size());
        }
        if (baseline.getCrashCount() != 0 || candidate.getCrashCount() != 0) {
            failures.add("冷启动崩溃次数必须为零：baseline=" + baseline.getCrashCount()
                    + "，candidate=" + candidate.getCrashCount());
        }
        if (baseline.getAnrCount() != 0 || candidate.getAnrCount() != 0) {
            failures.add("冷启动 ANR 次数必须为零：baseline=" + baseline.getAnrCount()
                    + "，candidate=" + candidate.getAnrCount());
        }

        long baselineP50 = percentileOrZero(baseline.getSamplesMillis(), 50);
        long baselineP95 = percentileOrZero(baseline.getSamplesMillis(), 95);
        long candidateP50 = percentileOrZero(candidate.getSamplesMillis(), 50);
        long candidateP95 = percentileOrZero(candidate.getSamplesMillis(), 95);
        long p50Limit = baselineP50 == 0L ? 0L
                : ProfileDurationStatistics.increasedLimit(baselineP50, 5);
        long p95Limit = baselineP95 == 0L ? 0L
                : ProfileDurationStatistics.increasedLimit(baselineP95, 10);
        boolean p50Passed = candidateP50 > 0L && candidateP50 <= p50Limit;
        boolean p95Passed = candidateP95 > 0L && candidateP95 <= p95Limit;
        if (!p50Passed) {
            failures.add("冷启动 P50 超过基线 +5%：baseline=" + baselineP50
                    + "，limit=" + p50Limit + "，actual=" + candidateP50);
        }
        if (!p95Passed) {
            failures.add("冷启动 P95 超过基线 +10%：baseline=" + baselineP95
                    + "，limit=" + p95Limit + "，actual=" + candidateP95);
        }
        return new ProfileColdStartPerformanceGateResult(
                baselineP50, baselineP95, candidateP50, candidateP95,
                p50Limit, p95Limit, p50Passed, p95Passed,
                baseline.getSamplesMillis(), candidate.getSamplesMillis(), failures);
    }

    private static long percentileOrZero(List<Long> samples, int percentile) {
        return samples.isEmpty() ? 0L : ProfileDurationStatistics.percentile(samples, percentile);
    }

    private static void requireSha256(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("冷启动候选 APK SHA-256 格式无效");
        }
    }
}
