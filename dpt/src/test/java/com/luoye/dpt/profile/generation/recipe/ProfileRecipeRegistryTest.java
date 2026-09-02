package com.luoye.dpt.profile.generation.recipe;

import com.luoye.dpt.profile.model.ProfileSchemaVersions;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ProfileRecipeRegistryTest {

    @Test
    public void builtInRegistryExposesTwoWholeVerifiedRecipes() {
        ProfileRecipeRegistry registry = ProfileRecipeRegistry.builtIn();

        List<ProfileRecipe> recipes = registry.getRecipes();

        Assert.assertEquals(2, recipes.size());
        assertVerifiedWholeRecipe(registry.requireVerified(
                ProfileRecipeRegistry.RECIPE_A, ProfileSchemaVersions.PROFILE_PROTOCOL));
        assertVerifiedWholeRecipe(registry.requireVerified(
                ProfileRecipeRegistry.RECIPE_B, ProfileSchemaVersions.PROFILE_PROTOCOL));
        Assert.assertNotEquals(recipes.get(0).getJavaCompositionId(),
                recipes.get(1).getJavaCompositionId());
        Assert.assertNotEquals(recipes.get(0).getNativeCompositionId(),
                recipes.get(1).getNativeCompositionId());
    }

    @Test
    public void rejectsUnknownUnverifiedAndProtocolIncompatibleRecipes() {
        ProfileRecipe pending = new ProfileRecipe("pending-recipe", 1,
                "java-pending", "native-pending", RecipeVerificationStatus.PENDING);
        ProfileRecipe incompatible = new ProfileRecipe("future-recipe", 2,
                "java-future", "native-future", RecipeVerificationStatus.VERIFIED);
        ProfileRecipeRegistry registry = new ProfileRecipeRegistry(
                Arrays.asList(pending, incompatible));

        assertRejected(registry, "missing-recipe", 1, "不存在");
        assertRejected(registry, "pending-recipe", 1, "未通过整体验证");
        assertRejected(registry, "future-recipe", 1, "协议版本不兼容");
    }

    @Test
    public void rejectsDuplicateRecipeIdsInsteadOfCombiningComponents() {
        ProfileRecipe first = new ProfileRecipe("duplicate-recipe", 1,
                "java-a", "native-a", RecipeVerificationStatus.VERIFIED);
        ProfileRecipe second = new ProfileRecipe("duplicate-recipe", 1,
                "java-b", "native-b", RecipeVerificationStatus.VERIFIED);

        try {
            new ProfileRecipeRegistry(Arrays.asList(first, second));
            Assert.fail("Expected duplicate recipe rejection");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("重复"));
        }
    }

    private static void assertVerifiedWholeRecipe(ProfileRecipe recipe) {
        Assert.assertEquals(ProfileSchemaVersions.PROFILE_PROTOCOL,
                recipe.getProtocolVersion());
        Assert.assertEquals(RecipeVerificationStatus.VERIFIED,
                recipe.getVerificationStatus());
        Assert.assertFalse(recipe.getRecipeId().isEmpty());
        Assert.assertFalse(recipe.getJavaCompositionId().isEmpty());
        Assert.assertFalse(recipe.getNativeCompositionId().isEmpty());
    }

    private static void assertRejected(ProfileRecipeRegistry registry, String recipeId,
                                       int protocolVersion, String messagePart) {
        try {
            registry.requireVerified(recipeId, protocolVersion);
            Assert.fail("Expected recipe rejection: " + recipeId);
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(messagePart));
        }
    }
}
