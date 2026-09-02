package com.luoye.dpt.profile.gate.structure;

import java.util.HashSet;
import java.util.Set;

public final class JaccardSimilarity {
    private JaccardSimilarity() {
    }

    public static double compare(StructuralFeatureSet left, StructuralFeatureSet right) {
        if (left == null || right == null) {
            throw new IllegalArgumentException("structural feature sets must not be null");
        }
        Set<String> intersection = new HashSet<>(left.getFeatures());
        intersection.retainAll(right.getFeatures());
        Set<String> union = new HashSet<>(left.getFeatures());
        union.addAll(right.getFeatures());
        return union.isEmpty() ? 1.0d : (double) intersection.size() / union.size();
    }
}
