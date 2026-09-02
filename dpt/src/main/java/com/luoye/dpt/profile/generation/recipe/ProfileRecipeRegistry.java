package com.luoye.dpt.profile.generation.recipe;

import com.luoye.dpt.profile.model.ProfileSchemaVersions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProfileRecipeRegistry {
    public static final String RECIPE_A = "recipe-a";
    public static final String RECIPE_B = "recipe-b";

    private static final ProfileRecipeRegistry BUILT_IN = new ProfileRecipeRegistry(Arrays.asList(
            new ProfileRecipe(RECIPE_A, ProfileSchemaVersions.PROFILE_PROTOCOL,
                    "java-delegated-startup-v1", "native-linear-bootstrap-v1",
                    RecipeVerificationStatus.VERIFIED),
            new ProfileRecipe(RECIPE_B, ProfileSchemaVersions.PROFILE_PROTOCOL,
                    "java-staged-startup-v1", "native-phased-bootstrap-v1",
                    RecipeVerificationStatus.VERIFIED)
    ));

    private final Map<String, ProfileRecipe> recipesById;
    private final List<ProfileRecipe> recipes;

    ProfileRecipeRegistry(List<ProfileRecipe> recipes) {
        if (recipes == null || recipes.isEmpty()) {
            throw new IllegalArgumentException("Recipe 注册表不能为空");
        }
        LinkedHashMap<String, ProfileRecipe> indexed = new LinkedHashMap<>();
        for (ProfileRecipe recipe : recipes) {
            if (recipe == null) {
                throw new IllegalArgumentException("Recipe 注册表不能包含空定义");
            }
            if (indexed.put(recipe.getRecipeId(), recipe) != null) {
                throw new IllegalArgumentException("Recipe ID 重复：" + recipe.getRecipeId());
            }
        }
        this.recipesById = Collections.unmodifiableMap(indexed);
        this.recipes = Collections.unmodifiableList(new ArrayList<>(indexed.values()));
    }

    public static ProfileRecipeRegistry builtIn() {
        return BUILT_IN;
    }

    public List<ProfileRecipe> getRecipes() {
        return recipes;
    }

    public ProfileRecipe requireVerified(String recipeId, int protocolVersion) {
        String normalizedRecipeId = recipeId == null ? null : recipeId.trim();
        ProfileRecipe recipe = recipesById.get(normalizedRecipeId);
        if (recipe == null) {
            throw new IllegalArgumentException("Recipe 不存在：" + recipeId);
        }
        if (recipe.getProtocolVersion() != protocolVersion) {
            throw new IllegalArgumentException("Recipe 协议版本不兼容：recipeId=" + recipeId
                    + ", expected=" + protocolVersion
                    + ", actual=" + recipe.getProtocolVersion());
        }
        if (recipe.getVerificationStatus() != RecipeVerificationStatus.VERIFIED) {
            throw new IllegalArgumentException("Recipe 未通过整体验证：recipeId=" + recipeId
                    + ", status=" + recipe.getVerificationStatus());
        }
        return recipe;
    }
}
