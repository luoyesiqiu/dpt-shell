package com.luoye.dpt.profile.gate.behavior;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

public final class ProfileApkBehaviorSnapshot {
    private final String apkSha256;
    private final String applicationId;
    private final Set<String> permissions;
    private final Set<String> exportedComponents;
    private final Set<String> dependencies;
    private final Set<String> networkCapabilities;
    private final Set<String> dynamicCodeCapabilities;

    ProfileApkBehaviorSnapshot(String apkSha256, String applicationId,
                               Set<String> permissions,
                               Set<String> exportedComponents,
                               Set<String> dependencies,
                               Set<String> networkCapabilities,
                               Set<String> dynamicCodeCapabilities) {
        this.apkSha256 = apkSha256;
        this.applicationId = applicationId;
        this.permissions = immutableSortedCopy(permissions);
        this.exportedComponents = immutableSortedCopy(exportedComponents);
        this.dependencies = immutableSortedCopy(dependencies);
        this.networkCapabilities = immutableSortedCopy(networkCapabilities);
        this.dynamicCodeCapabilities = immutableSortedCopy(dynamicCodeCapabilities);
    }

    public String getApkSha256() {
        return apkSha256;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public Set<String> getExportedComponents() {
        return exportedComponents;
    }

    public Set<String> getDependencies() {
        return dependencies;
    }

    public Set<String> getNetworkCapabilities() {
        return networkCapabilities;
    }

    public Set<String> getDynamicCodeCapabilities() {
        return dynamicCodeCapabilities;
    }

    private static Set<String> immutableSortedCopy(Set<String> values) {
        return Collections.unmodifiableSet(new TreeSet<>(values));
    }
}
