package com.luoye.dpt.profile.gate.performance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ProfileColdStartPerformanceGateResult {
    private final long baselineP50Millis;
    private final long baselineP95Millis;
    private final long candidateP50Millis;
    private final long candidateP95Millis;
    private final long p50LimitMillis;
    private final long p95LimitMillis;
    private final boolean p50Passed;
    private final boolean p95Passed;
    private final List<Long> baselineSamplesMillis;
    private final List<Long> candidateSamplesMillis;
    private final List<String> failureReasons;

    ProfileColdStartPerformanceGateResult(long baselineP50Millis,
                                          long baselineP95Millis,
                                          long candidateP50Millis,
                                          long candidateP95Millis,
                                          long p50LimitMillis,
                                          long p95LimitMillis,
                                          boolean p50Passed,
                                          boolean p95Passed,
                                          List<Long> baselineSamplesMillis,
                                          List<Long> candidateSamplesMillis,
                                          List<String> failureReasons) {
        this.baselineP50Millis = baselineP50Millis;
        this.baselineP95Millis = baselineP95Millis;
        this.candidateP50Millis = candidateP50Millis;
        this.candidateP95Millis = candidateP95Millis;
        this.p50LimitMillis = p50LimitMillis;
        this.p95LimitMillis = p95LimitMillis;
        this.p50Passed = p50Passed;
        this.p95Passed = p95Passed;
        this.baselineSamplesMillis = immutableCopy(baselineSamplesMillis);
        this.candidateSamplesMillis = immutableCopy(candidateSamplesMillis);
        this.failureReasons = Collections.unmodifiableList(new ArrayList<>(failureReasons));
    }

    public boolean isPassed() {
        return failureReasons.isEmpty();
    }

    public long getBaselineP50Millis() {
        return baselineP50Millis;
    }

    public long getBaselineP95Millis() {
        return baselineP95Millis;
    }

    public long getCandidateP50Millis() {
        return candidateP50Millis;
    }

    public long getCandidateP95Millis() {
        return candidateP95Millis;
    }

    public long getP50LimitMillis() {
        return p50LimitMillis;
    }

    public long getP95LimitMillis() {
        return p95LimitMillis;
    }

    public boolean isP50Passed() {
        return p50Passed;
    }

    public boolean isP95Passed() {
        return p95Passed;
    }

    public List<Long> getBaselineSamplesMillis() {
        return baselineSamplesMillis;
    }

    public List<Long> getCandidateSamplesMillis() {
        return candidateSamplesMillis;
    }

    public List<String> getFailureReasons() {
        return failureReasons;
    }

    private static List<Long> immutableCopy(List<Long> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
