package com.luoye.dpt.profile.gate.structure;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class StructuralFeatureSet {
    private final String language;
    private final String scope;
    private final Set<String> features;

    public StructuralFeatureSet(String language, String scope, Set<String> features) {
        if (language == null || language.trim().isEmpty()
                || scope == null || scope.trim().isEmpty() || features == null) {
            throw new IllegalArgumentException("structural feature metadata must not be blank");
        }
        this.language = language.trim();
        this.scope = scope.trim();
        this.features = Collections.unmodifiableSet(new LinkedHashSet<>(features));
    }

    public String getLanguage() {
        return language;
    }

    public String getScope() {
        return scope;
    }

    public Set<String> getFeatures() {
        return features;
    }
}
