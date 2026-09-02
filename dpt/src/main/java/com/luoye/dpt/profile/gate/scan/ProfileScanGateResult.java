package com.luoye.dpt.profile.gate.scan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Result of comparing two manually supplied scan reports. */
public final class ProfileScanGateResult {
    private final ProfileScanReport baseline;
    private final ProfileScanReport candidate;
    private final boolean comparable;
    private final int baselineDetectionCount;
    private final int candidateDetectionCount;
    private final Set<String> newHighRiskFamilies;
    private final List<String> failureReasons;

    ProfileScanGateResult(ProfileScanReport baseline, ProfileScanReport candidate,
                          boolean comparable, int baselineDetectionCount,
                          int candidateDetectionCount, Set<String> newHighRiskFamilies,
                          List<String> failureReasons) {
        this.baseline = baseline;
        this.candidate = candidate;
        this.comparable = comparable;
        this.baselineDetectionCount = baselineDetectionCount;
        this.candidateDetectionCount = candidateDetectionCount;
        this.newHighRiskFamilies = Collections.unmodifiableSet(
                new LinkedHashSet<>(newHighRiskFamilies));
        this.failureReasons = Collections.unmodifiableList(new ArrayList<>(failureReasons));
    }

    public ProfileScanReport getBaseline() {
        return baseline;
    }

    public ProfileScanReport getCandidate() {
        return candidate;
    }

    public boolean isComparable() {
        return comparable;
    }

    public int getBaselineDetectionCount() {
        return baselineDetectionCount;
    }

    public int getCandidateDetectionCount() {
        return candidateDetectionCount;
    }

    public Set<String> getNewHighRiskFamilies() {
        return newHighRiskFamilies;
    }

    public List<String> getFailureReasons() {
        return failureReasons;
    }

    public boolean isPassed() {
        return comparable && failureReasons.isEmpty();
    }
}
