package com.luoye.dpt.profile.generation.recipe;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JavaShellStartupContractTest {

    @Test
    public void legacyApplicationAttachKeepsExistingOrderAndReplacementGuard() {
        JavaShellStartupContract contract = JavaShellStartupContract.immutableV1();

        assertSteps(contract, JavaShellStartupContract.EntryPoint.LEGACY_ATTACH_BASE_CONTEXT,
                expected(JavaShellStartupContract.Operation.CALL_FRAMEWORK_SUPER,
                        JavaShellStartupContract.Condition.ALWAYS),
                expected(JavaShellStartupContract.Operation.REQUIRE_APPLICATION_INFO,
                        JavaShellStartupContract.Condition.CLASS_LOADER_NOT_REPLACED),
                expected(JavaShellStartupContract.Operation.EXTRACT_OR_REUSE_SHELL_SO,
                        JavaShellStartupContract.Condition.CLASS_LOADER_NOT_REPLACED),
                expected(JavaShellStartupContract.Operation.LOAD_SHELL_SO,
                        JavaShellStartupContract.Condition.CLASS_LOADER_NOT_REPLACED),
                expected(JavaShellStartupContract.Operation.INITIALIZE_NATIVE_RUNTIME,
                        JavaShellStartupContract.Condition.CLASS_LOADER_NOT_REPLACED),
                expected(JavaShellStartupContract.Operation.RESTORE_DEX_AND_REPLACE_CLASS_LOADER,
                        JavaShellStartupContract.Condition.CLASS_LOADER_NOT_REPLACED),
                expected(JavaShellStartupContract.Operation.MARK_CLASS_LOADER_REPLACED,
                        JavaShellStartupContract.Condition.CLASS_LOADER_NOT_REPLACED),
                expected(JavaShellStartupContract.Operation.READ_ORIGINAL_APPLICATION_NAME,
                        JavaShellStartupContract.Condition.ALWAYS));
    }

    @Test
    public void legacyApplicationRestorationRemainsSingleShotForBothTriggers() {
        JavaShellStartupContract contract = JavaShellStartupContract.immutableV1();

        List<JavaShellStartupContract.StartupStep> restoreOnce = Arrays.asList(
                expected(JavaShellStartupContract.Operation.CREATE_AND_INSTALL_ORIGINAL_APPLICATION,
                        JavaShellStartupContract.Condition.ORIGINAL_APPLICATION_DECLARED_AND_PENDING),
                expected(JavaShellStartupContract.Operation.CALL_ORIGINAL_APPLICATION_ON_CREATE,
                        JavaShellStartupContract.Condition.ORIGINAL_APPLICATION_DECLARED_AND_PENDING),
                expected(JavaShellStartupContract.Operation.MARK_ORIGINAL_APPLICATION_RESTORED,
                        JavaShellStartupContract.Condition.ORIGINAL_APPLICATION_DECLARED_AND_PENDING));
        assertSteps(contract, JavaShellStartupContract.EntryPoint.LEGACY_ON_CREATE,
                expected(JavaShellStartupContract.Operation.CALL_FRAMEWORK_SUPER,
                        JavaShellStartupContract.Condition.ALWAYS),
                restoreOnce.get(0), restoreOnce.get(1), restoreOnce.get(2));
        assertSteps(contract, JavaShellStartupContract.EntryPoint.LEGACY_CREATE_PACKAGE_CONTEXT,
                restoreOnce.get(0), restoreOnce.get(1), restoreOnce.get(2),
                expected(JavaShellStartupContract.Operation.RETURN_ORIGINAL_APPLICATION_CONTEXT,
                        JavaShellStartupContract.Condition.ORIGINAL_APPLICATION_DECLARED),
                expected(JavaShellStartupContract.Operation.FALL_BACK_TO_FRAMEWORK,
                        JavaShellStartupContract.Condition.ORIGINAL_APPLICATION_NOT_DECLARED));
    }

    @Test
    public void componentFactoryClassLoaderAndApplicationKeepExistingInitializationOrder() {
        JavaShellStartupContract contract = JavaShellStartupContract.immutableV1();

        assertSteps(contract,
                JavaShellStartupContract.EntryPoint.FACTORY_INSTANTIATE_CLASS_LOADER,
                expected(JavaShellStartupContract.Operation.EXTRACT_OR_REUSE_SHELL_SO,
                        JavaShellStartupContract.Condition.ALWAYS),
                expected(JavaShellStartupContract.Operation.LOAD_SHELL_SO,
                        JavaShellStartupContract.Condition.ALWAYS),
                expected(JavaShellStartupContract.Operation.INITIALIZE_NATIVE_RUNTIME,
                        JavaShellStartupContract.Condition.ALWAYS),
                expected(JavaShellStartupContract.Operation.RESOLVE_ORIGINAL_COMPONENT_FACTORY,
                        JavaShellStartupContract.Condition.ALWAYS),
                expected(JavaShellStartupContract.Operation.RESTORE_DEX_AND_REPLACE_CLASS_LOADER,
                        JavaShellStartupContract.Condition.ALWAYS),
                expected(JavaShellStartupContract.Operation.MARK_CLASS_LOADER_REPLACED,
                        JavaShellStartupContract.Condition.ALWAYS),
                expected(JavaShellStartupContract.Operation.DELEGATE_TO_ORIGINAL_COMPONENT_FACTORY,
                        JavaShellStartupContract.Condition.ORIGINAL_COMPONENT_FACTORY_AVAILABLE),
                expected(JavaShellStartupContract.Operation.FALL_BACK_TO_FRAMEWORK,
                        JavaShellStartupContract.Condition.ORIGINAL_COMPONENT_FACTORY_UNAVAILABLE_OR_FAILED));

        assertSteps(contract,
                JavaShellStartupContract.EntryPoint.FACTORY_INSTANTIATE_APPLICATION,
                expected(JavaShellStartupContract.Operation.REQUIRE_APPLICATION_INFO,
                        JavaShellStartupContract.Condition.CLASS_LOADER_NOT_REPLACED),
                expected(JavaShellStartupContract.Operation.EXTRACT_OR_REUSE_SHELL_SO,
                        JavaShellStartupContract.Condition.CLASS_LOADER_NOT_REPLACED),
                expected(JavaShellStartupContract.Operation.LOAD_SHELL_SO,
                        JavaShellStartupContract.Condition.CLASS_LOADER_NOT_REPLACED),
                expected(JavaShellStartupContract.Operation.INITIALIZE_NATIVE_RUNTIME,
                        JavaShellStartupContract.Condition.ALWAYS),
                expected(JavaShellStartupContract.Operation.READ_ORIGINAL_APPLICATION_NAME,
                        JavaShellStartupContract.Condition.ALWAYS),
                expected(JavaShellStartupContract.Operation.RESTORE_DEX_AND_REPLACE_CLASS_LOADER,
                        JavaShellStartupContract.Condition.CLASS_LOADER_NOT_REPLACED),
                expected(JavaShellStartupContract.Operation.MARK_CLASS_LOADER_REPLACED,
                        JavaShellStartupContract.Condition.CLASS_LOADER_NOT_REPLACED),
                expected(JavaShellStartupContract.Operation.RESOLVE_ORIGINAL_COMPONENT_FACTORY,
                        JavaShellStartupContract.Condition.ALWAYS),
                expected(JavaShellStartupContract.Operation.DISABLE_LEGACY_APPLICATION_RESTORE,
                        JavaShellStartupContract.Condition.ALWAYS),
                expected(JavaShellStartupContract.Operation.SELECT_ORIGINAL_APPLICATION_CLASS,
                        JavaShellStartupContract.Condition.ALWAYS),
                expected(JavaShellStartupContract.Operation.DELEGATE_TO_ORIGINAL_COMPONENT_FACTORY,
                        JavaShellStartupContract.Condition.ORIGINAL_COMPONENT_FACTORY_AVAILABLE),
                expected(JavaShellStartupContract.Operation.FALL_BACK_TO_FRAMEWORK,
                        JavaShellStartupContract.Condition.ORIGINAL_COMPONENT_FACTORY_UNAVAILABLE_OR_FAILED));
    }

    @Test
    public void allApplicationComponentsKeepOriginalFactoryDelegationAndFrameworkFallback() {
        JavaShellStartupContract contract = JavaShellStartupContract.immutableV1();
        JavaShellStartupContract.StartupStep[] expected = {
                expected(JavaShellStartupContract.Operation.RESOLVE_ORIGINAL_COMPONENT_FACTORY,
                        JavaShellStartupContract.Condition.ALWAYS),
                expected(JavaShellStartupContract.Operation.DELEGATE_TO_ORIGINAL_COMPONENT_FACTORY,
                        JavaShellStartupContract.Condition.ORIGINAL_COMPONENT_FACTORY_AVAILABLE),
                expected(JavaShellStartupContract.Operation.FALL_BACK_TO_FRAMEWORK,
                        JavaShellStartupContract.Condition.ORIGINAL_COMPONENT_FACTORY_UNAVAILABLE_OR_FAILED)
        };

        assertSteps(contract, JavaShellStartupContract.EntryPoint.FACTORY_INSTANTIATE_ACTIVITY,
                expected);
        assertSteps(contract, JavaShellStartupContract.EntryPoint.FACTORY_INSTANTIATE_SERVICE,
                expected);
        assertSteps(contract, JavaShellStartupContract.EntryPoint.FACTORY_INSTANTIATE_RECEIVER,
                expected);
        assertSteps(contract, JavaShellStartupContract.EntryPoint.FACTORY_INSTANTIATE_PROVIDER,
                expected);
    }

    @Test
    public void startupContractIsVersionedImmutableAndSharedByCompleteRecipes() {
        ProfileRecipeTemplateRegistry templates = ProfileRecipeTemplateRegistry.builtIn();
        JavaShellStartupContract first = templates.getTemplates().get(0).getRuntimeContract()
                .getJavaStartupContract();
        JavaShellStartupContract second = templates.getTemplates().get(1).getRuntimeContract()
                .getJavaStartupContract();

        Assert.assertEquals("java-shell-startup-v1", first.getVersion());
        Assert.assertSame(first, second);
        try {
            first.getSteps(JavaShellStartupContract.EntryPoint.LEGACY_ATTACH_BASE_CONTEXT)
                    .clear();
            Assert.fail("启动步骤必须不可修改");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    @Test
    public void completeRecipesKeepFirstAndRepeatedInitializationSingleShot() {
        for (ProfileRecipeTemplate template
                : ProfileRecipeTemplateRegistry.builtIn().getTemplates()) {
            JavaShellStartupContract contract = template.getRuntimeContract()
                    .getJavaStartupContract();
            Assert.assertEquals(template.getRecipeId(), Arrays.asList(
                            JavaShellStartupContract.Operation.CALL_FRAMEWORK_SUPER,
                            JavaShellStartupContract.Operation.REQUIRE_APPLICATION_INFO,
                            JavaShellStartupContract.Operation.EXTRACT_OR_REUSE_SHELL_SO,
                            JavaShellStartupContract.Operation.LOAD_SHELL_SO,
                            JavaShellStartupContract.Operation.INITIALIZE_NATIVE_RUNTIME,
                            JavaShellStartupContract.Operation
                                    .RESTORE_DEX_AND_REPLACE_CLASS_LOADER,
                            JavaShellStartupContract.Operation.MARK_CLASS_LOADER_REPLACED,
                            JavaShellStartupContract.Operation.READ_ORIGINAL_APPLICATION_NAME),
                    enabledOperations(contract,
                            JavaShellStartupContract.EntryPoint.LEGACY_ATTACH_BASE_CONTEXT,
                            false, false, false, false));
            Assert.assertEquals(template.getRecipeId(), Arrays.asList(
                            JavaShellStartupContract.Operation.CALL_FRAMEWORK_SUPER,
                            JavaShellStartupContract.Operation.READ_ORIGINAL_APPLICATION_NAME),
                    enabledOperations(contract,
                            JavaShellStartupContract.EntryPoint.LEGACY_ATTACH_BASE_CONTEXT,
                            true, false, false, false));

            Assert.assertEquals(template.getRecipeId(), Arrays.asList(
                            JavaShellStartupContract.Operation.CALL_FRAMEWORK_SUPER,
                            JavaShellStartupContract.Operation
                                    .CREATE_AND_INSTALL_ORIGINAL_APPLICATION,
                            JavaShellStartupContract.Operation
                                    .CALL_ORIGINAL_APPLICATION_ON_CREATE,
                            JavaShellStartupContract.Operation
                                    .MARK_ORIGINAL_APPLICATION_RESTORED),
                    enabledOperations(contract,
                            JavaShellStartupContract.EntryPoint.LEGACY_ON_CREATE,
                            true, true, true, false));
            Assert.assertEquals(template.getRecipeId(), Arrays.asList(
                            JavaShellStartupContract.Operation.CALL_FRAMEWORK_SUPER),
                    enabledOperations(contract,
                            JavaShellStartupContract.EntryPoint.LEGACY_ON_CREATE,
                            true, true, false, false));
        }
    }

    @Test
    public void completeRecipesKeepOriginalFactoryDelegationAndFrameworkFallbackPaths() {
        List<JavaShellStartupContract.EntryPoint> componentEntries = Arrays.asList(
                JavaShellStartupContract.EntryPoint.FACTORY_INSTANTIATE_ACTIVITY,
                JavaShellStartupContract.EntryPoint.FACTORY_INSTANTIATE_SERVICE,
                JavaShellStartupContract.EntryPoint.FACTORY_INSTANTIATE_RECEIVER,
                JavaShellStartupContract.EntryPoint.FACTORY_INSTANTIATE_PROVIDER);
        for (ProfileRecipeTemplate template
                : ProfileRecipeTemplateRegistry.builtIn().getTemplates()) {
            JavaShellStartupContract contract = template.getRuntimeContract()
                    .getJavaStartupContract();
            for (JavaShellStartupContract.EntryPoint entryPoint : componentEntries) {
                Assert.assertEquals(template.getRecipeId() + ":" + entryPoint,
                        Arrays.asList(
                                JavaShellStartupContract.Operation
                                        .RESOLVE_ORIGINAL_COMPONENT_FACTORY,
                                JavaShellStartupContract.Operation
                                        .DELEGATE_TO_ORIGINAL_COMPONENT_FACTORY),
                        enabledOperations(contract, entryPoint,
                                true, false, false, true));
                Assert.assertEquals(template.getRecipeId() + ":" + entryPoint,
                        Arrays.asList(
                                JavaShellStartupContract.Operation
                                        .RESOLVE_ORIGINAL_COMPONENT_FACTORY,
                                JavaShellStartupContract.Operation.FALL_BACK_TO_FRAMEWORK),
                        enabledOperations(contract, entryPoint,
                                true, false, false, false));
            }
        }
    }

    private static void assertSteps(JavaShellStartupContract contract,
                                    JavaShellStartupContract.EntryPoint entryPoint,
                                    JavaShellStartupContract.StartupStep... expected) {
        Assert.assertEquals(entryPoint.name(), Arrays.asList(expected),
                contract.getSteps(entryPoint));
    }

    private static JavaShellStartupContract.StartupStep expected(
            JavaShellStartupContract.Operation operation,
            JavaShellStartupContract.Condition condition) {
        return JavaShellStartupContract.StartupStep.of(operation, condition);
    }

    private static List<JavaShellStartupContract.Operation> enabledOperations(
            JavaShellStartupContract contract,
            JavaShellStartupContract.EntryPoint entryPoint,
            boolean classLoaderReplaced,
            boolean originalApplicationDeclared,
            boolean originalApplicationPending,
            boolean originalFactoryAvailable) {
        List<JavaShellStartupContract.Operation> result = new ArrayList<>();
        for (JavaShellStartupContract.StartupStep step : contract.getSteps(entryPoint)) {
            if (conditionMatches(step.getCondition(), classLoaderReplaced,
                    originalApplicationDeclared, originalApplicationPending,
                    originalFactoryAvailable)) {
                result.add(step.getOperation());
            }
        }
        return result;
    }

    private static boolean conditionMatches(JavaShellStartupContract.Condition condition,
                                            boolean classLoaderReplaced,
                                            boolean originalApplicationDeclared,
                                            boolean originalApplicationPending,
                                            boolean originalFactoryAvailable) {
        switch (condition) {
            case ALWAYS:
                return true;
            case CLASS_LOADER_NOT_REPLACED:
                return !classLoaderReplaced;
            case ORIGINAL_APPLICATION_DECLARED:
                return originalApplicationDeclared;
            case ORIGINAL_APPLICATION_NOT_DECLARED:
                return !originalApplicationDeclared;
            case ORIGINAL_APPLICATION_DECLARED_AND_PENDING:
                return originalApplicationDeclared && originalApplicationPending;
            case ORIGINAL_COMPONENT_FACTORY_AVAILABLE:
                return originalFactoryAvailable;
            case ORIGINAL_COMPONENT_FACTORY_UNAVAILABLE_OR_FAILED:
                return !originalFactoryAvailable;
            default:
                throw new AssertionError("未覆盖条件：" + condition);
        }
    }
}
