package com.luoye.dpt.profile.generation.recipe;

import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.model.ProfileSchemaVersions;
import com.luoye.dpt.profile.model.RevisionStatus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ProfileRecipeSelector {
    private static final double JAVA_SIMILARITY_LIMIT = 0.80d;
    private static final double NATIVE_SIMILARITY_LIMIT = 0.85d;
    private static final ProfileRecipeSelector BUILT_IN = new ProfileRecipeSelector(
            ProfileRecipeRegistry.builtIn(), ProfileRecipeTemplateRegistry.builtIn());

    private final ProfileRecipeRegistry recipeRegistry;
    private final ProfileRecipeTemplateRegistry templateRegistry;

    private ProfileRecipeSelector(ProfileRecipeRegistry recipeRegistry,
                                  ProfileRecipeTemplateRegistry templateRegistry) {
        this.recipeRegistry = recipeRegistry;
        this.templateRegistry = templateRegistry;
    }

    public static ProfileRecipeSelector builtIn() {
        return BUILT_IN;
    }

    public ProfileRecipe select(String explicitRecipeId, String currentApplicationId,
                                List<ProfileRevisionManifest> revisionHistory) {
        if (currentApplicationId == null || currentApplicationId.trim().isEmpty()) {
            throw new IllegalArgumentException("当前 applicationId 不能为空");
        }
        String normalizedRecipeId = explicitRecipeId == null ? null : explicitRecipeId.trim();
        if (normalizedRecipeId != null && !normalizedRecipeId.isEmpty()) {
            ProfileRecipe recipe = requireRecipe(normalizedRecipeId);
            templateRegistry.requireFor(recipe);
            return recipe;
        }
        if (revisionHistory == null) {
            throw new IllegalArgumentException("Recipe 选择历史不能为空");
        }

        List<ProfileRecipeTemplate> comparisonTemplates = comparisonTemplates(
                currentApplicationId, revisionHistory);
        ProfileRecipe selected = null;
        double selectedRank = Double.POSITIVE_INFINITY;
        for (ProfileRecipe candidate : recipeRegistry.getRecipes()) {
            if (candidate.getVerificationStatus() != RecipeVerificationStatus.VERIFIED
                    || candidate.getProtocolVersion() != ProfileSchemaVersions.PROFILE_PROTOCOL) {
                continue;
            }
            ProfileRecipeTemplate template = templateRegistry.requireFor(candidate);
            SimilarityScore score = score(template, comparisonTemplates);
            if (score.javaSimilarity > JAVA_SIMILARITY_LIMIT
                    || score.nativeSimilarity > NATIVE_SIMILARITY_LIMIT) {
                continue;
            }
            double rank = Math.max(score.javaSimilarity / JAVA_SIMILARITY_LIMIT,
                    score.nativeSimilarity / NATIVE_SIMILARITY_LIMIT);
            if (selected == null || rank < selectedRank
                    || (Double.compare(rank, selectedRank) == 0
                    && candidate.getRecipeId().compareTo(selected.getRecipeId()) < 0)) {
                selected = candidate;
                selectedRank = rank;
            }
        }
        if (selected == null) {
            throw new IllegalArgumentException("没有候选 Recipe 满足跨应用相似度门槛：java<="
                    + JAVA_SIMILARITY_LIMIT + "，native<=" + NATIVE_SIMILARITY_LIMIT);
        }
        return selected;
    }

    private ProfileRecipe requireRecipe(String recipeId) {
        return recipeRegistry.requireVerified(recipeId, ProfileSchemaVersions.PROFILE_PROTOCOL);
    }

    private List<ProfileRecipeTemplate> comparisonTemplates(String currentApplicationId,
                                                             List<ProfileRevisionManifest> history) {
        List<ProfileRecipeTemplate> result = new ArrayList<>();
        for (ProfileRevisionManifest revision : history) {
            if (revision == null || revision.getStatus() != RevisionStatus.ACTIVE
                    || currentApplicationId.equals(revision.getApplicationId())) {
                continue;
            }
            result.add(templateRegistry.requireFor(requireRecipe(revision.getRecipeId())));
        }
        return result;
    }

    private static SimilarityScore score(ProfileRecipeTemplate candidate,
                                         List<ProfileRecipeTemplate> comparisons) {
        double maxJava = 0.0d;
        double maxNative = 0.0d;
        for (ProfileRecipeTemplate comparison : comparisons) {
            maxJava = Math.max(maxJava, jaccard(candidate.getJavaSourceUnits(),
                    comparison.getJavaSourceUnits()));
            maxNative = Math.max(maxNative, jaccard(candidate.getNativeSourceUnits(),
                    comparison.getNativeSourceUnits()));
        }
        return new SimilarityScore(maxJava, maxNative);
    }

    private static double jaccard(List<String> left, List<String> right) {
        Set<String> leftSet = new HashSet<>(left);
        Set<String> union = new HashSet<>(leftSet);
        union.addAll(right);
        if (union.isEmpty()) {
            return 1.0d;
        }
        leftSet.retainAll(new HashSet<>(right));
        return (double) leftSet.size() / (double) union.size();
    }

    private static final class SimilarityScore {
        private final double javaSimilarity;
        private final double nativeSimilarity;

        private SimilarityScore(double javaSimilarity, double nativeSimilarity) {
            this.javaSimilarity = javaSimilarity;
            this.nativeSimilarity = nativeSimilarity;
        }
    }
}
