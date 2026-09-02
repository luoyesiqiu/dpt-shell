package com.luoye.dpt.profile.generation;

import com.luoye.dpt.profile.generation.recipe.ProfileRecipe;
import com.luoye.dpt.profile.generation.recipe.ProfileRecipeRegistry;
import com.luoye.dpt.profile.generation.recipe.ProfileRecipeTemplate;
import com.luoye.dpt.profile.generation.recipe.ProfileRecipeTemplateRegistry;
import com.luoye.dpt.profile.generation.recipe.RecipeRuntimeContract;
import com.luoye.dpt.profile.model.ProfileIdentityLock;
import com.luoye.dpt.profile.model.ProfileSchemaVersions;
import com.luoye.dpt.profile.storage.ProfileCreationResult;
import com.luoye.dpt.profile.storage.ProfileCreationService;
import com.luoye.dpt.profile.storage.ProfileIdentityStore;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ProfileJavaStartupGraphGenerationTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void delegatedRecipeUsesFanOutGraphWithCanonicalStartupOrder() throws Exception {
        ProfileIdentityLock identity = createIdentity();
        ProfileRecipeTemplate template = template(ProfileRecipeRegistry.RECIPE_A);
        ProfileCodeGenerator.GenerationLayout layout = generate(identity, template, "delegated");
        GraphNames names = new GraphNames(identity, template);

        String actions = read(layout, names.actionsClass);
        assertActionInterface(actions, names.actionMethods);

        String root = read(layout, names.className("startup-delegate"));
        String library = read(layout, names.className("library-delegate"));
        String components = read(layout, names.className("component-delegate"));
        assertInOrder(root,
                call(names.className("library-delegate"),
                        names.methodName("library-delegate")),
                call(names.className("component-delegate"),
                        names.methodName("component-delegate")));
        assertInOrder(library, actionCalls(names.actionMethods, 0, 2));
        assertInOrder(components, actionCalls(names.actionMethods, 2,
                names.actionMethods.size()));
        assertEntryPointsReferenceRoot(layout, identity, names,
                names.className("startup-delegate"));
    }

    @Test
    public void stagedRecipeUsesLinearPipelineWithSameCanonicalStartupOrder() throws Exception {
        ProfileIdentityLock identity = createIdentity();
        ProfileRecipeTemplate template = template(ProfileRecipeRegistry.RECIPE_B);
        ProfileCodeGenerator.GenerationLayout layout = generate(identity, template, "staged");
        GraphNames names = new GraphNames(identity, template);

        String actions = read(layout, names.actionsClass);
        assertActionInterface(actions, names.actionMethods);

        String root = read(layout, names.className("startup-pipeline"));
        String library = read(layout, names.className("library-stage"));
        String restore = read(layout, names.className("restore-stage"));
        String components = read(layout, names.className("component-router"));
        Assert.assertTrue(root.contains(call(names.className("library-stage"),
                names.methodName("library-stage"))));
        assertInOrder(library,
                actionCalls(names.actionMethods, 0, 2),
                call(names.className("restore-stage"), names.methodName("restore-stage")));
        assertInOrder(restore,
                actionCalls(names.actionMethods, 2, 4),
                call(names.className("component-router"),
                        names.methodName("component-router")));
        assertInOrder(components, actionCalls(names.actionMethods, 4,
                names.actionMethods.size()));
        assertEntryPointsReferenceRoot(layout, identity, names,
                names.className("startup-pipeline"));
    }

    @Test
    public void twoRecipesGenerateDifferentEdgesButShareTheImmutableActionContract()
            throws Exception {
        ProfileIdentityLock identity = createIdentity();
        ProfileRecipeTemplate delegated = template(ProfileRecipeRegistry.RECIPE_A);
        ProfileRecipeTemplate staged = template(ProfileRecipeRegistry.RECIPE_B);
        ProfileCodeGenerator.GenerationLayout delegatedLayout = generate(identity, delegated,
                "compare-delegated");
        ProfileCodeGenerator.GenerationLayout stagedLayout = generate(identity, staged,
                "compare-staged");
        GraphNames delegatedNames = new GraphNames(identity, delegated);
        GraphNames stagedNames = new GraphNames(identity, staged);

        Assert.assertEquals(delegatedNames.actionsClass, stagedNames.actionsClass);
        Assert.assertEquals(delegatedNames.actionMethods, stagedNames.actionMethods);
        Assert.assertEquals(read(delegatedLayout, delegatedNames.actionsClass),
                read(stagedLayout, stagedNames.actionsClass));
        Assert.assertNotEquals(read(delegatedLayout,
                        delegatedNames.className("startup-delegate")),
                read(stagedLayout, stagedNames.className("startup-pipeline")));
    }

    @Test
    public void generatedEntriesImplementRuntimeLifecycleDispatch() throws Exception {
        for (String recipeId : new String[]{ProfileRecipeRegistry.RECIPE_A,
                ProfileRecipeRegistry.RECIPE_B}) {
            ProfileIdentityLock identity = createIdentity();
            ProfileCodeGenerator.GenerationLayout layout = generate(identity,
                    template(recipeId), "runtime-" + recipeId);

            String application = read(layout, simpleName(identity.getProxyApplicationClassName()));
            Assert.assertTrue(application.contains("protected void attachBaseContext(Context base)"));
            Assert.assertTrue(application.contains("public void onCreate()"));
            Assert.assertTrue(application.contains("createPackageContext"));
            Assert.assertTrue(application.contains("getPackageName()"));

            String componentFactory = read(layout,
                    simpleName(identity.getProxyComponentFactoryClassName()));
            for (String method : new String[]{"instantiateClassLoader",
                    "instantiateApplication", "instantiateActivity", "instantiateService",
                    "instantiateReceiver", "instantiateProvider"}) {
                Assert.assertTrue("缺少 AppComponentFactory 入口 " + method,
                        componentFactory.contains(method + "("));
            }
        }
    }

    private ProfileIdentityLock createIdentity() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder().toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot)
                .create("com.example.startup");
        return new ProfileIdentityStore(profilesRoot).loadExisting(
                "com.example.startup", created.getProfileId());
    }

    private ProfileCodeGenerator.GenerationLayout generate(ProfileIdentityLock identity,
                                                            ProfileRecipeTemplate template,
                                                            String directoryName)
            throws Exception {
        return new ProfileCodeGenerator(temporaryFolder.newFolder(directoryName).toPath())
                .generateJava(identity, "r000001", template);
    }

    private static ProfileRecipeTemplate template(String recipeId) {
        ProfileRecipe recipe = ProfileRecipeRegistry.builtIn().requireVerified(recipeId,
                ProfileSchemaVersions.PROFILE_PROTOCOL);
        return ProfileRecipeTemplateRegistry.builtIn().requireFor(recipe);
    }

    private static void assertActionInterface(String source, List<String> actionMethods) {
        List<String> declarations = new ArrayList<>();
        for (String actionMethod : actionMethods) {
            declarations.add("void " + actionMethod + "();");
        }
        assertInOrder(source, declarations.toArray(new String[0]));
    }

    private static void assertEntryPointsReferenceRoot(
            ProfileCodeGenerator.GenerationLayout layout, ProfileIdentityLock identity,
            GraphNames names, String rootClass) throws Exception {
        String application = read(layout, simpleName(identity.getProxyApplicationClassName()));
        String componentFactory = read(layout,
                simpleName(identity.getProxyComponentFactoryClassName()));
        Assert.assertTrue(application.contains(names.actionsClass));
        Assert.assertTrue(application.contains(rootClass));
        Assert.assertTrue(componentFactory.contains(names.actionsClass));
        Assert.assertTrue(componentFactory.contains(rootClass));
    }

    private static String[] actionCalls(List<String> actionMethods, int from, int to) {
        String[] calls = new String[to - from];
        for (int index = from; index < to; index++) {
            calls[index - from] = "actions." + actionMethods.get(index) + "();";
        }
        return calls;
    }

    private static String call(String className, String methodName) {
        return className + "." + methodName + "(actions);";
    }

    private static void assertInOrder(String source, String[] first, String... remaining) {
        String[] expected = new String[first.length + remaining.length];
        System.arraycopy(first, 0, expected, 0, first.length);
        System.arraycopy(remaining, 0, expected, first.length, remaining.length);
        assertInOrder(source, expected);
    }

    private static void assertInOrder(String source, String... expected) {
        int previous = -1;
        for (String value : expected) {
            int current = source.indexOf(value, previous + 1);
            Assert.assertTrue("缺少或顺序错误：" + value + "\n" + source,
                    current > previous);
            previous = current;
        }
    }

    private static String read(ProfileCodeGenerator.GenerationLayout layout,
                               String simpleClassName) throws Exception {
        Path path = layout.getJavaDirectory().resolve(simpleClassName + ".java");
        if (!Files.isRegularFile(path)) {
            try (java.util.stream.Stream<Path> paths = Files.walk(layout.getJavaDirectory())) {
                path = paths.filter(candidate -> candidate.getFileName().toString()
                                .equals(simpleClassName + ".java"))
                        .findFirst().orElse(path);
            }
        }
        return Files.readString(path);
    }

    private static String simpleName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    private static final class GraphNames {
        private final ProfileIdentityLock identity;
        private final ProfileRecipeTemplate template;
        private final String actionsClass;
        private final List<String> actionMethods;

        private GraphNames(ProfileIdentityLock identity, ProfileRecipeTemplate template) {
            this.identity = identity;
            this.template = template;
            this.actionsClass = derive("java-startup-actions", "A");
            this.actionMethods = new ArrayList<>();
            for (RecipeRuntimeContract.JavaStartupDependency dependency
                    : template.getRuntimeContract().getJavaStartupDependencies()) {
                actionMethods.add(derive("java-startup-action-" + dependency.name(), "a"));
            }
        }

        private String className(String unit) {
            requireUnit(unit);
            return derive("java-helper-class-" + template.getRecipeId() + "-" + unit, "H");
        }

        private String methodName(String unit) {
            requireUnit(unit);
            return derive("java-helper-method-" + template.getRecipeId() + "-" + unit, "m");
        }

        private String derive(String label, String prefix) {
            return ProfileStableNameDeriver.identifier(identity.getIdentitySeed(), label,
                    prefix, 12);
        }

        private void requireUnit(String unit) {
            Assert.assertTrue("Recipe 缺少 Java 单元：" + unit,
                    template.getJavaSourceUnits().contains(unit));
        }
    }
}
