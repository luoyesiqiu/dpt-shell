package com.luoye.dpt.profile.gate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable result of the release hard-gate aggregation. */
public final class ProfileUnifiedGateResult {
    private final Map<String, String> statuses;
    private final List<String> failureReasons;

    ProfileUnifiedGateResult(Map<String, String> statuses, List<String> failureReasons) {
        this.statuses = Collections.unmodifiableMap(new LinkedHashMap<>(statuses));
        this.failureReasons = Collections.unmodifiableList(new ArrayList<>(failureReasons));
    }

    public Map<String, String> getStatuses() {
        return statuses;
    }

    public List<String> getFailureReasons() {
        return failureReasons;
    }

    public boolean isPassed() {
        return failureReasons.isEmpty();
    }
}
