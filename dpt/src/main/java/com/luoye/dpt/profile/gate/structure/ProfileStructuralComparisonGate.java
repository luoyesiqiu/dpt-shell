package com.luoye.dpt.profile.gate.structure;

import com.luoye.dpt.profile.model.RevisionStatus;

import java.util.ArrayList;
import java.util.List;

public final class ProfileStructuralComparisonGate {
    public static final double JAVA_SIMILARITY_LIMIT = 0.80d;
    public static final double NATIVE_SIMILARITY_LIMIT = 0.85d;

    public ProfileStructuralComparisonResult evaluate(ProfileStructuralRevision candidate,
                                                       List<ProfileStructuralRevision> history) {
        if (candidate == null || history == null) {
            throw new IllegalArgumentException("structural comparison inputs must not be null");
        }
        List<ProfileStructuralComparisonResult.Comparison> comparisons = new ArrayList<>();
        for (ProfileStructuralRevision revision : history) {
            if (revision == null) {
                throw new IllegalArgumentException("structural comparison history contains null");
            }
            // Revisions remain ACTIVE after a later activation, so this status is the complete
            // history of revisions that have actually been active.
            if (revision.getStatus() != RevisionStatus.ACTIVE
                    || sameRevision(candidate, revision)) {
                continue;
            }
            double javaSimilarity = JaccardSimilarity.compare(candidate.getJavaFeatures(),
                    revision.getJavaFeatures());
            double nativeSimilarity = JaccardSimilarity.compare(candidate.getNativeFeatures(),
                    revision.getNativeFeatures());
            boolean blocking = !candidate.getApplicationId().equals(revision.getApplicationId());
            boolean passed = !blocking || (javaSimilarity <= JAVA_SIMILARITY_LIMIT
                    && nativeSimilarity <= NATIVE_SIMILARITY_LIMIT);
            comparisons.add(new ProfileStructuralComparisonResult.Comparison(revision,
                    javaSimilarity, nativeSimilarity, blocking, passed));
        }
        return new ProfileStructuralComparisonResult(candidate, comparisons);
    }

    private static boolean sameRevision(ProfileStructuralRevision left,
                                        ProfileStructuralRevision right) {
        return left.getProfileId().equals(right.getProfileId())
                && left.getRevisionId().equals(right.getRevisionId());
    }
}
