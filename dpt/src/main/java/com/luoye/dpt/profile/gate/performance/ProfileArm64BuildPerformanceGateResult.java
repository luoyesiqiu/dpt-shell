package com.luoye.dpt.profile.gate.performance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ProfileArm64BuildPerformanceGateResult {
    private final long baselineMillis;
    private final long limitMillis;
    private final long candidateMillis;
    private final List<String> failureReasons;

    ProfileArm64BuildPerformanceGateResult(long baselineMillis, long limitMillis,
                                           long candidateMillis,
                                           List<String> failureReasons) {
        this.baselineMillis = baselineMillis;
        this.limitMillis = limitMillis;
        this.candidateMillis = candidateMillis;
        this.failureReasons = Collections.unmodifiableList(new ArrayList<>(failureReasons));
    }

    public boolean isPassed() {
        return failureReasons.isEmpty();
    }

    public long getBaselineMillis() {
        return baselineMillis;
    }

    public long getLimitMillis() {
        return limitMillis;
    }

    public long getCandidateMillis() {
        return candidateMillis;
    }

    public List<String> getFailureReasons() {
        return failureReasons;
    }
}
