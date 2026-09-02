package com.luoye.dpt.profile.gate.behavior;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProfileBehaviorMatrixGate {
    private ProfileBehaviorMatrixGate() {
    }

    public static ProfileBehaviorMatrixGateResult evaluate(
            List<ProfileBehaviorCase> expectedCases,
            List<ProfileBehaviorObservation> observations) {
        if (expectedCases == null || expectedCases.isEmpty() || observations == null) {
            throw new IllegalArgumentException("行为矩阵用例和证据不能为空");
        }
        List<String> failures = new ArrayList<>();
        Map<String, ProfileBehaviorCase> expectedByKey = new LinkedHashMap<>();
        Map<String, Set<ProfileBehaviorVariant>> groups = new LinkedHashMap<>();
        for (ProfileBehaviorCase behaviorCase : expectedCases) {
            if (behaviorCase == null) {
                throw new IllegalArgumentException("行为矩阵用例不能包含空值");
            }
            if (expectedByKey.put(behaviorCase.key(), behaviorCase) != null) {
                failures.add("行为矩阵存在重复用例：" + behaviorCase.key());
            }
            groups.computeIfAbsent(behaviorCase.groupKey(), ignored ->
                    EnumSet.noneOf(ProfileBehaviorVariant.class)).add(behaviorCase.getVariant());
        }
        for (Map.Entry<String, Set<ProfileBehaviorVariant>> group : groups.entrySet()) {
            if (!group.getValue().equals(EnumSet.allOf(ProfileBehaviorVariant.class))) {
                failures.add("Recipe/Overlay 行为矩阵没有覆盖四类 APK：" + group.getKey()
                        + "，actual=" + group.getValue());
            }
        }

        Map<String, ProfileBehaviorObservation> observationsByKey = new LinkedHashMap<>();
        for (ProfileBehaviorObservation observation : observations) {
            if (observation == null) {
                throw new IllegalArgumentException("行为矩阵证据不能包含空值");
            }
            String key = observation.getBehaviorCase().key();
            if (observationsByKey.put(key, observation) != null) {
                failures.add("行为矩阵存在重复证据：" + key);
            }
        }

        List<ProfileBehaviorObservation> matched = new ArrayList<>();
        for (Map.Entry<String, ProfileBehaviorCase> expected : expectedByKey.entrySet()) {
            ProfileBehaviorObservation observation = observationsByKey.remove(expected.getKey());
            if (observation == null) {
                failures.add("缺少行为证据：" + expected.getKey());
                continue;
            }
            matched.add(observation);
            if (!expected.getValue().equals(observation.getBehaviorCase())) {
                failures.add("行为证据目标或候选 APK SHA-256 不匹配：" + expected.getKey());
            }
            validateObservation(expected.getKey(), observation, failures);
        }
        for (String unexpected : observationsByKey.keySet()) {
            failures.add("存在未登记的行为证据：" + unexpected);
        }
        return new ProfileBehaviorMatrixGateResult(matched, failures);
    }

    private static void validateObservation(String key, ProfileBehaviorObservation observation,
                                            List<String> failures) {
        if (!"arm64-v8a".equals(observation.getAbi())) {
            failures.add("行为证据 ABI 不是 arm64-v8a：" + key);
        }
        requirePassed(key, "Application", observation.isApplicationPassed(), failures);
        requirePassed(key, "Activity", observation.isActivityPassed(), failures);
        requirePassed(key, "Service", observation.isServicePassed(), failures);
        requirePassed(key, "Receiver", observation.isReceiverPassed(), failures);
        requirePassed(key, "Provider", observation.isProviderPassed(), failures);
        requirePassed(key, "ClassLoader", observation.isClassLoaderPassed(), failures);
        if (observation.getSdkInt() >= 28
                && observation.getBehaviorCase().getVariant().hasCustomComponentFactory()) {
            requirePassed(key, "AppComponentFactory 委托",
                    observation.isComponentFactoryDelegationPassed(), failures);
        }
        if (observation.getCrashCount() != 0) {
            failures.add("行为验证发生崩溃：" + key + "，count="
                    + observation.getCrashCount());
        }
        if (observation.getAnrCount() != 0) {
            failures.add("行为验证发生 ANR：" + key + "，count="
                    + observation.getAnrCount());
        }
    }

    private static void requirePassed(String key, String capability, boolean passed,
                                      List<String> failures) {
        if (!passed) {
            failures.add("行为验证失败：" + capability + "，case=" + key);
        }
    }
}
