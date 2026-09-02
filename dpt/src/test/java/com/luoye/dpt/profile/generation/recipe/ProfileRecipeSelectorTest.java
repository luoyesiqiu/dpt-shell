package com.luoye.dpt.profile.generation.recipe;

import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.model.RevisionStatus;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class ProfileRecipeSelectorTest {
    private static final String CURRENT_APPLICATION = "com.example.current";

    @Test
    public void explicitSelectionUsesExactVerifiedRecipeWithoutFallback() {
        ProfileRecipeSelector selector = ProfileRecipeSelector.builtIn();

        ProfileRecipe selected = selector.select(ProfileRecipeRegistry.RECIPE_B,
                CURRENT_APPLICATION, Collections.singletonList(activeRevision(
                        "com.example.other", ProfileRecipeRegistry.RECIPE_B)));

        Assert.assertEquals(ProfileRecipeRegistry.RECIPE_B, selected.getRecipeId());
        try {
            selector.select("unknown-recipe", CURRENT_APPLICATION, Collections.emptyList());
            Assert.fail("Expected explicit recipe rejection");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("不存在"));
        }
    }

    @Test
    public void automaticSelectionChoosesLowestCrossApplicationTemplateSimilarity() {
        ProfileRecipeSelector selector = ProfileRecipeSelector.builtIn();

        ProfileRecipe first = selector.select(null, CURRENT_APPLICATION, Collections.emptyList());
        ProfileRecipe second = selector.select(null, CURRENT_APPLICATION,
                Collections.singletonList(activeRevision("com.example.other",
                        ProfileRecipeRegistry.RECIPE_A)));

        Assert.assertEquals(ProfileRecipeRegistry.RECIPE_A, first.getRecipeId());
        Assert.assertEquals(ProfileRecipeRegistry.RECIPE_B, second.getRecipeId());
    }

    @Test
    public void automaticSelectionIgnoresSameApplicationAndNeverActivatedRevisions() {
        ProfileRecipe selected = ProfileRecipeSelector.builtIn().select(null,
                CURRENT_APPLICATION, Arrays.asList(
                        activeRevision(CURRENT_APPLICATION, ProfileRecipeRegistry.RECIPE_A),
                        revision("com.example.other", ProfileRecipeRegistry.RECIPE_A,
                                RevisionStatus.REJECTED)));

        Assert.assertEquals(ProfileRecipeRegistry.RECIPE_A, selected.getRecipeId());
    }

    @Test
    public void automaticSelectionFailsWhenEveryWholeRecipeExceedsThreshold() {
        try {
            ProfileRecipeSelector.builtIn().select(null, CURRENT_APPLICATION, Arrays.asList(
                    activeRevision("com.example.alpha", ProfileRecipeRegistry.RECIPE_A),
                    activeRevision("com.example.beta", ProfileRecipeRegistry.RECIPE_B)));
            Assert.fail("Expected no eligible recipe failure");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("没有候选 Recipe"));
        }
    }

    private static ProfileRevisionManifest activeRevision(String applicationId, String recipeId) {
        return revision(applicationId, recipeId, RevisionStatus.ACTIVE);
    }

    private static ProfileRevisionManifest revision(String applicationId, String recipeId,
                                                     RevisionStatus status) {
        ProfileRevisionManifest revision = new ProfileRevisionManifest();
        revision.setApplicationId(applicationId);
        revision.setRecipeId(recipeId);
        revision.setStatus(status);
        return revision;
    }
}
