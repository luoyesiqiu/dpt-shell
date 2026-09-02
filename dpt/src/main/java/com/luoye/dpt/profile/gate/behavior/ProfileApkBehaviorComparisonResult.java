package com.luoye.dpt.profile.gate.behavior;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class ProfileApkBehaviorComparisonResult {
    private final ProfileApkBehaviorSnapshot baseline;
    private final ProfileApkBehaviorSnapshot candidate;
    private final Set<String> addedPermissions;
    private final Set<String> addedExportedComponents;
    private final Set<String> addedDependencies;
    private final Set<String> addedNetworkCapabilities;
    private final Set<String> addedDynamicCodeCapabilities;
    private final List<String> failureReasons;

    ProfileApkBehaviorComparisonResult(ProfileApkBehaviorSnapshot baseline,
                                       ProfileApkBehaviorSnapshot candidate,
                                       Set<String> addedPermissions,
                                       Set<String> addedExportedComponents,
                                       Set<String> addedDependencies,
                                       Set<String> addedNetworkCapabilities,
                                       Set<String> addedDynamicCodeCapabilities,
                                       List<String> failureReasons) {
        this.baseline = baseline;
        this.candidate = candidate;
        this.addedPermissions = immutableSortedCopy(addedPermissions);
        this.addedExportedComponents = immutableSortedCopy(addedExportedComponents);
        this.addedDependencies = immutableSortedCopy(addedDependencies);
        this.addedNetworkCapabilities = immutableSortedCopy(addedNetworkCapabilities);
        this.addedDynamicCodeCapabilities = immutableSortedCopy(addedDynamicCodeCapabilities);
        this.failureReasons = Collections.unmodifiableList(new ArrayList<>(failureReasons));
    }

    public boolean isPassed() {
        return failureReasons.isEmpty();
    }

    public ProfileApkBehaviorSnapshot getBaseline() {
        return baseline;
    }

    public ProfileApkBehaviorSnapshot getCandidate() {
        return candidate;
    }

    public Set<String> getAddedPermissions() {
        return addedPermissions;
    }

    public Set<String> getAddedExportedComponents() {
        return addedExportedComponents;
    }

    public Set<String> getAddedDependencies() {
        return addedDependencies;
    }

    public Set<String> getAddedNetworkCapabilities() {
        return addedNetworkCapabilities;
    }

    public Set<String> getAddedDynamicCodeCapabilities() {
        return addedDynamicCodeCapabilities;
    }

    public List<String> getFailureReasons() {
        return failureReasons;
    }

    private static Set<String> immutableSortedCopy(Set<String> values) {
        return Collections.unmodifiableSet(new TreeSet<>(values));
    }
}
