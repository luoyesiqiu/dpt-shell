package com.luoye.dpt.profile.gate.structure;

import com.luoye.dpt.profile.model.RevisionStatus;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ProfileStructuralComparisonGateTest {

    @Test
    public void comparesEveryActiveRevisionButOnlyOtherApplicationsCanBlock() {
        ProfileStructuralRevision candidate = revision("com.example.current", "p-current",
                "r000002", RevisionStatus.CANDIDATE, range("j", 0, 9), range("n", 0, 17));
        ProfileStructuralRevision crossAppOverLimit = revision("com.example.alpha", "p-alpha",
                "r000001", RevisionStatus.ACTIVE, range("j", 0, 10), range("n", 20, 37));
        ProfileStructuralRevision crossAppAtLimit = revision("com.example.beta", "p-beta",
                "r000003", RevisionStatus.ACTIVE,
                withExtra(range("j", 0, 8), "j99"), range("n", 0, 20));
        ProfileStructuralRevision sameApplication = revision("com.example.current", "p-current",
                "r000001", RevisionStatus.ACTIVE, range("j", 0, 9), range("n", 0, 17));
        ProfileStructuralRevision neverActivated = revision("com.example.rejected", "p-rejected",
                "r000001", RevisionStatus.REJECTED, range("j", 0, 9), range("n", 0, 17));
        ProfileStructuralRevision onlyVerified = revision("com.example.verified", "p-verified",
                "r000001", RevisionStatus.VERIFIED, range("j", 0, 9), range("n", 0, 17));

        ProfileStructuralComparisonResult result = new ProfileStructuralComparisonGate().evaluate(
                candidate, Arrays.asList(crossAppOverLimit, crossAppAtLimit, sameApplication,
                        neverActivated, onlyVerified));

        Assert.assertFalse(result.isPassed());
        Assert.assertEquals(3, result.getComparisons().size());
        ProfileStructuralComparisonResult.Comparison over = result.getComparisons().get(0);
        Assert.assertTrue(over.isBlocking());
        Assert.assertFalse(over.isPassed());
        Assert.assertEquals(0.9d, over.getJavaSimilarity(), 0.0d);
        ProfileStructuralComparisonResult.Comparison threshold = result.getComparisons().get(1);
        Assert.assertEquals(0.8d, threshold.getJavaSimilarity(), 0.0d);
        Assert.assertEquals(0.85d, threshold.getNativeSimilarity(), 0.0d);
        Assert.assertTrue(threshold.isPassed());
        ProfileStructuralComparisonResult.Comparison same = result.getComparisons().get(2);
        Assert.assertFalse(same.isBlocking());
        Assert.assertTrue(same.isPassed());
        Assert.assertEquals(1.0d, same.getJavaSimilarity(), 0.0d);
    }

    @Test
    public void exactThresholdsPassAsHardLimits() {
        ProfileStructuralRevision candidate = revision("com.example.current", "p-current",
                "r000002", RevisionStatus.CANDIDATE, range("j", 0, 4), range("n", 0, 17));
        ProfileStructuralRevision history = revision("com.example.other", "p-other",
                "r000001", RevisionStatus.ACTIVE, range("j", 0, 5), range("n", 0, 20));

        ProfileStructuralComparisonResult result = new ProfileStructuralComparisonGate()
                .evaluate(candidate, Arrays.asList(history));

        Assert.assertTrue(result.getComparisons().toString(), result.isPassed());
        Assert.assertEquals(ProfileStructuralComparisonGate.JAVA_SIMILARITY_LIMIT,
                result.getComparisons().get(0).getJavaSimilarity(), 0.0d);
        Assert.assertEquals(ProfileStructuralComparisonGate.NATIVE_SIMILARITY_LIMIT,
                result.getComparisons().get(0).getNativeSimilarity(), 0.0d);
    }

    private static ProfileStructuralRevision revision(String applicationId, String profileId,
                                                       String revisionId, RevisionStatus status,
                                                       Set<String> javaFeatures,
                                                       Set<String> nativeFeatures) {
        return new ProfileStructuralRevision(applicationId, profileId, revisionId, status,
                new StructuralFeatureSet("java", "java-variable", javaFeatures),
                new StructuralFeatureSet("native", "native-variable", nativeFeatures));
    }

    private static Set<String> range(String prefix, int startInclusive, int endExclusive) {
        List<String> values = new ArrayList<>();
        for (int value = startInclusive; value < endExclusive; value++) {
            values.add(prefix + value);
        }
        return new LinkedHashSet<>(values);
    }

    private static Set<String> withExtra(Set<String> values, String extra) {
        Set<String> result = new LinkedHashSet<>(values);
        result.add(extra);
        return result;
    }
}
