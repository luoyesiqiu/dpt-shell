package com.luoye.dpt.profile.generation.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ProfileRecipeTemplate {
    private final String recipeId;
    private final String javaCompositionId;
    private final String nativeCompositionId;
    private final List<String> javaSourceUnits;
    private final List<String> nativeSourceUnits;
    private final RecipeRuntimeContract runtimeContract;

    ProfileRecipeTemplate(String recipeId, String javaCompositionId, String nativeCompositionId,
                          List<String> javaSourceUnits, List<String> nativeSourceUnits,
                          RecipeRuntimeContract runtimeContract) {
        this.recipeId = requireText("recipeId", recipeId);
        this.javaCompositionId = requireText("javaCompositionId", javaCompositionId);
        this.nativeCompositionId = requireText("nativeCompositionId", nativeCompositionId);
        this.javaSourceUnits = requireUnits("javaSourceUnits", javaSourceUnits);
        this.nativeSourceUnits = requireUnits("nativeSourceUnits", nativeSourceUnits);
        if (runtimeContract == null) {
            throw new IllegalArgumentException("Recipe 运行契约不能为空");
        }
        this.runtimeContract = runtimeContract;
    }

    public String getRecipeId() {
        return recipeId;
    }

    public String getJavaCompositionId() {
        return javaCompositionId;
    }

    public String getNativeCompositionId() {
        return nativeCompositionId;
    }

    public List<String> getJavaSourceUnits() {
        return javaSourceUnits;
    }

    public List<String> getNativeSourceUnits() {
        return nativeSourceUnits;
    }

    public RecipeRuntimeContract getRuntimeContract() {
        return runtimeContract;
    }

    private static List<String> requireUnits(String fieldName, List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Recipe " + fieldName + " 不能为空");
        }
        ArrayList<String> result = new ArrayList<>();
        for (String value : values) {
            result.add(requireText(fieldName, value));
        }
        return Collections.unmodifiableList(result);
    }

    private static String requireText(String fieldName, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Recipe " + fieldName + " 不能为空");
        }
        return value.trim();
    }
}
