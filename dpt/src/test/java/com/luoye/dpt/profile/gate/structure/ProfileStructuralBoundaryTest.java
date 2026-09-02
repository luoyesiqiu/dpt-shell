package com.luoye.dpt.profile.gate.structure;

import com.luoye.dpt.profile.generation.recipe.ProfileRecipeRegistry;
import com.luoye.dpt.profile.generation.recipe.ProfileRecipeSelector;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.model.RevisionStatus;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;

public class ProfileStructuralBoundaryTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void pureRenamingFailsCrossApplicationButSameApplicationOnlyReports()
            throws Exception {
        Path candidateSource = temporaryFolder.newFolder("candidate-java").toPath();
        Path historySource = temporaryFolder.newFolder("history-java").toPath();
        write(candidateSource.resolve("Candidate.java"),
                "package candidate.hidden; final class Candidate { int first(int value) {"
                        + "if (value > 7) return 1; return 0; } }");
        write(historySource.resolve("History.java"),
                "package history.privateid; final class History { int renamed(int argument) {"
                        + "if (argument > 99) return 8; return 3; } }");
        JavaStructuralFeatureExtractor extractor = new JavaStructuralFeatureExtractor();
        StructuralFeatureSet candidateJava = extractor.extract(candidateSource);
        StructuralFeatureSet historyJava = extractor.extract(historySource);
        StructuralFeatureSet nativeFeatures = new StructuralFeatureSet("native",
                "arm64-variable-objects", new LinkedHashSet<>(Arrays.asList(
                "native:function:block-count:2", "native:instruction:ret")));
        ProfileStructuralRevision candidate = revision("com.example.rename.candidate",
                "pf_candidate", "r000002", RevisionStatus.CANDIDATE, candidateJava,
                nativeFeatures);
        ProfileStructuralRevision crossApplication = revision("com.example.rename.history",
                "pf_history", "r000001", RevisionStatus.ACTIVE, historyJava,
                nativeFeatures);
        ProfileStructuralRevision sameApplication = revision("com.example.rename.candidate",
                "pf_candidate", "r000001", RevisionStatus.ACTIVE, historyJava,
                nativeFeatures);

        ProfileStructuralComparisonResult result = new ProfileStructuralComparisonGate()
                .evaluate(candidate, Arrays.asList(crossApplication, sameApplication));

        Assert.assertEquals(candidateJava.getFeatures(), historyJava.getFeatures());
        Assert.assertFalse(result.isPassed());
        Assert.assertEquals(2, result.getComparisons().size());
        Assert.assertTrue(result.getComparisons().get(0).isBlocking());
        Assert.assertFalse(result.getComparisons().get(0).isPassed());
        Assert.assertEquals(1.0d, result.getComparisons().get(0).getJavaSimilarity(), 0.0d);
        Assert.assertFalse(result.getComparisons().get(1).isBlocking());
        Assert.assertTrue(result.getComparisons().get(1).isPassed());
    }

    @Test
    public void everyActiveRevisionIsComparedAndNoEligibleRecipeFailsStrictly() {
        StructuralFeatureSet javaFeatures = new StructuralFeatureSet("java", "java-variable",
                new LinkedHashSet<>(Arrays.asList("same-java")));
        StructuralFeatureSet nativeFeatures = new StructuralFeatureSet("native",
                "native-variable", new LinkedHashSet<>(Arrays.asList("same-native")));
        ProfileStructuralRevision candidate = revision("com.example.current", "pf_current",
                "r000003", RevisionStatus.CANDIDATE, javaFeatures, nativeFeatures);
        ProfileStructuralComparisonResult result = new ProfileStructuralComparisonGate().evaluate(
                candidate, Arrays.asList(
                        revision("com.example.alpha", "pf_alpha", "r000001",
                                RevisionStatus.ACTIVE, javaFeatures, nativeFeatures),
                        revision("com.example.alpha", "pf_alpha", "r000002",
                                RevisionStatus.ACTIVE, javaFeatures, nativeFeatures),
                        revision("com.example.current", "pf_current", "r000001",
                                RevisionStatus.ACTIVE, javaFeatures, nativeFeatures),
                        revision("com.example.pending", "pf_pending", "r000001",
                                RevisionStatus.CANDIDATE, javaFeatures, nativeFeatures)));

        Assert.assertEquals(3, result.getComparisons().size());
        Assert.assertEquals("r000001",
                result.getComparisons().get(0).getHistory().getRevisionId());
        Assert.assertEquals("r000002",
                result.getComparisons().get(1).getHistory().getRevisionId());
        Assert.assertFalse(result.getComparisons().get(2).isBlocking());

        try {
            ProfileRecipeSelector.builtIn().select(null, "com.example.new", Arrays.asList(
                    activeRecipe("com.example.alpha", ProfileRecipeRegistry.RECIPE_A),
                    activeRecipe("com.example.beta", ProfileRecipeRegistry.RECIPE_B)));
            Assert.fail("Expected no eligible Recipe failure");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage()
                    .contains("没有候选 Recipe"));
        }
    }

    private static ProfileStructuralRevision revision(String applicationId, String profileId,
                                                       String revisionId, RevisionStatus status,
                                                       StructuralFeatureSet javaFeatures,
                                                       StructuralFeatureSet nativeFeatures) {
        return new ProfileStructuralRevision(applicationId, profileId, revisionId, status,
                javaFeatures, nativeFeatures);
    }

    private static ProfileRevisionManifest activeRecipe(String applicationId, String recipeId) {
        ProfileRevisionManifest revision = new ProfileRevisionManifest();
        revision.setApplicationId(applicationId);
        revision.setRecipeId(recipeId);
        revision.setStatus(RevisionStatus.ACTIVE);
        return revision;
    }

    private static void write(Path path, String content) throws Exception {
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
