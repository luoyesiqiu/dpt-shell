package com.luoye.dpt.profile.gate.behavior;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProfileBehaviorMatrixGateTest {
    @Test
    public void acceptsEveryVariantForBothRecipesAndLegalOverlayRevision() {
        List<ProfileBehaviorCase> expected = expectedCases();
        List<ProfileBehaviorObservation> observations = passingObservations(expected);

        ProfileBehaviorMatrixGateResult result = ProfileBehaviorMatrixGate.evaluate(
                expected, observations);

        Assert.assertTrue(result.getFailureReasons().toString(), result.isPassed());
        Assert.assertEquals(12, result.getMatchedObservations().size());
    }

    @Test
    public void rejectsMissingVariantOrFailedApplicationComponentAndClassLoaderBehavior() {
        List<ProfileBehaviorCase> expected = expectedCases();
        List<ProfileBehaviorObservation> observations = passingObservations(expected);
        observations.remove(0);
        ProfileBehaviorObservation failed = observations.get(0);
        observations.set(0, failed.withResults(false, true, false, true,
                true, false, true, 0, 0));

        ProfileBehaviorMatrixGateResult result = ProfileBehaviorMatrixGate.evaluate(
                expected, observations);

        Assert.assertFalse(result.isPassed());
        String failures = result.getFailureReasons().toString();
        Assert.assertTrue(failures, failures.contains("缺少行为证据"));
        Assert.assertTrue(failures, failures.contains("Application"));
        Assert.assertTrue(failures, failures.contains("Service"));
        Assert.assertTrue(failures, failures.contains("ClassLoader"));
    }

    private static List<ProfileBehaviorCase> expectedCases() {
        List<ProfileBehaviorCase> result = new ArrayList<>();
        addGroup(result, "recipe-a", repeat('0'), "base-a");
        addGroup(result, "recipe-b", repeat('0'), "base-b");
        addGroup(result, "recipe-a", repeat('e'), "overlay-a");
        return result;
    }

    private static void addGroup(List<ProfileBehaviorCase> target, String recipeId,
                                 String overlaySha256, String label) {
        int offset = target.size() + 1;
        int index = 0;
        for (ProfileBehaviorVariant variant : ProfileBehaviorVariant.values()) {
            target.add(new ProfileBehaviorCase(
                    "com.example.fixture." + label + "." + variant.name().toLowerCase(),
                    "prf_" + alpha(offset + index), "r000001", recipeId,
                    overlaySha256, variant, sha256(offset + index)));
            index++;
        }
    }

    private static List<ProfileBehaviorObservation> passingObservations(
            List<ProfileBehaviorCase> expected) {
        List<ProfileBehaviorObservation> result = new ArrayList<>();
        for (ProfileBehaviorCase behaviorCase : expected) {
            result.add(new ProfileBehaviorObservation(behaviorCase, "api35-arm64",
                    35, "arm64-v8a", true, true, true, true, true,
                    true, true, 0, 0));
        }
        return result;
    }

    private static String alpha(int value) {
        char character = (char) ('a' + (value % 26));
        return String.join("", Collections.nCopies(26, String.valueOf(character)));
    }

    private static String sha256(int value) {
        return String.format("%064x", value);
    }

    private static String repeat(char value) {
        return String.join("", Collections.nCopies(64, String.valueOf(value)));
    }
}
