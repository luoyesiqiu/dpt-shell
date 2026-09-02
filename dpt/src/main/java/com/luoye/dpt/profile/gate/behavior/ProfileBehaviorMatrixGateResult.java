package com.luoye.dpt.profile.gate.behavior;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ProfileBehaviorMatrixGateResult {
    private final List<ProfileBehaviorObservation> matchedObservations;
    private final List<String> failureReasons;

    ProfileBehaviorMatrixGateResult(List<ProfileBehaviorObservation> matchedObservations,
                                    List<String> failureReasons) {
        this.matchedObservations = Collections.unmodifiableList(
                new ArrayList<>(matchedObservations));
        this.failureReasons = Collections.unmodifiableList(new ArrayList<>(failureReasons));
    }

    public boolean isPassed() {
        return failureReasons.isEmpty();
    }

    public List<ProfileBehaviorObservation> getMatchedObservations() {
        return matchedObservations;
    }

    public List<String> getFailureReasons() {
        return failureReasons;
    }
}
