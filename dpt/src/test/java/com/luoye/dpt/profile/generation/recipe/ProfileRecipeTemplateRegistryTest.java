package com.luoye.dpt.profile.generation.recipe;

import com.luoye.dpt.profile.model.ProfileSchemaVersions;

import org.junit.Assert;
import org.junit.Test;

public class ProfileRecipeTemplateRegistryTest {

    @Test
    public void providesTwoStructurallyDifferentCompleteTemplatesWithSameRuntimeContract() {
        ProfileRecipeRegistry recipes = ProfileRecipeRegistry.builtIn();
        ProfileRecipeTemplateRegistry templates = ProfileRecipeTemplateRegistry.builtIn();
        ProfileRecipe recipeA = recipes.requireVerified(ProfileRecipeRegistry.RECIPE_A,
                ProfileSchemaVersions.PROFILE_PROTOCOL);
        ProfileRecipe recipeB = recipes.requireVerified(ProfileRecipeRegistry.RECIPE_B,
                ProfileSchemaVersions.PROFILE_PROTOCOL);

        ProfileRecipeTemplate templateA = templates.requireFor(recipeA);
        ProfileRecipeTemplate templateB = templates.requireFor(recipeB);

        Assert.assertEquals(2, templates.getTemplates().size());
        Assert.assertEquals(recipeA.getJavaCompositionId(), templateA.getJavaCompositionId());
        Assert.assertEquals(recipeA.getNativeCompositionId(), templateA.getNativeCompositionId());
        Assert.assertEquals(recipeB.getJavaCompositionId(), templateB.getJavaCompositionId());
        Assert.assertEquals(recipeB.getNativeCompositionId(), templateB.getNativeCompositionId());
        Assert.assertNotEquals(templateA.getJavaSourceUnits(), templateB.getJavaSourceUnits());
        Assert.assertNotEquals(templateA.getNativeSourceUnits(), templateB.getNativeSourceUnits());
        Assert.assertEquals(templateA.getRuntimeContract(), templateB.getRuntimeContract());
        Assert.assertFalse(templateA.getJavaSourceUnits().isEmpty());
        Assert.assertFalse(templateA.getNativeSourceUnits().isEmpty());
    }

    @Test
    public void immutableRuntimeContractKeepsStartupConfigurationAndFailureSemantics() {
        RecipeRuntimeContract contract = ProfileRecipeTemplateRegistry.builtIn().getTemplates()
                .get(0).getRuntimeContract();

        Assert.assertEquals(6, contract.getJavaStartupDependencies().size());
        Assert.assertEquals(3, contract.getNativeOnLoadDependencies().size());
        Assert.assertTrue(contract.getConfigurationOutcomes().contains(
                RecipeRuntimeContract.ConfigurationOutcome.JNI_MAPPING_FROM_PROFILE));
        Assert.assertTrue(contract.getConfigurationOutcomes().contains(
                RecipeRuntimeContract.ConfigurationOutcome.ORIGINAL_COMPONENTS_FROM_CONFIG));
        Assert.assertTrue(contract.getFailureSemantics().contains(
                RecipeRuntimeContract.FailureSemantic.JNI_ONLOAD_FAILURE_RETURNS_ERROR));
        Assert.assertTrue(contract.getFailureSemantics().contains(
                RecipeRuntimeContract.FailureSemantic.OPTIONAL_COMPONENT_DELEGATION_FALLS_BACK));
    }
}
