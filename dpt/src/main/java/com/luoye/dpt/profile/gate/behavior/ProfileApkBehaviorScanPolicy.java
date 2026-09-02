package com.luoye.dpt.profile.gate.behavior;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ProfileApkBehaviorScanPolicy {
    private final Set<String> ignoredClassPrefixes;
    private final Set<String> ignoredNativeLibraries;

    private ProfileApkBehaviorScanPolicy(Set<String> ignoredClassPrefixes,
                                         Set<String> ignoredNativeLibraries) {
        this.ignoredClassPrefixes = immutableCopy(ignoredClassPrefixes);
        this.ignoredNativeLibraries = immutableCopy(ignoredNativeLibraries);
    }

    public static ProfileApkBehaviorScanPolicy empty() {
        return of(Collections.emptySet(), Collections.emptySet());
    }

    public static ProfileApkBehaviorScanPolicy of(Set<String> ignoredClassPrefixes,
                                                  Set<String> ignoredNativeLibraries) {
        if (ignoredClassPrefixes == null || ignoredNativeLibraries == null) {
            throw new IllegalArgumentException("APK 静态扫描忽略规则不能为空");
        }
        return new ProfileApkBehaviorScanPolicy(ignoredClassPrefixes,
                ignoredNativeLibraries);
    }

    public Set<String> getIgnoredClassPrefixes() {
        return ignoredClassPrefixes;
    }

    public Set<String> getIgnoredNativeLibraries() {
        return ignoredNativeLibraries;
    }

    private static Set<String> immutableCopy(Set<String> values) {
        LinkedHashSet<String> copy = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException("APK 静态扫描忽略规则不能包含空值");
            }
            copy.add(value.trim());
        }
        return Collections.unmodifiableSet(copy);
    }
}
