package com.luoye.dpt.profile.gate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ProfileStaticIdentifierReport {
    private final int catalogVersion;
    private final Map<String, Map<String, Integer>> hits;

    ProfileStaticIdentifierReport(int catalogVersion,
                                  Map<String, Map<String, Integer>> hits) {
        this.catalogVersion = catalogVersion;
        Map<String, Map<String, Integer>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Integer>> entry : hits.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableMap(
                    new LinkedHashMap<>(entry.getValue())));
        }
        this.hits = Collections.unmodifiableMap(copy);
    }

    public int getCatalogVersion() {
        return catalogVersion;
    }

    public Map<String, Map<String, Integer>> getHits() {
        return hits;
    }

    public boolean isPassed() {
        return hits.isEmpty();
    }
}
