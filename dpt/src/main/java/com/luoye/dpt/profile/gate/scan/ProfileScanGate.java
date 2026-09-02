package com.luoye.dpt.profile.gate.scan;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Fail-closed comparison for reports exported by independent scanners.
 *
 * <p>Reports are comparable only when application, scanner IDs/versions and the exact time
 * window match.  No network operation is performed here.</p>
 */
public final class ProfileScanGate {
    private static final Set<String> HIGH_RISK_FAMILIES = new LinkedHashSet<>();

    static {
        HIGH_RISK_FAMILIES.add("trojan");
        HIGH_RISK_FAMILIES.add("banker");
        HIGH_RISK_FAMILIES.add("spyware");
        HIGH_RISK_FAMILIES.add("rat");
    }

    private ProfileScanGate() {
    }

    public static ProfileScanGateResult evaluate(ProfileScanReport baseline,
                                                 ProfileScanReport candidate) {
        return evaluate(null, baseline, candidate);
    }

    public static ProfileScanGateResult evaluate(String expectedCandidateApkSha256,
                                                 ProfileScanReport baseline,
                                                 ProfileScanReport candidate) {
        return evaluate(null, expectedCandidateApkSha256, baseline, candidate);
    }

    /**
     * Strict comparison bound to the application and candidate artifact selected by the caller.
     * The two expected values are deliberately supplied separately from the imported reports so
     * a pair of reports for another APK cannot be accepted accidentally.
     */
    public static ProfileScanGateResult evaluate(String expectedApplicationId,
                                                 String expectedCandidateApkSha256,
                                                 ProfileScanReport baseline,
                                                 ProfileScanReport candidate) {
        ProfileScanReportCodec.validate(baseline);
        ProfileScanReportCodec.validate(candidate);
        if (expectedApplicationId != null) {
            try {
                com.luoye.dpt.profile.validation.ProfileModelValidator
                        .validateApplicationId(expectedApplicationId);
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("期望 applicationId 格式无效", exception);
            }
        }
        if (expectedCandidateApkSha256 != null
                && !expectedCandidateApkSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("期望候选 APK SHA-256 格式无效");
        }
        List<String> failures = new ArrayList<>();
        boolean comparable = true;
        if (expectedApplicationId != null
                && !expectedApplicationId.equals(baseline.getApplicationId())) {
            comparable = false;
            failures.add("基线扫描报告 applicationId 与目标不一致：expected="
                    + expectedApplicationId + "，actual=" + baseline.getApplicationId());
        }
        if (expectedApplicationId != null
                && !expectedApplicationId.equals(candidate.getApplicationId())) {
            comparable = false;
            failures.add("候选扫描报告 applicationId 与目标不一致：expected="
                    + expectedApplicationId + "，actual=" + candidate.getApplicationId());
        }
        if (!baseline.getApplicationId().equals(candidate.getApplicationId())) {
            comparable = false;
            failures.add("扫描报告 applicationId 不一致：baseline="
                    + baseline.getApplicationId() + "，candidate="
                    + candidate.getApplicationId());
        }
        if (!baseline.getScannerSet().equals(candidate.getScannerSet())) {
            comparable = false;
            failures.add("扫描器集合不一致");
        }
        if (!baseline.getWindowId().equals(candidate.getWindowId())
                || baseline.getWindowStartEpochMillis() != candidate.getWindowStartEpochMillis()
                || baseline.getWindowEndEpochMillis() != candidate.getWindowEndEpochMillis()) {
            comparable = false;
            failures.add("扫描时间窗口不一致");
        }
        if (expectedCandidateApkSha256 != null
                && !expectedCandidateApkSha256.equals(candidate.getApkSha256())) {
            comparable = false;
            failures.add("扫描报告候选 APK SHA-256 不匹配：期望="
                    + expectedCandidateApkSha256 + "，实际=" + candidate.getApkSha256());
        }

        int baselineCount = baseline.getDetections().size();
        int candidateCount = candidate.getDetections().size();
        if (comparable && candidateCount > baselineCount) {
            failures.add("候选检测数高于旧 DPT 基线：baseline=" + baselineCount
                    + "，candidate=" + candidateCount);
        }
        Set<String> baselineRisk = highRiskFamilies(baseline);
        Set<String> candidateRisk = highRiskFamilies(candidate);
        candidateRisk.removeAll(baselineRisk);
        if (!candidateRisk.isEmpty()) {
            failures.add("候选新增高风险家族标签：" + String.join(",", candidateRisk));
        }
        return new ProfileScanGateResult(baseline, candidate, comparable, baselineCount,
                candidateCount, candidateRisk, failures);
    }

    private static Set<String> highRiskFamilies(ProfileScanReport report) {
        Set<String> result = new LinkedHashSet<>();
        for (ProfileScanReport.Detection detection : report.getDetections()) {
            String[] values = {detection.getCategory(), detection.getFamily(),
                    detection.getSignature()};
            for (String value : values) {
                String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
                for (String family : HIGH_RISK_FAMILIES) {
                    if (containsFamily(normalized, family)) {
                        result.add(family);
                    }
                }
            }
        }
        return result;
    }

    private static boolean containsFamily(String value, String family) {
        if ("rat".equals(family)) {
            return value.matches(".*(^|[^a-z])rat([^a-z]|$).*" );
        }
        return value.matches(".*(^|[^a-z])" + family + "([^a-z]|$).*");
    }
}
