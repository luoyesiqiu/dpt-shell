package com.luoye.dpt.profile.gate.behavior;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class ProfileApkBehaviorGate {
    private ProfileApkBehaviorGate() {
    }

    public static ProfileApkBehaviorComparisonResult evaluate(
            Path legacyProtectedApk, ProfileApkBehaviorScanPolicy legacyPolicy,
            Path candidateApk, ProfileApkBehaviorScanPolicy candidatePolicy) throws IOException {
        ProfileApkBehaviorSnapshot baseline = ProfileApkBehaviorAnalyzer.analyze(
                legacyProtectedApk, legacyPolicy);
        ProfileApkBehaviorSnapshot candidate = ProfileApkBehaviorAnalyzer.analyze(
                candidateApk, candidatePolicy);
        return compare(baseline, candidate);
    }

    public static ProfileApkBehaviorComparisonResult compare(
            ProfileApkBehaviorSnapshot baseline, ProfileApkBehaviorSnapshot candidate) {
        if (baseline == null || candidate == null) {
            throw new IllegalArgumentException("APK 静态行为快照不能为空");
        }
        Set<String> addedPermissions = additions(candidate.getPermissions(),
                baseline.getPermissions());
        Set<String> addedComponents = additions(candidate.getExportedComponents(),
                baseline.getExportedComponents());
        Set<String> addedDependencies = additions(candidate.getDependencies(),
                baseline.getDependencies());
        Set<String> addedNetwork = additions(candidate.getNetworkCapabilities(),
                baseline.getNetworkCapabilities());
        Set<String> addedDynamicCode = additions(candidate.getDynamicCodeCapabilities(),
                baseline.getDynamicCodeCapabilities());
        List<String> failures = new ArrayList<>();
        if (!baseline.getApplicationId().equals(candidate.getApplicationId())) {
            failures.add("候选 APK applicationId 与旧 DPT 基线不一致：baseline="
                    + baseline.getApplicationId() + "，candidate="
                    + candidate.getApplicationId());
        }
        addFailure(failures, "新增权限", addedPermissions);
        addFailure(failures, "新增导出组件", addedComponents);
        addFailure(failures, "新增依赖", addedDependencies);
        addFailure(failures, "新增网络能力", addedNetwork);
        addFailure(failures, "新增远程动态代码能力", addedDynamicCode);
        return new ProfileApkBehaviorComparisonResult(baseline, candidate,
                addedPermissions, addedComponents, addedDependencies, addedNetwork,
                addedDynamicCode, failures);
    }

    private static Set<String> additions(Set<String> candidate, Set<String> baseline) {
        Set<String> result = new TreeSet<>(candidate);
        result.removeAll(baseline);
        return result;
    }

    private static void addFailure(List<String> failures, String label, Set<String> additions) {
        if (!additions.isEmpty()) {
            failures.add(label + "：" + String.join(",", additions));
        }
    }
}
