package com.luoye.dpt.profile.gate.structure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ProfileStructuralComparisonResult {
    private final ProfileStructuralRevision candidate;
    private final List<Comparison> comparisons;

    ProfileStructuralComparisonResult(ProfileStructuralRevision candidate,
                                      List<Comparison> comparisons) {
        this.candidate = candidate;
        this.comparisons = Collections.unmodifiableList(new ArrayList<>(comparisons));
    }

    public ProfileStructuralRevision getCandidate() {
        return candidate;
    }

    public List<Comparison> getComparisons() {
        return comparisons;
    }

    public boolean isPassed() {
        for (Comparison comparison : comparisons) {
            if (!comparison.isPassed()) {
                return false;
            }
        }
        return true;
    }

    public static final class Comparison {
        private final ProfileStructuralRevision history;
        private final double javaSimilarity;
        private final double nativeSimilarity;
        private final boolean blocking;
        private final boolean passed;

        Comparison(ProfileStructuralRevision history, double javaSimilarity,
                   double nativeSimilarity, boolean blocking, boolean passed) {
            this.history = history;
            this.javaSimilarity = javaSimilarity;
            this.nativeSimilarity = nativeSimilarity;
            this.blocking = blocking;
            this.passed = passed;
        }

        public ProfileStructuralRevision getHistory() {
            return history;
        }

        public double getJavaSimilarity() {
            return javaSimilarity;
        }

        public double getNativeSimilarity() {
            return nativeSimilarity;
        }

        public boolean isBlocking() {
            return blocking;
        }

        public boolean isPassed() {
            return passed;
        }

        @Override
        public String toString() {
            return history.getApplicationId() + "/" + history.getProfileId() + "/"
                    + history.getRevisionId() + " java=" + javaSimilarity + " native="
                    + nativeSimilarity + " blocking=" + blocking + " passed=" + passed;
        }
    }
}
