package com.luoye.dpt.profile.generation.recipe;

public final class ProfileRecipe {
    private final String recipeId;
    private final int protocolVersion;
    private final String javaCompositionId;
    private final String nativeCompositionId;
    private final RecipeVerificationStatus verificationStatus;

    ProfileRecipe(String recipeId, int protocolVersion, String javaCompositionId,
                  String nativeCompositionId, RecipeVerificationStatus verificationStatus) {
        this.recipeId = requireText("recipeId", recipeId);
        if (protocolVersion <= 0) {
            throw new IllegalArgumentException("Recipe 协议版本必须为正数");
        }
        this.protocolVersion = protocolVersion;
        this.javaCompositionId = requireText("javaCompositionId", javaCompositionId);
        this.nativeCompositionId = requireText("nativeCompositionId", nativeCompositionId);
        if (verificationStatus == null) {
            throw new IllegalArgumentException("Recipe 整体验证状态不能为空");
        }
        this.verificationStatus = verificationStatus;
    }

    public String getRecipeId() {
        return recipeId;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public String getJavaCompositionId() {
        return javaCompositionId;
    }

    public String getNativeCompositionId() {
        return nativeCompositionId;
    }

    public RecipeVerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    private static String requireText(String fieldName, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Recipe " + fieldName + " 不能为空");
        }
        return value.trim();
    }
}
