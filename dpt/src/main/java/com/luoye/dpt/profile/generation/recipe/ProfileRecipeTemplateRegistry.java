package com.luoye.dpt.profile.generation.recipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProfileRecipeTemplateRegistry {
    private static final RecipeRuntimeContract RUNTIME_CONTRACT =
            RecipeRuntimeContract.immutableShellV1();
    private static final ProfileRecipeTemplateRegistry BUILT_IN =
            new ProfileRecipeTemplateRegistry(Arrays.asList(
                    new ProfileRecipeTemplate(ProfileRecipeRegistry.RECIPE_A,
                            "java-delegated-startup-v1", "native-linear-bootstrap-v1",
                            Arrays.asList("application-entry", "component-factory-entry",
                                    "jni-bridge", "startup-delegate", "library-delegate",
                                    "component-delegate"),
                            Arrays.asList("jni-entry", "startup-linear", "path-direct",
                                    "registration-table"), RUNTIME_CONTRACT),
                    new ProfileRecipeTemplate(ProfileRecipeRegistry.RECIPE_B,
                            "java-staged-startup-v1", "native-phased-bootstrap-v1",
                            Arrays.asList("application-entry", "component-factory-entry",
                                    "jni-bridge", "startup-pipeline", "library-stage",
                                    "restore-stage", "component-router"),
                            Arrays.asList("jni-entry", "startup-phases", "path-context",
                                    "config-adapter", "startup-config-risk-phase",
                                    "startup-registration-phase", "registration-table"),
                            RUNTIME_CONTRACT)
            ));

    private final Map<String, ProfileRecipeTemplate> templatesByRecipeId;
    private final List<ProfileRecipeTemplate> templates;

    ProfileRecipeTemplateRegistry(List<ProfileRecipeTemplate> templates) {
        if (templates == null || templates.isEmpty()) {
            throw new IllegalArgumentException("Recipe 模板注册表不能为空");
        }
        LinkedHashMap<String, ProfileRecipeTemplate> indexed = new LinkedHashMap<>();
        for (ProfileRecipeTemplate template : templates) {
            if (template == null) {
                throw new IllegalArgumentException("Recipe 模板注册表不能包含空定义");
            }
            if (indexed.put(template.getRecipeId(), template) != null) {
                throw new IllegalArgumentException("Recipe 模板 ID 重复：" + template.getRecipeId());
            }
        }
        this.templatesByRecipeId = Collections.unmodifiableMap(indexed);
        this.templates = Collections.unmodifiableList(new ArrayList<>(indexed.values()));
    }

    public static ProfileRecipeTemplateRegistry builtIn() {
        return BUILT_IN;
    }

    public List<ProfileRecipeTemplate> getTemplates() {
        return templates;
    }

    public ProfileRecipeTemplate requireFor(ProfileRecipe recipe) {
        if (recipe == null) {
            throw new IllegalArgumentException("Recipe 不能为空");
        }
        ProfileRecipeTemplate template = templatesByRecipeId.get(recipe.getRecipeId());
        if (template == null) {
            throw new IllegalArgumentException("Recipe 缺少完整生成模板：" + recipe.getRecipeId());
        }
        if (!recipe.getJavaCompositionId().equals(template.getJavaCompositionId())
                || !recipe.getNativeCompositionId().equals(template.getNativeCompositionId())) {
            throw new IllegalArgumentException("Recipe 模板组成不匹配：" + recipe.getRecipeId());
        }
        return template;
    }
}
